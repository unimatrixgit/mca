<?php
namespace Azuriom\Support;

use PDO;
use RuntimeException;

/** Exact public cosmetic rank allowlist; never replace primary or staff ranks. */
class DuneShopRanks
{
    private PDO $db;
    public function __construct(?PDO $db = null)
    {
        if ($db) { $this->db = $db; return; }
        $c = json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'), true, 8, JSON_THROW_ON_ERROR);
        $this->db = new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4', $c['username'], $c['password'], [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION, PDO::ATTR_EMULATE_PREPARES=>false]);
    }
    public const PUBLIC_RANKS=['user'=>'Member','patron_caravan'=>'Caravan Supporter','patron_guild'=>'Guild Patron','vip'=>'VIP','patron_grand'=>'Grand Benefactor'];
    public static function allowedRank(string $rank): bool { return isset(self::PUBLIC_RANKS[$rank]); }
    private function query(string $sql, array $values = [])
    {
        $q=$this->db->prepare($sql);$q->execute($values);return $q;
    }
    public function safeForSale(string $rank): bool
    {
        if(!self::allowedRank($rank))return false;
        $row=$this->query('SELECT kind,rank_level,permissions_json FROM cp_ranks WHERE rank_key=?',[$rank])->fetch(PDO::FETCH_ASSOC);
        if(!$row||$row['kind']!=='access'||(int)$row['rank_level']<1||(int)$row['rank_level']>25)return false;
        $member=json_decode($this->query("SELECT permissions_json FROM cp_ranks WHERE rank_key='user'")->fetchColumn()?:'[]',true,16,JSON_THROW_ON_ERROR);
        $paid=json_decode($row['permissions_json']?:'[]',true,16,JSON_THROW_ON_ERROR);
        return is_array($member)&&is_array($paid)&&!array_diff($paid,$member);
    }
    private function changed(string $account, string $action, string $receipt): void
    {
        $this->query('INSERT INTO account_audit(id,actor_id,action,details,created_at) VALUES(?,?,?,?,?)', [(string)\Illuminate\Support\Str::uuid(),$account,$action,$receipt,(int)(microtime(true)*1000)]);
        $this->query("INSERT INTO native_sync_outbox(job_key,revision,attempts,next_attempt_at,lease_token,lease_until,created_at,last_error) VALUES('memberships',?,0,0,'',0,?,'') ON DUPLICATE KEY UPDATE revision=VALUES(revision),attempts=0,next_attempt_at=0,created_at=VALUES(created_at),last_error=''", [(string)\Illuminate\Support\Str::uuid(),(int)(microtime(true)*1000)]);
    }
    public function grant(array $receipt): string
    {
        $rank=$receipt['rank']??'';$account=$receipt['account']??'';$key=$receipt['receipt']??'';
        if (($receipt['status']??'')!=='completed' || !self::allowedRank($rank)
            || !preg_match('/^[a-f0-9-]{36}$/i',$account) || !preg_match('/^azuriom:item:[1-9][0-9]*$/',$key)) throw new RuntimeException('Invalid paid rank entitlement');
        $now=(int)(microtime(true)*1000);$expiry=$receipt['expires']??null;
        if ($expiry!==null && (!is_int($expiry)||$expiry<=$now)) throw new RuntimeException('Expired rank entitlement');
        $this->db->beginTransaction();
        try {
            $user=$this->query('SELECT id,role FROM site_accounts WHERE id=? FOR UPDATE',[$account])->fetch(PDO::FETCH_ASSOC);
            if (!$user) throw new RuntimeException('Linked website account missing');
            if ($this->query('SELECT 1 FROM site_account_policies WHERE user_id=? AND game_link_exempt=1',[$account])->fetchColumn()) throw new RuntimeException('Service account cannot receive purchased ranks');
            $previous=$this->query('SELECT account_id,rank_key FROM shop_rank_entitlements WHERE receipt_key=?',[$key])->fetch(PDO::FETCH_ASSOC);
            if ($previous) {
                if ($previous['account_id']!==$account||$previous['rank_key']!==$rank) throw new RuntimeException('Receipt identity mismatch');
                $this->db->commit();return 'already-recorded';
            }
            $catalog=$this->query('SELECT kind,rank_level,permissions_json FROM cp_ranks WHERE rank_key=?',[$rank])->fetch(PDO::FETCH_ASSOC);
            if (!$catalog||$catalog['kind']!=='access'||(int)$catalog['rank_level']>25) throw new RuntimeException('Protected rank cannot be sold');
            $member=json_decode($this->query("SELECT permissions_json FROM cp_ranks WHERE rank_key='user'")->fetchColumn()?:'[]',true,16,JSON_THROW_ON_ERROR);
            $paid=json_decode($catalog['permissions_json']?:'[]',true,16,JSON_THROW_ON_ERROR);
            if(!is_array($member)||!is_array($paid)||array_diff($paid,$member))throw new RuntimeException('Paid ranks cannot grant extra website authority');
            $this->query('INSERT INTO shop_rank_entitlements(receipt_key,account_id,rank_key,active,expires_at,created_at) VALUES(?,?,?,1,?,?)',[$key,$account,$rank,$expiry,$now]);
            if ($rank!=='user') {
                $owned=$this->query('SELECT grant_owned FROM shop_rank_memberships WHERE account_id=? AND rank_key=?',[$account,$rank])->fetchColumn();
                $already=$user['role']===$rank||(bool)$this->query('SELECT 1 FROM cp_account_ranks WHERE user_id=? AND rank_key=?',[$account,$rank])->fetchColumn();
                if ($owned===false) $this->query('INSERT INTO shop_rank_memberships(account_id,rank_key,grant_owned,created_at) VALUES(?,?,?,?)',[$account,$rank,$already?0:1,$now]);
                if (!$already) {
                    $this->query('INSERT INTO cp_account_ranks(user_id,rank_key) VALUES(?,?)',[$account,$rank]);
                    $this->changed($account,'shop-'.$rank.'-granted',$key);
                }
            }
            $this->db->commit();return 'recorded';
        } catch (\Throwable $e) { if($this->db->inTransaction())$this->db->rollBack();throw $e; }
    }
    public function revoke(string $key): void
    {
        $this->db->beginTransaction();
        try {
            $e=$this->query('SELECT account_id,rank_key FROM shop_rank_entitlements WHERE receipt_key=?',[$key])->fetch(PDO::FETCH_ASSOC);
            if (!$e) {$this->db->commit();return;}
            $account=$e['account_id'];$user=$this->query('SELECT role FROM site_accounts WHERE id=? FOR UPDATE',[$account])->fetch(PDO::FETCH_ASSOC);
            $now=(int)(microtime(true)*1000);
            $this->query('UPDATE shop_rank_entitlements SET active=0,revoked_at=? WHERE receipt_key=? AND active=1',[$now,$key]);
            $rank=$e['rank_key'];
            $other=$this->query('SELECT 1 FROM shop_rank_entitlements WHERE account_id=? AND rank_key=? AND active=1 AND (expires_at IS NULL OR expires_at>?) LIMIT 1',[$account,$rank,$now])->fetchColumn();
            $owned=$this->query('SELECT grant_owned,created_at FROM shop_rank_memberships WHERE account_id=? AND rank_key=?',[$account,$rank])->fetch(PDO::FETCH_ASSOC);
            // A later manual rank assignment or an existing primary VIP stays authoritative.
            $manual=$owned?$this->query("SELECT 1 FROM account_audit WHERE action='account-ranks' AND details LIKE ? AND created_at>=? LIMIT 1",[$account.' -> %',$owned['created_at']])->fetchColumn():false;
            if ($rank!=='user'&&self::allowedRank($rank)&&!$other&&$owned&&$owned['grant_owned']&&!$manual&&($user['role']??null)!==$rank) {
                $q=$this->query('DELETE FROM cp_account_ranks WHERE user_id=? AND rank_key=?',[$account,$rank]);
                if($q->rowCount())$this->changed($account,'shop-'.$rank.'-revoked',$key);
            }
            $this->db->commit();
        } catch (\Throwable $e) {if($this->db->inTransaction())$this->db->rollBack();throw $e;}
    }
}
