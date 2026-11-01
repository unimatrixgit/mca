<?php
// Private CLI installer. Never place this file in the public support directory.
if(PHP_SAPI!=='cli')exit(1);
$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
$db=new mysqli($c['host'],$c['username'],$c['password'],$c['database']);
$row=$db->query('SELECT * FROM drsupport_help_topic WHERE topic_id=1')->fetch_assoc();
if(!$row)throw new Exception('Missing general support');
foreach(['Forum support'=>'Help using the community forums.','Report forum post'=>'Private forum moderation reports.']as$name=>$notes){
 $q=$db->prepare('SELECT topic_id FROM drsupport_help_topic WHERE topic=?');$q->bind_param('s',$name);$q->execute();
 if($r=$q->get_result()->fetch_assoc()){echo "$name exists: ".$r['topic_id']."\n";continue;}
 // Inherit the installed general-support defaults, including its form and routing settings.
 $copy=$row;unset($copy['topic_id']);$copy['topic']=$name;$copy['notes']=$notes;
 $copy['ispublic']=$name==='Forum support'?1:0;$copy['created']=$copy['updated']=date('Y-m-d H:i:s');
 $cols=implode(',',array_map(fn($s)=>'`'.$s.'`',array_keys($copy)));
 $q=$db->prepare('INSERT INTO drsupport_help_topic('.$cols.') VALUES('.implode(',',array_fill(0,count($copy),'?')).')');
 $values=array_values($copy);$q->bind_param(str_repeat('s',count($values)),...$values);$q->execute();
 echo "$name created: ".$db->insert_id."\n";
}
