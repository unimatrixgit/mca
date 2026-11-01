<?php
if(PHP_SAPI!=='cli')exit(1);
$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);
$db=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
$db->beginTransaction();
try{
 foreach(['Support','Ban appeals','Forum support','Legal','Sales']as$name){
  $q=$db->prepare("INSERT INTO drsupport_team(name,notes,flags,created,updated) SELECT ?,'Managed from DuneRealms website permissions',1,NOW(),NOW() WHERE NOT EXISTS(SELECT 1 FROM drsupport_team WHERE name=?)");$q->execute([$name,$name]);
 }
 foreach(['General support'=>'Support','Parental consent request [Preview]'=>'Legal','Parental data removal [Preview]'=>'Legal','Ban appeal'=>'Ban appeals','Forum support'=>'Forum support','Report forum post'=>'Forum support']as$topic=>$name){
  $q=$db->prepare('UPDATE drsupport_help_topic SET dept_id=(SELECT id FROM drsupport_department WHERE name=?),team_id=(SELECT team_id FROM drsupport_team WHERE name=?),updated=NOW() WHERE topic=?');$q->execute([$name,$name,$topic]);
 }
 $db->exec("UPDATE drsupport_ticket SET team_id=(SELECT team_id FROM drsupport_team WHERE name='Ban appeals') WHERE ticket_id IN (SELECT ticket_id FROM native_ban_appeals) AND staff_id=0 AND team_id=0");
 $db->commit();echo "Four managed teams and topic routing configured.\n";
}catch(Throwable $e){$db->rollBack();throw $e;}

