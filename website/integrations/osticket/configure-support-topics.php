<?php
// Private, idempotent installer. Intake topics do not grant consent or delete data.
if(PHP_SAPI!=='cli')exit(1);
$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);
$db=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
function insertRow($table,$row){global $db;$columns=array_keys($row);$q=$db->prepare('INSERT INTO '.$table.' (`'.implode('`,`',$columns).'`) VALUES ('.implode(',',array_fill(0,count($columns),'?')).')');$q->execute(array_values($row));return (int)$db->lastInsertId();}
function findId($table,$column,$value){global $db;$q=$db->prepare('SELECT * FROM '.$table.' WHERE `'.$column.'`=?');$q->execute([$value]);return $q->fetch();}
$db->beginTransaction();
try{
 $support=findId('drsupport_department','name','Support');if(!$support)throw new Exception('Support department missing');
 $departments=['Support'=>(int)$support['id']];
 foreach(['Ban appeals','Forum support','Legal'] as $name){$row=findId('drsupport_department','name',$name);if(!$row){$row=$support;unset($row['id']);$row['name']=$row['path']=$name;$row['ispublic']=0;$row['created']=$row['updated']=gmdate('Y-m-d H:i:s');$departments[$name]=insertRow('drsupport_department',$row);}else $departments[$name]=(int)$row['id'];}
 foreach(['Ban appeals','Forum support','Legal'] as $departmentName){$id=$departments[$departmentName];$q=$db->prepare('UPDATE drsupport_department SET path=? WHERE id=?');$q->execute(['/'.$id.'/',$id]);}
 foreach(['General support'=>'Support','Ban appeal'=>'Ban appeals','Forum support'=>'Forum support','Report forum post'=>'Forum support'] as $topic=>$dept){$q=$db->prepare('UPDATE drsupport_help_topic SET dept_id=?,updated=NOW() WHERE topic=?');$q->execute([$departments[$dept],$topic]);}
 $base=findId('drsupport_help_topic','topic','General support');if(!$base)throw new Exception('General support topic missing');
 $form=findId('drsupport_form','name','dune_parental_preview');
 if(!$form){$formId=insertRow('drsupport_form',['pid'=>0,'type'=>'G','flags'=>1,'title'=>'Parental request details · Preview','instructions'=>'Preview intake for manual staff review. No automatic consent, age unlock or data deletion occurs. Enter the child’s Minecraft username. Do not upload government ID or national registry numbers.','name'=>'dune_parental_preview','notes'=>'Preparation for the parental workflows; no automatic identity or guardian verification.','created'=>gmdate('Y-m-d H:i:s'),'updated'=>gmdate('Y-m-d H:i:s')]);
  $field=findId('drsupport_form_field','name','subject');unset($field['id']);$field['form_id']=$formId;$field['name']='child_minecraft_username';$field['label']='Child’s Minecraft username';$field['configuration']=json_encode(['size'=>32,'length'=>32]);$field['hint']='A linked Minecraft account is not required. Staff will verify the username and guardian request.';$field['sort']=1;$field['created']=$field['updated']=gmdate('Y-m-d H:i:s');insertRow('drsupport_form_field',$field);
 }else $formId=(int)$form['id'];
 foreach(['Parental consent request [Preview]','Parental data removal [Preview]'] as $name){$topic=findId('drsupport_help_topic','topic',$name);if(!$topic){$topic=$base;unset($topic['topic_id']);$topic['topic']=$name;$topic['notes']='Preview intake. Manual review only; no automatic account changes.';$topic['dept_id']=$departments['Legal'];$topic['ispublic']=1;$topic['created']=$topic['updated']=gmdate('Y-m-d H:i:s');$topicId=insertRow('drsupport_help_topic',$topic);}else $topicId=(int)$topic['topic_id'];
  $q=$db->prepare('SELECT form_id FROM drsupport_help_topic_form WHERE topic_id=?');$q->execute([$base['topic_id']]);foreach($q->fetchAll(PDO::FETCH_COLUMN) as $baseForm){$check=$db->prepare('SELECT id FROM drsupport_help_topic_form WHERE topic_id=? AND form_id=?');$check->execute([$topicId,$baseForm]);if(!$check->fetch())insertRow('drsupport_help_topic_form',['topic_id'=>$topicId,'form_id'=>$baseForm,'sort'=>2,'extra'=>'{}']);}
  $q=$db->prepare('SELECT id FROM drsupport_help_topic_form WHERE topic_id=? AND form_id=?');$q->execute([$topicId,$formId]);if(!$q->fetch())insertRow('drsupport_help_topic_form',['topic_id'=>$topicId,'form_id'=>$formId,'sort'=>1,'extra'=>'{}']);echo $name.' topic ID '.$topicId.PHP_EOL;
 }
 $db->commit();echo 'Support routing and parental preview intake prepared.'.PHP_EOL;
}catch(Throwable $e){$db->rollBack();throw $e;}
