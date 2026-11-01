<?php
if(PHP_SAPI!=='cli')exit(1);
$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);
$db=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
$db->beginTransaction();
try{
 foreach(['Support'=>48,'Forum support'=>48,'Ban appeals'=>72,'Sales'=>72,'Legal'=>168]as$department=>$hours){
  $name='DuneRealms '.$department.' - '.$hours.'h';$q=$db->prepare('INSERT INTO drsupport_sla(name,schedule_id,flags,grace_period,notes,created,updated) VALUES(?,2,3,?,?,NOW(),NOW()) ON DUPLICATE KEY UPDATE schedule_id=2,grace_period=VALUES(grace_period),updated=NOW()');$q->execute([$name,$hours,'Service handling target in calendar hours, not a guaranteed resolution time. Statutory rights are unaffected.']);
  $q=$db->prepare('SELECT id FROM drsupport_sla WHERE name=?');$q->execute([$name]);$id=$q->fetchColumn();
  $q=$db->prepare('UPDATE drsupport_department SET sla_id=? WHERE name=?');$q->execute([$id,$department]);
  $q=$db->prepare('UPDATE drsupport_help_topic h JOIN drsupport_department d ON d.id=h.dept_id SET h.sla_id=? WHERE d.name=?');$q->execute([$id,$department]);
  $q=$db->prepare("UPDATE drsupport_ticket t JOIN drsupport_department d ON d.id=t.dept_id JOIN drsupport_ticket_status s ON s.id=t.status_id SET t.sla_id=?,t.est_duedate=DATE_ADD(t.created,INTERVAL ? HOUR) WHERE d.name=? AND s.state='open' AND t.sla_id IN (0,1) AND t.duedate IS NULL");$q->execute([$id,$hours,$department]);
 }
 $db->exec("UPDATE drsupport_config SET value=(SELECT id FROM drsupport_sla WHERE name='DuneRealms Support - 48h') WHERE namespace='core' AND `key`='default_sla_id'");
 $db->commit();echo "Department, topic and default SLA assignment configured.\n";
}catch(Throwable $e){$db->rollBack();throw $e;}

