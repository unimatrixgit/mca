<?php
if(PHP_SAPI!=='cli')exit(1);
$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);
$db=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
$db->beginTransaction();
try{
 $dept=$db->query("SELECT id FROM drsupport_department WHERE name='Legal'")->fetchColumn();$team=$db->query("SELECT team_id FROM drsupport_team WHERE name='Legal'")->fetchColumn();if(!$dept||!$team)throw new Exception('Legal department/team missing');
 $topic=$db->query("SELECT topic_id FROM drsupport_help_topic WHERE topic='Legal enquiries'")->fetchColumn();
 if(!$topic){$row=$db->query("SELECT * FROM drsupport_help_topic WHERE topic='General support'")->fetch();unset($row['topic_id']);$row['topic']='Legal enquiries';$row['ispublic']=0;$row['noautoresp']=1;$row['dept_id']=$dept;$row['team_id']=$team;$row['staff_id']=0;$row['created']=$row['updated']=gmdate('Y-m-d H:i:s');$q=$db->prepare('INSERT INTO drsupport_help_topic (`'.implode('`,`',array_keys($row)).'`) VALUES('.implode(',',array_fill(0,count($row),'?')).')');$q->execute(array_values($row));$topic=$db->lastInsertId();$q=$db->prepare("INSERT INTO drsupport_help_topic_form(topic_id,form_id,sort,extra) VALUES(?,2,1,'{}')");$q->execute([$topic]);}
 $q=$db->prepare("INSERT INTO drsupport_email(email,name,dept_id,topic_id,noautoresp,notes,created,updated) SELECT 'legal@dunerealms.com','DuneRealms Legal',?,?,1,'Private Legal intake',NOW(),NOW() WHERE NOT EXISTS(SELECT 1 FROM drsupport_email WHERE email='legal@dunerealms.com')");$q->execute([$dept,$topic]);
 $q=$db->prepare("UPDATE drsupport_email SET dept_id=?,topic_id=?,noautoresp=1 WHERE email IN ('legal@dunerealms.com','unimatrix@dunerealms.com')");$q->execute([$dept,$topic]);
 $q=$db->prepare("UPDATE drsupport_department SET email_id=(SELECT email_id FROM drsupport_email WHERE email='legal@dunerealms.com') WHERE id=?");$q->execute([$dept]);
 $db->commit();echo "Legal mail identity and automatic department/team routing prepared.\n";
}catch(Throwable $e){$db->rollBack();throw $e;}

