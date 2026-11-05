<?php
require $argv[1];$count=0;
function check($value,$label){global $count;if(!$value)throw new RuntimeException($label);$count++;}
if(($argv[2]??'')==='mysql'){
 $c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);$db=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);$prefix='CREATE TEMPORARY TABLE ';$auto='INTEGER PRIMARY KEY AUTO_INCREMENT';$engine=' ENGINE=InnoDB';
}else{$db=new PDO('sqlite::memory:',null,null,[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);$prefix='CREATE TABLE ';$auto='INTEGER PRIMARY KEY AUTOINCREMENT';$engine='';}
// Same names deliberately shadow real tables on this private MySQL connection.
$db->exec($prefix.'drsupport_user (id INTEGER PRIMARY KEY,default_email_id INTEGER)'.$engine);
$db->exec($prefix.'drsupport_user_email (id '.$auto.',user_id INTEGER,address VARCHAR(254) UNIQUE)'.$engine);
$db->exec($prefix.'dune_contact_projection (website_id VARCHAR(36) PRIMARY KEY,address VARCHAR(254),version INTEGER NOT NULL DEFAULT -1)'.$engine);
$a='11111111-1111-4111-8111-111111111111';$b='22222222-2222-4222-8222-222222222222';
$db->prepare('INSERT INTO drsupport_user_email(id,user_id,address) VALUES(1,1,?),(2,2,?),(3,2,?)')->execute([$a.'@accounts.dunerealms.invalid',$b.'@accounts.dunerealms.invalid','other@example.org']);
$db->exec('INSERT INTO drsupport_user VALUES(1,1),(2,2)');
$value=fn($sql)=>$db->query($sql)->fetchColumn();
check(dune_contact_address('TRAVELLER@example.org')==='traveller@example.org','Normalization');
foreach(["a\r\nBcc:b@example.org",'a@accounts.dunerealms.invalid','broken']as$bad){try{dune_contact_address($bad);check(false,'Invalid address accepted');}catch(RuntimeException $e){$count++;}}
dune_contact_sync($db,$a,'first@example.org',2);
check($value('SELECT default_email_id FROM drsupport_user WHERE id=1')!=1,'Notification default switched');
check($value("SELECT user_id FROM drsupport_user_email WHERE id=1")==1,'Identity alias retained');
$default=$value('SELECT default_email_id FROM drsupport_user WHERE id=1');
dune_contact_sync($db,$a,'first@example.org',2);check($value('SELECT default_email_id FROM drsupport_user WHERE id=1')==$default,'Replay idempotent');
dune_contact_sync($db,$a,null,1);check($value('SELECT default_email_id FROM drsupport_user WHERE id=1')==$default,'Stale removal ignored');
try{dune_contact_sync($db,$a,'conflict@example.org',2);check(false,'Conflicting revision accepted');}catch(RuntimeException $e){$count++;}
try{dune_contact_sync($db,$a,'other@example.org',3);check(false,'Other account contact accepted');}catch(RuntimeException $e){$count++;}
check($value('SELECT default_email_id FROM drsupport_user WHERE id=1')==$default,'Duplicate rejection rolls back default');
check($value("SELECT version FROM dune_contact_projection WHERE website_id='$a'")==2,'Duplicate rejection rolls back revision');
check($value('SELECT default_email_id FROM drsupport_user WHERE id=2')==2,'Other identity unchanged');
dune_contact_sync($db,$a,'second@example.org',4);
check($value("SELECT COUNT(*) FROM drsupport_user_email WHERE address='first@example.org'")==0,'Old projected address no longer identifies account');
check($value('SELECT COUNT(*) FROM drsupport_user_email WHERE user_id=1')==2,'Only alias and current contact remain');
dune_contact_sync($db,$a,null,5);
check($value('SELECT default_email_id FROM drsupport_user WHERE id=1')==1,'Removal restores technical alias');
check($value('SELECT COUNT(*) FROM drsupport_user_email WHERE user_id=1')==1,'Removal detaches former contact');
check($value("SELECT COUNT(*) FROM drsupport_user_email WHERE address='other@example.org'")==1,'Unrelated email retained');
try{dune_contact_sync($db,'33333333-3333-4333-8333-333333333333','missing@example.org',1);check(false,'Missing identity accepted');}catch(RuntimeException $e){$count++;}
check($value("SELECT COUNT(*) FROM dune_contact_projection WHERE website_id='33333333-3333-4333-8333-333333333333'")==0,'Missing identity rolls back projection');
echo $count.' '.(($argv[2]??'')==='mysql'?'temporary MySQL':'SQLite').' contact projection checks passed; no live identity changed.'.PHP_EOL;
