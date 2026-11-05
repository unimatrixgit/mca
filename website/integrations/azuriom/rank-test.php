<?php
require '/home/dunerealms/apps/azuriom/vendor/autoload.php';
require_once __DIR__.'/DuneShopRanks.php';
use Azuriom\Support\DuneShopRanks;
$c=json_decode(isset($argv[1])?file_get_contents($argv[1]):stream_get_contents(STDIN),true,8,JSON_THROW_ON_ERROR);
$db=new PDO('mysql:host='.$c['host'].';dbname='.($c['database']??'website').';charset=utf8mb4',($c['username']??$c['user name']),$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
// Every table touched by the bridge is shadowed on this connection only.
foreach([
'site_accounts(id CHAR(36) PRIMARY KEY,role VARCHAR(32))',
'site_account_policies(user_id CHAR(36) PRIMARY KEY,game_link_exempt INT)',
'cp_ranks(rank_key VARCHAR(32) PRIMARY KEY,kind VARCHAR(32),rank_level INT,permissions_json TEXT)',
'cp_account_ranks(user_id CHAR(36),rank_key VARCHAR(32),PRIMARY KEY(user_id,rank_key))',
'shop_rank_entitlements(receipt_key VARCHAR(96) PRIMARY KEY,account_id CHAR(36),rank_key VARCHAR(16),active BOOLEAN,expires_at BIGINT,created_at BIGINT,revoked_at BIGINT)',
'shop_rank_memberships(account_id CHAR(36),rank_key VARCHAR(16),grant_owned BOOLEAN,created_at BIGINT,PRIMARY KEY(account_id,rank_key))',
'account_audit(id CHAR(36) PRIMARY KEY,actor_id CHAR(36),action VARCHAR(64),details TEXT,created_at BIGINT)',
'native_sync_outbox(job_key VARCHAR(32) PRIMARY KEY,revision CHAR(36),attempts INT,next_attempt_at BIGINT,lease_token CHAR(36),lease_until BIGINT,created_at BIGINT,last_error VARCHAR(80))'
] as $schema)$db->exec('CREATE TEMPORARY TABLE '.$schema.' ENGINE=InnoDB');
$checks=0;function verify($ok){global $checks;$checks++;if(!$ok)throw new RuntimeException('Paid rank regression #'.$checks);}
function rejected($fn){try{$fn();return false;}catch(Throwable $e){return true;}}
$a='10000000-0000-4000-8000-000000000001';$b='10000000-0000-4000-8000-000000000002';$staff='10000000-0000-4000-8000-000000000003';$d='10000000-0000-4000-8000-000000000004';$fail='10000000-0000-4000-8000-000000000005';
foreach([[$a,'user'],[$b,'vip'],[$staff,'owners'],[$d,'user'],[$fail,'user']] as $row)$db->prepare('INSERT INTO site_accounts VALUES(?,?)')->execute($row);
$db->exec("INSERT INTO cp_ranks(rank_key,kind,rank_level) VALUES('user','access',10),('vip','access',20),('owners','access',100)");
$service=new DuneShopRanks($db);
$receipt=fn($id,$who,$rank='vip',$status='completed')=>['receipt'=>'azuriom:item:'.$id,'account'=>$who,'rank'=>$rank,'status'=>$status];
$has=fn($who)=>(int)$db->query("SELECT COUNT(*) FROM cp_account_ranks WHERE user_id='$who' AND rank_key='vip'")->fetchColumn();
verify($service->grant($receipt(1,$a))==='recorded');verify($has($a)===1);
verify($service->grant($receipt(1,$a))==='already-recorded');verify($has($a)===1);
verify(rejected(fn()=>$service->grant($receipt(1,$b))));
verify(rejected(fn()=>$service->grant($receipt(3,$a,'owners'))));
verify(rejected(fn()=>$service->grant($receipt(4,$a,'vip','pending'))));
$service->grant($receipt(2,$a));verify($has($a)===1);$service->revoke('azuriom:item:1');verify($has($a)===1);$service->revoke('azuriom:item:2');verify($has($a)===0);
verify($service->grant($receipt(1,$a))==='already-recorded');verify($has($a)===0);
$service->grant($receipt(5,$b));$service->revoke('azuriom:item:5');verify($db->query("SELECT role FROM site_accounts WHERE id='$b'")->fetchColumn()==='vip');
$service->grant($receipt(6,$staff));verify($has($staff)===1);$service->revoke('azuriom:item:6');verify($db->query("SELECT role FROM site_accounts WHERE id='$staff'")->fetchColumn()==='owners');
$service->grant($receipt(7,$staff,'user'));verify($db->query("SELECT role FROM site_accounts WHERE id='$staff'")->fetchColumn()==='owners');
$service->grant($receipt(8,$d));$q=$db->prepare('INSERT INTO account_audit VALUES(?,?,?,?,?)');$q->execute([(string)Illuminate\Support\Str::uuid(),$d,'account-ranks',$d.' -> user,vip',(int)(microtime(true)*1000)+1]);$service->revoke('azuriom:item:8');verify($has($d)===1);
$db->exec("UPDATE cp_ranks SET rank_level=90 WHERE rank_key='vip'");verify(rejected(fn()=>$service->grant($receipt(9,$fail))));verify($has($fail)===0);$db->exec("UPDATE cp_ranks SET rank_level=20 WHERE rank_key='vip'");
foreach(['patron_caravan'=>12,'patron_guild'=>15,'patron_grand'=>25,'council_member'=>20] as $rank=>$level){
 $db->prepare('INSERT INTO cp_ranks(rank_key,kind,rank_level) VALUES(?,?,?)')->execute([$rank,'access',$level]);
 $id=100+$level;$service->grant($receipt($id,$a,$rank));
 verify((int)$db->query("SELECT count(*) FROM cp_account_ranks WHERE user_id='$a' AND rank_key='$rank'")->fetchColumn()===1);
 verify($service->grant($receipt($id,$a,$rank))==='already-recorded');
 $service->revoke('azuriom:item:'.$id);
 verify((int)$db->query("SELECT count(*) FROM cp_account_ranks WHERE user_id='$a' AND rank_key='$rank'")->fetchColumn()===0);
 verify($service->grant($receipt($id,$a,$rank))==='already-recorded');
 verify((int)$db->query("SELECT count(*) FROM cp_account_ranks WHERE user_id='$a' AND rank_key='$rank'")->fetchColumn()===0);
}
// Multiple paid Council periods: revoking one receipt cannot erase the renewal.
$rank='council_member';$now=(int)(microtime(true)*1000);
$r1=$receipt(201,$a,$rank)+['expires'=>$now+3600000];$r2=$receipt(202,$a,$rank)+['expires'=>$now+7200000];
verify($service->grant($r1)==='recorded');verify($service->grant($r2)==='recorded');
$councilCount=fn()=>(int)$db->query("SELECT count(*) FROM cp_account_ranks WHERE user_id='$a' AND rank_key='council_member'")->fetchColumn();
verify($councilCount()===1);$service->revoke('azuriom:item:201');verify($councilCount()===1);
verify($service->grant($r2)==='already-recorded');$service->revoke('azuriom:item:202');verify($councilCount()===0);
$service->revoke('azuriom:item:202');verify($councilCount()===0);
verify(rejected(fn()=>$service->grant($receipt(203,$a,$rank)+['expires'=>$now-1])));
verify($service->grant($r1)==='already-recorded');verify($councilCount()===0);
// A separately granted Council role is not owned by the paid bridge.
$db->prepare('INSERT INTO cp_account_ranks(user_id,rank_key) VALUES(?,?)')->execute([$b,$rank]);
$service->grant($receipt(204,$b,$rank)+['expires'=>$now+3600000]);$service->revoke('azuriom:item:204');
verify((int)$db->query("SELECT count(*) FROM cp_account_ranks WHERE user_id='$b' AND rank_key='council_member'")->fetchColumn()===1);
$db->prepare('INSERT INTO site_account_policies VALUES(?,1)')->execute([$fail]);
verify(rejected(fn()=>$service->grant($receipt(150,$fail,'patron_grand'))));
$db->exec('DELETE FROM site_account_policies');
$db->exec('DELETE FROM native_sync_outbox');$db->exec("ALTER TABLE native_sync_outbox ADD CONSTRAINT reject_delivery CHECK(revision='deny')");
verify(rejected(fn()=>$service->grant($receipt(10,$fail))));verify($has($fail)===0);verify((int)$db->query("SELECT COUNT(*) FROM shop_rank_entitlements WHERE receipt_key='azuriom:item:10'")->fetchColumn()===0);verify((int)$db->query("SELECT COUNT(*) FROM account_audit WHERE actor_id='$fail'")->fetchColumn()===0);
echo "Paid rank bridge: $checks checks passed using connection-local temporary tables; no real users or payments changed.\n";

