<?php
// Idempotent CLI installer: creates intake metadata only, never applications or ranks.
if(PHP_SAPI!=='cli')exit(1);
$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true,8,JSON_THROW_ON_ERROR);
$db=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
function alphaFind($table,$key,$value){global $db;$q=$db->prepare('SELECT * FROM '.$table.' WHERE `'.$key.'`=?');$q->execute([$value]);return $q->fetch();}
function alphaInsert($table,$r){global $db;$keys=array_keys($r);$q=$db->prepare('INSERT INTO '.$table.' (`'.implode('`,`',$keys).'`) VALUES('.implode(',',array_fill(0,count($keys),'?')).')');$q->execute(array_values($r));return(int)$db->lastInsertId();}
$base=alphaFind('drsupport_help_topic','topic','General support');$subject=alphaFind('drsupport_form_field','name','subject');if(!$base||!$subject)throw new RuntimeException('Native support base missing.');
$db->beginTransaction();try{
 $form=alphaFind('drsupport_form','name','dune_alpha_signup');
 if(!$form)$formId=alphaInsert('drsupport_form',['pid'=>0,'type'=>'G','flags'=>1,'title'=>'Alpha tester application','instructions'=>'Tell us how you would like to help. Use your Minecraft name, not your real name. Rough availability is enough; this is not a commitment. Do not include passwords, addresses, dates of birth or payment information. In Issue Details, briefly tell us why you would like to test.','name'=>'dune_alpha_signup','notes'=>'Private applications for manual Support review. No automatic approval, rank assignment or rewards.','created'=>gmdate('Y-m-d H:i:s'),'updated'=>gmdate('Y-m-d H:i:s')]);else $formId=(int)$form['id'];
 $fields=[
 ['alpha_minecraft','Minecraft username','text',true,['size'=>32,'length'=>32],'Include the Bedrock prefix if your name has one.'],
 ['alpha_edition','Which edition can you test?','choices',true,['choices'=>"java:Java Edition\nbedrock:Bedrock Edition\nboth:Both Java and Bedrock",'prompt'=>'Choose an edition','multiselect'=>false],''],
 ['alpha_platform','Platforms you can use','choices',true,['choices'=>"pc:PC or laptop\nconsole:Console\nmobile:Phone or tablet",'multiselect'=>true],'Select all that apply.'],
 ['alpha_availability','Approximate testing time per week','choices',true,['choices'=>"under1:Less than one hour\n1to3:1 to 3 hours\n4to7:4 to 7 hours\n8plus:8 hours or more\nflexible:Occasional or flexible",'prompt'=>'Choose an estimate','multiselect'=>false],'An estimate only; no fixed schedule is required.'],
 ['alpha_focus','What would you like to test?','choices',true,['choices'=>"accounts:Account linking and first login\ntravel:World travel and exploration\nmenus:Menus, controls and accessibility\neconomy:Items, inventory and trading\nclans:Clans, banks and kits\nquests:Quests and progression\nany:Happy to test anything",'multiselect'=>true],'Choose as many as you like. Some systems are still planned or being built.'],
 ['alpha_experience','Previous testing experience (optional)','memo',false,['rows'=>3,'cols'=>60,'length'=>700,'html'=>false],'New testers are welcome. Mention useful experience, or leave this blank.'],
 ['alpha_ack','Alpha participation','bool',true,['desc'=>'I understand this is an unfinished Alpha and will report problems constructively.'],'']
 ];
 foreach($fields as$i=>[$name,$label,$type,$required,$config,$hint]){if(alphaFind('drsupport_form_field','name',$name))continue;$r=$subject;unset($r['id']);$r['form_id']=$formId;$r['type']=$type;$r['flags']=0x3301|($required?0x400:0);$r['name']=$name;$r['label']=$label;$r['configuration']=json_encode($config,JSON_THROW_ON_ERROR);$r['hint']=$hint;$r['sort']=$i+1;$r['created']=$r['updated']=gmdate('Y-m-d H:i:s');alphaInsert('drsupport_form_field',$r);}
 $topic=alphaFind('drsupport_help_topic','topic','Alpha Tester Signup');
 if(!$topic){$r=$base;unset($r['topic_id']);$r['topic']='Alpha Tester Signup';$r['dept_id']=1;$r['ispublic']=1;$r['notes']='Alpha tester applications; Support reviews privately. No automatic rank or reward.';$r['created']=$r['updated']=gmdate('Y-m-d H:i:s');$topicId=alphaInsert('drsupport_help_topic',$r);}else $topicId=(int)$topic['topic_id'];
 $q=$db->prepare('SELECT form_id FROM drsupport_help_topic_form WHERE topic_id=?');$q->execute([$base['topic_id']]);$ids=$q->fetchAll(PDO::FETCH_COLUMN);$ids[]=$formId;
 foreach(array_unique($ids)as$id){$q=$db->prepare('SELECT id FROM drsupport_help_topic_form WHERE topic_id=? AND form_id=?');$q->execute([$topicId,$id]);if(!$q->fetch())alphaInsert('drsupport_help_topic_form',['topic_id'=>$topicId,'form_id'=>$id,'sort'=>$id==$formId?1:2,'extra'=>'{}']);}
 $db->commit();echo json_encode(['topicId'=>$topicId,'formId'=>$formId,'customFields'=>count($fields)]).PHP_EOL;
}catch(Throwable $e){$db->rollBack();throw$e;}
