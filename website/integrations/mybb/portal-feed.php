<?php
// Private CLI feed: use native guest permissions, including every ancestor.
if(PHP_SAPI!=='cli')exit(1);
$_SERVER['SCRIPT_NAME']='/dune-sso.php';$_SERVER['REQUEST_METHOD']='GET';$_SERVER['REMOTE_ADDR']='127.0.0.1';$_SERVER['HTTP_HOST']='forums.dunerealms.com';
define('IN_MYBB',1);chdir('/home/dunerealms/apps/integrations/mybb');
ob_start();require 'global.php';ob_end_clean();
$forums=[];$query=$db->simple_select('forums');while($row=$db->fetch_array($query))$forums[(int)$row['fid']]=$row;
$allowed=[];
foreach($forums as $fid=>$forum){
 $current=$fid;$seen=[];$ok=true;
 while($current){
  if(isset($seen[$current])||!isset($forums[$current])){$ok=false;break;}$seen[$current]=true;$ancestor=$forums[$current];
  $permissions=forum_permissions($current,0,1);
  if(empty($ancestor['active'])||!empty($ancestor['password'])||empty($permissions['canview'])||empty($permissions['canviewthreads'])||!empty($permissions['canonlyviewownthreads'])){$ok=false;break;}
  $current=(int)$ancestor['pid'];
 }
 if($ok&&$forum['type']==='f'&&empty($forum['linkto']))$allowed[]=$fid;
}
$posts=[];
if($allowed){
 $query=$db->simple_select('threads','tid,fid,subject,username,lastpost',"visible=1 AND fid IN (".implode(',',$allowed).")",['order_by'=>'lastpost DESC,tid DESC','limit'=>9]);
 while($row=$db->fetch_array($query))$posts[]=['id'=>(int)$row['tid'],'title'=>$row['subject'],'author_name'=>$row['username'],'last_activity_at'=>(int)$row['lastpost']*1000,'excerpt'=>'Discussion in '.$forums[(int)$row['fid']]['name'],'url'=>'https://forums.dunerealms.com/showthread.php?tid='.(int)$row['tid']];
}
echo json_encode(['posts'=>$posts],JSON_UNESCAPED_SLASHES|JSON_INVALID_UTF8_SUBSTITUTE);
