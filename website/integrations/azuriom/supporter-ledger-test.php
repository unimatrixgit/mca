<?php
require __DIR__.'/DuneSupporterLedger.php';
use Azuriom\Support\DuneSupporterLedger;
$n=0;
function check($condition) { global $n; if (!$condition) throw new RuntimeException('Assertion '.($n+1).' failed'); $n++; }
function rejects(callable $call) { try {$call();} catch (Throwable $e) {check(true);return;} throw new RuntimeException('Expected rejection'); }
$db = new PDO('sqlite::memory:', null, null, [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
$db->exec('PRAGMA foreign_keys=ON; CREATE TABLE site_accounts(id TEXT PRIMARY KEY,role TEXT); CREATE TABLE supporter_receipts(receipt_key TEXT PRIMARY KEY,account_id TEXT,package_key TEXT,rewards_json TEXT,state TEXT); CREATE TABLE supporter_cosmetics(receipt_key TEXT,reward_key TEXT,PRIMARY KEY(receipt_key,reward_key),FOREIGN KEY(receipt_key) REFERENCES supporter_receipts(receipt_key));');
$a='11111111-1111-4111-8111-111111111111';$b='22222222-2222-4222-8222-222222222222';
$db->prepare('INSERT INTO site_accounts VALUES(?,?)')->execute([$a,'owners']);
$db->prepare('INSERT INTO site_accounts VALUES(?,?)')->execute([$b,'user']);
$ledger=new DuneSupporterLedger($db);
check($ledger->grant('azuriom:item:1',$a,'guild-patron','completed')==='recorded');
check($ledger->rewards($a)===['profile-frame','profile-theme','silver-seal']);
$snapshot=$db->query('SELECT rewards_json FROM supporter_receipts')->fetchColumn();
check($ledger->grant('azuriom:item:1',$a,'guild-patron','completed')==='already-recorded');
check((int)$db->query('SELECT count(*) FROM supporter_cosmetics')->fetchColumn()===3);
rejects(fn()=>$ledger->grant('azuriom:item:1',$b,'guild-patron','completed'));
rejects(fn()=>$ledger->grant('azuriom:item:1',$a,'grand-benefactor','completed'));
foreach (['pending','failed','refunded','COMPLETED'] as $status)
 rejects(fn()=>$ledger->grant('azuriom:item:2',$a,'guild-patron',$status));
foreach (['owners','vip','legendary-kit','random-power-token',''] as $package)
 rejects(fn()=>$ledger->grant('azuriom:item:2',$a,$package,'completed'));
foreach (['azuriom:item:0','azuriom:item:01','azuriom:item:1junk',"azuriom:item:1\n",''] as $key)
 rejects(fn()=>$ledger->grant($key,$a,'guild-patron','completed'));
rejects(fn()=>$ledger->grant('azuriom:item:2','33333333-3333-4333-8333-333333333333','guild-patron','completed'));
rejects(fn()=>$ledger->grant('azuriom:item:2','not-a-uuid','guild-patron','completed'));
check($ledger->grant('azuriom:item:2',$a,'caravan-supporter','completed')==='recorded');
check(count($ledger->rewards($a))===4);
check($ledger->revoke('azuriom:item:1')==='revoked');
check($ledger->rewards($a)===['bronze-seal','profile-theme']);
check($ledger->revoke('azuriom:item:1')==='already-revoked');
check($ledger->grant('azuriom:item:1',$a,'guild-patron','completed')==='already-revoked');
check($db->query("SELECT rewards_json FROM supporter_receipts WHERE receipt_key='azuriom:item:1'")->fetchColumn()===$snapshot);
check($db->query("SELECT role FROM site_accounts WHERE id='$a'")->fetchColumn()==='owners');
check($ledger->rewards($b)===[]);
// Actual SQL failure mid-grant must roll back both the receipt and any partial reward rows.
$db->exec("CREATE TRIGGER simulate_failure BEFORE INSERT ON supporter_cosmetics WHEN NEW.reward_key='profile-frame' BEGIN SELECT RAISE(ABORT,'simulated storage failure'); END;");
rejects(fn()=>$ledger->grant('azuriom:item:3',$b,'guild-patron','completed'));
check((int)$db->query("SELECT count(*) FROM supporter_receipts WHERE receipt_key='azuriom:item:3'")->fetchColumn()===0);
check((int)$db->query("SELECT count(*) FROM supporter_cosmetics WHERE receipt_key='azuriom:item:3'")->fetchColumn()===0);
check(!$db->inTransaction());
$db->exec('DROP TRIGGER simulate_failure');
check($ledger->grant('azuriom:item:3',$b,'guild-patron','completed')==='recorded');
check(count($ledger->rewards($b))===3);
check($ledger->revoke('azuriom:item:999')==='revoked');
check($ledger->grant('azuriom:item:999',$b,'guild-patron','completed')==='already-revoked');
check(count($ledger->rewards($b))===3);
echo json_encode(['assertions'=>$n,'database'=>'isolated SQLite memory','productionChanged'=>false]).PHP_EOL;
