<?php
if(PHP_SAPI!=='cli')exit(1);
$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);$p=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
$p->beginTransaction();try{
 $name='Network staff';$q=$p->prepare('SELECT id FROM drsupport_department WHERE name=?');$q->execute([$name]);$id=$q->fetchColumn();
 if(!$id){$row=$p->query("SELECT * FROM drsupport_department WHERE name='Support'")->fetch();if(!$row)throw new Exception('Support department missing');unset($row['id']);$row['name']=$name;$row['ispublic']=0;$row['signature']='';$row['created']=$row['updated']=gmdate('Y-m-d H:i:s');$q=$p->prepare('INSERT INTO drsupport_department (`'.implode('`,`',array_keys($row)).'`) VALUES('.implode(',',array_fill(0,count($row),'?')).')');$q->execute(array_values($row));$id=$p->lastInsertId();}
 $q=$p->prepare('UPDATE drsupport_department SET ispublic=0 WHERE id=?');$q->execute([$id]);
 $q=$p->prepare("INSERT INTO drsupport_team(name,notes,flags,created,updated) SELECT ?,'Managed by explicit DuneRealms Network Staff permission',1,NOW(),NOW() WHERE NOT EXISTS(SELECT 1 FROM drsupport_team WHERE name=?)");$q->execute([$name,$name]);
 $p->commit();echo "Private Network staff department and team configured.\n";
}catch(Throwable$e){$p->rollBack();throw$e;}
