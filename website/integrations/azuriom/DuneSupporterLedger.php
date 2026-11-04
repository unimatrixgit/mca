<?php
namespace Azuriom\Support;

use PDO;
use RuntimeException;

/** Candidate internal service only. No HTTP endpoint or checkout hook is installed.
 * Caller must verify native payment ownership/status before invoking this service.
 * This ledger grants decoration identifiers only, never access ranks or game power.
 */
final class DuneSupporterLedger
{
    private const PACKAGES = [
        'caravan-supporter' => ['bronze-seal', 'profile-theme'],
        'guild-patron' => ['silver-seal', 'profile-theme', 'profile-frame'],
        'imperial-patron' => ['imperial-seal', 'profile-theme', 'profile-frame', 'profile-banner'],
        'grand-benefactor' => ['sapphire-seal', 'profile-theme', 'profile-frame', 'profile-banner', 'supporter-acknowledgement'],
    ];
    public function __construct(private PDO $db) {}
    private function query(string $sql, array $values = [])
    {
        $q = $this->db->prepare($sql); $q->execute($values); return $q;
    }
    private function key(string $receipt): void
    {
        if (!preg_match('/^azuriom:item:[1-9][0-9]{0,18}$/D', $receipt))
            throw new RuntimeException('Invalid native receipt key');
    }
    private function transaction(callable $work): mixed
    {
        if ($this->db->inTransaction()) throw new RuntimeException('Independent transaction required');
        $this->db->beginTransaction();
        try { $result = $work(); $this->db->commit(); return $result; }
        catch (\Throwable $e) { if ($this->db->inTransaction()) $this->db->rollBack(); throw $e; }
    }
    public function grant(string $receipt, string $account, string $package, string $status): string
    {
        $this->key($receipt);
        if ($status !== 'completed' || !isset(self::PACKAGES[$package])
            || !preg_match('/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/D', $account))
            throw new RuntimeException('Invalid paid cosmetic entitlement');
        return $this->transaction(function () use ($receipt, $account, $package) {
            $lock = $this->db->getAttribute(PDO::ATTR_DRIVER_NAME) === 'mysql' ? ' FOR UPDATE' : '';
            if (!$this->query('SELECT id FROM site_accounts WHERE id=?'.$lock, [$account])->fetchColumn())
                throw new RuntimeException('Verified website account missing');
            $old = $this->query('SELECT account_id,package_key,state FROM supporter_receipts WHERE receipt_key=?', [$receipt])->fetch(PDO::FETCH_ASSOC);
            if ($old) {
                if ($old['state'] === 'revoked' && $old['account_id'] === null) return 'already-revoked';
                if ($old['account_id'] !== $account || $old['package_key'] !== $package)
                    throw new RuntimeException('Receipt identity mismatch');
                return $old['state'] === 'revoked' ? 'already-revoked' : 'already-recorded';
            }
            $rewards = self::PACKAGES[$package];
            $this->query('INSERT INTO supporter_receipts(receipt_key,account_id,package_key,rewards_json,state) VALUES(?,?,?,?,?)',
                [$receipt, $account, $package, json_encode($rewards, JSON_THROW_ON_ERROR), 'active']);
            foreach ($rewards as $reward)
                $this->query('INSERT INTO supporter_cosmetics(receipt_key,reward_key) VALUES(?,?)', [$receipt, $reward]);
            return 'recorded';
        });
    }
    /** Terminal: a replayed old completion can never reactivate a refunded receipt. */
    public function revoke(string $receipt): string
    {
        $this->key($receipt);
        return $this->transaction(function () use ($receipt) {
            $lock = $this->db->getAttribute(PDO::ATTR_DRIVER_NAME) === 'mysql' ? ' FOR UPDATE' : '';
            $row = $this->query('SELECT state FROM supporter_receipts WHERE receipt_key=?'.$lock, [$receipt])->fetch(PDO::FETCH_ASSOC);
            if (!$row) {
                $this->query('INSERT INTO supporter_receipts(receipt_key,account_id,package_key,rewards_json,state) VALUES(?,NULL,NULL,?,?)', [$receipt, '[]', 'revoked']);
                return 'revoked';
            }
            if ($row['state'] === 'revoked') return 'already-revoked';
            $this->query("UPDATE supporter_receipts SET state='revoked' WHERE receipt_key=?", [$receipt]);
            return 'revoked';
        });
    }
    public function rewards(string $account): array
    {
        return $this->query("SELECT DISTINCT c.reward_key FROM supporter_cosmetics c JOIN supporter_receipts r ON r.receipt_key=c.receipt_key WHERE r.account_id=? AND r.state='active' ORDER BY c.reward_key", [$account])->fetchAll(PDO::FETCH_COLUMN);
    }
}
