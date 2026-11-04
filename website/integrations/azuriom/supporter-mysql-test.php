<?php
// Actual MySQL isolation/concurrency acceptance. No production rows are used.
// Usage: php supporter-mysql-test.php /private/config.json
$config=$argv[1]??'';
$c=json_decode(file_get_contents($config),true,8,JSON_THROW_ON_ERROR);
$connect=fn()=>new PDO('mysql:host='.$c['host'].';port='.($c['port']??3306).';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_EMULATE_PREPARES=>false]);
$prefix=$argv[2]??('test329_'.bin2hex(random_bytes(6)).'_');
if(!preg_match('/^test329_[a-f0-9]{12}_$/D',$prefix))throw new RuntimeException('Unsafe test namespace');
$tables=['site_accounts','site_account_policies','supporter_receipts','supporter_cosmetics'];
$source=file_get_contents(__DIR__.'/DuneSupporterLedger.php');
foreach($tables as $name)$source=str_replace($name,$prefix.$name,$source);
eval(substr($source,5));
$a='11111111-1111-4111-8111-111111111111';$b='22222222-2222-4222-8222-222222222222';
if(($argv[3]??'')==='worker') {
    $db=$connect();$service=new \Azuriom\Support\DuneSupporterLedger($db);
    try {$r=($argv[4]??'')==='revoke'?$service->revoke('azuriom:item:8'):$service->grant('azuriom:item:8',$a,'guild-patron','completed');echo $r;exit(0);}
    catch(Throwable $e){fwrite(STDERR,get_class($e));exit(2);}
}
$db=$connect();$n=0;
$check=function($ok)use(&$n){if(!$ok)throw new RuntimeException('MySQL assertion '.($n+1));$n++;};
$reject=function($call)use($check){try{$call();}catch(Throwable $e){$check(true);return;}$check(false);};
$created=[];
try {
 foreach([
 'site_accounts'=>'(id CHAR(36) PRIMARY KEY,role VARCHAR(40))',
 'site_account_policies'=>'(user_id CHAR(36) PRIMARY KEY,game_link_exempt INT)',
 'supporter_receipts'=>'(receipt_key VARCHAR(40) PRIMARY KEY,account_id CHAR(36),package_key VARCHAR(64),rewards_json JSON,state VARCHAR(16))',
 'supporter_cosmetics'=>'(receipt_key VARCHAR(40),reward_key VARCHAR(64),PRIMARY KEY(receipt_key,reward_key))',
 ]as$name=>$sql){$db->exec('CREATE TABLE '.$prefix.$name.$sql.' ENGINE=InnoDB');$created[]=$prefix.$name;}
 $db->prepare('INSERT INTO '.$prefix.'site_accounts VALUES(?,?)')->execute([$a,'owners']);
 $db->prepare('INSERT INTO '.$prefix.'site_accounts VALUES(?,?)')->execute([$b,'user']);
 $service=new \Azuriom\Support\DuneSupporterLedger($db);
 $check($service->grant('azuriom:item:1',$a,'guild-patron','completed')==='recorded');
 $check($service->grant('azuriom:item:1',$a,'guild-patron','completed')==='already-recorded');
 $check(count($service->rewards($a))===3);
 $reject(fn()=>$service->grant('azuriom:item:1',$b,'guild-patron','completed'));
 $reject(fn()=>$service->grant('azuriom:item:1',$a,'grand-benefactor','completed'));
 $reject(fn()=>$service->grant('azuriom:item:2',$a,'guild-patron','pending'));
 $db->prepare('INSERT INTO '.$prefix.'site_account_policies VALUES(?,1)')->execute([$b]);
 $reject(fn()=>$service->grant('azuriom:item:2',$b,'guild-patron','completed'));
 // Actual InnoDB partial-insert failure: fixture constraint rejects the second reward.
 $db->exec("ALTER TABLE {$prefix}supporter_cosmetics ADD CONSTRAINT test329_failure CHECK(receipt_key<>'azuriom:item:3' OR reward_key<>'profile-theme')");
 $reject(fn()=>$service->grant('azuriom:item:3',$a,'guild-patron','completed'));
 $check((int)$db->query("SELECT count(*) FROM {$prefix}supporter_receipts WHERE receipt_key='azuriom:item:3'")->fetchColumn()===0);
 $check((int)$db->query("SELECT count(*) FROM {$prefix}supporter_cosmetics WHERE receipt_key='azuriom:item:3'")->fetchColumn()===0);
 $db->exec('ALTER TABLE '.$prefix.'supporter_cosmetics DROP CONSTRAINT test329_failure');
 $check($service->grant('azuriom:item:3',$a,'caravan-supporter','completed')==='recorded');
 $check($service->revoke('azuriom:item:1')==='revoked');
 $check($service->rewards($a)===['bronze-seal','profile-theme']);
 $check($service->grant('azuriom:item:1',$a,'guild-patron','completed')==='already-revoked');
 $check($service->revoke('azuriom:item:9')==='revoked');
 $check($service->grant('azuriom:item:9',$a,'guild-patron','completed')==='already-revoked');
 $run=function(array $operations)use($config,$prefix,$check){
   $workers=[];foreach($operations as$operation){$pipes=[];$process=proc_open([PHP_BINARY,__FILE__,$config,$prefix,'worker',$operation],[0=>['pipe','r'],1=>['pipe','w'],2=>['pipe','w']],$pipes);fclose($pipes[0]);$workers[]=[$process,$pipes];}
   foreach($workers as[$process,$pipes]){$out=stream_get_contents($pipes[1]);$err=stream_get_contents($pipes[2]);fclose($pipes[1]);fclose($pipes[2]);$check(proc_close($process)===0 && $err==='' && in_array($out,['recorded','already-recorded','revoked','already-revoked'],true));}
 };
 $run(array_fill(0,8,'grant'));
 $check((int)$db->query("SELECT count(*) FROM {$prefix}supporter_receipts WHERE receipt_key='azuriom:item:8'")->fetchColumn()===1);
 $check((int)$db->query("SELECT count(*) FROM {$prefix}supporter_cosmetics WHERE receipt_key='azuriom:item:8'")->fetchColumn()===3);
 $run(['grant','revoke','grant','revoke']);
 $check($service->grant('azuriom:item:8',$a,'guild-patron','completed')==='already-revoked');
 $check($db->query("SELECT role FROM {$prefix}site_accounts WHERE id='$a'")->fetchColumn()==='owners');
 echo json_encode(['assertions'=>$n,'database'=>'namespaced real MySQL/InnoDB','parallelWorkers'=>12,'productionRowsChanged'=>false]).PHP_EOL;
} finally {
 foreach(array_reverse($created)as$name){if(!str_starts_with($name,$prefix))throw new RuntimeException('Unsafe cleanup');$db->exec('DROP TABLE '.$name);}
 echo json_encode(['fixtureTablesRemaining'=>(int)$db->query("SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE '$prefix%'")->fetchColumn()]).PHP_EOL;
}
