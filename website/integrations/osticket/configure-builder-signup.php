<?php
// Reuse the Alpha structured intake scaffold. Metadata only: no applications, approvals or rank grants.
if(PHP_SAPI!=='cli')exit(1);
$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true,8,JSON_THROW_ON_ERROR);
$db=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
function builderFind($table,$key,$value){global$db;$q=$db->prepare('SELECT * FROM '.$table.' WHERE `'.$key.'`=?');$q->execute([$value]);return$q->fetch();}
function builderInsert($table,$r){global$db;$keys=array_keys($r);$q=$db->prepare('INSERT INTO '.$table.' (`'.implode('`,`',$keys).'`) VALUES('.implode(',',array_fill(0,count($keys),'?')).')');$q->execute(array_values($r));return(int)$db->lastInsertId();}
$base=builderFind('drsupport_help_topic','topic','General support');$subject=builderFind('drsupport_form_field','name','subject');if(!$base||!$subject)throw new RuntimeException('Native support base missing.');
$db->beginTransaction();try{
 $form=builderFind('drsupport_form','name','dune_builder_signup');
 $formId=$form?(int)$form['id']:builderInsert('drsupport_form',['pid'=>0,'type'=>'G','flags'=>1,'title'=>'Builder application','instructions'=>'Help shape DuneRealms. Use your Minecraft name, not your real name. Share examples of your own builds if available. Never include passwords, home addresses, dates of birth or payment details. Applications are reviewed privately by staff.','name'=>'dune_builder_signup','notes'=>'Community Builder applications. Manual staff review; no automatic acceptance, rank, permissions or award.','created'=>gmdate('Y-m-d H:i:s'),'updated'=>gmdate('Y-m-d H:i:s')]);
 $fields=[
 ['builder_minecraft','Minecraft username','text',true,['size'=>32,'length'=>32],'Include your Bedrock prefix if applicable.'],
 ['builder_edition','Minecraft edition','choices',true,['choices'=>"java:Java Edition\nbedrock:Bedrock Edition\nboth:Both",'prompt'=>'Choose an edition','multiselect'=>false],''],
 ['builder_availability','Approximate building time per week','choices',true,['choices'=>"under1:Less than one hour\n1to3:1 to 3 hours\n4to7:4 to 7 hours\n8plus:8 hours or more\nflexible:Occasional or flexible",'prompt'=>'Choose an estimate','multiselect'=>false],'An estimate, not a fixed commitment.'],
 ['builder_focus','What would you like to build?','choices',true,['choices'=>"cities:Desert cities and settlements\nsietches:Sietches and underground rooms\nlandmarks:Palaces and landmarks\ninteriors:Interiors and detail\nlandscape:Landscaping and atmosphere\nstarports:Starports and infrastructure\nany:Happy to help anywhere",'multiselect'=>true],'Select all that interest you.'],
 ['builder_experience','Building experience','memo',false,['rows'=>3,'cols'=>60,'length'=>1000,'html'=>false],'Tell us about your style and any tools you use.'],
 ['builder_portfolio','Examples of your own builds (optional)','memo',false,['rows'=>3,'cols'=>60,'length'=>1500,'html'=>false],'Portfolio or screenshot links are welcome. Native ticket attachments may also be used. Only share work you made or have permission to share.'],
 ['builder_motivation','Why would you like to build for DuneRealms?','memo',true,['rows'=>4,'cols'=>60,'length'=>1500,'html'=>false],'A short introduction is enough.'],
 ['builder_ack','Builder application','bool',true,['desc'=>'I understand that staff will review this application and that applying does not automatically grant a rank, building permissions or an award.'],'']
 ];
 foreach($fields as$i=>[$name,$label,$type,$required,$configuration,$hint]){if(builderFind('drsupport_form_field','name',$name))continue;$r=$subject;unset($r['id']);$r['form_id']=$formId;$r['type']=$type;$r['flags']=0x3301|($required?0x400:0);$r['name']=$name;$r['label']=$label;$r['configuration']=json_encode($configuration,JSON_THROW_ON_ERROR);$r['hint']=$hint;$r['sort']=$i+1;$r['created']=$r['updated']=gmdate('Y-m-d H:i:s');builderInsert('drsupport_form_field',$r);}
 $topic=builderFind('drsupport_help_topic','topic','Builder Application');
 if(!$topic){$r=$base;unset($r['topic_id']);$r['topic']='Builder Application';$r['dept_id']=$base['dept_id'];$r['ispublic']=1;$r['notes']='Private Builder applications, manually reviewed. Applying does not grant ranks, permissions or awards.';$r['created']=$r['updated']=gmdate('Y-m-d H:i:s');$topicId=builderInsert('drsupport_help_topic',$r);}else$topicId=(int)$topic['topic_id'];
 $q=$db->prepare('SELECT form_id FROM drsupport_help_topic_form WHERE topic_id=?');$q->execute([$base['topic_id']]);$ids=$q->fetchAll(PDO::FETCH_COLUMN);$ids[]=$formId;
 foreach(array_unique($ids)as$id){$q=$db->prepare('SELECT id FROM drsupport_help_topic_form WHERE topic_id=? AND form_id=?');$q->execute([$topicId,$id]);if(!$q->fetch())builderInsert('drsupport_help_topic_form',['topic_id'=>$topicId,'form_id'=>$id,'sort'=>$id==$formId?1:2,'extra'=>'{}']);}
 $db->commit();echo json_encode(['topicId'=>$topicId,'formId'=>$formId,'customFields'=>count($fields)]).PHP_EOL;
}catch(Throwable$e){$db->rollBack();throw$e;}
