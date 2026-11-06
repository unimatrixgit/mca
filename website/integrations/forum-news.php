<?php
// Private CLI adapter: native forum validation and exact source ownership remain authoritative.
if(PHP_SAPI!=='cli')exit(1);
$input=json_decode(stream_get_contents(STDIN),true);
if(!is_array($input)||!preg_match('/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i',$input['id']??''))exit(2);
$status=$input['status']??'published';$modern=isset($input['revision']);
if(!in_array($status,['published','archived'],true)||($modern&&!preg_match('/^[0-9a-f]{64}$/',$input['revision'])))exit(2);
if($status==='published'&&(!is_numeric($input['published_at']??null)||(int)$input['published_at']>(int)(microtime(true)*1000)||!is_string($input['title']??null)||!is_string($input['body']??null)))exit(2);
$_SERVER['SCRIPT_NAME']='/dune-sso.php';$_SERVER['REQUEST_METHOD']='GET';$_SERVER['REMOTE_ADDR']='127.0.0.1';$_SERVER['HTTP_HOST']='forums.dunerealms.com';define('IN_MYBB',1);chdir('/home/dunerealms/apps/integrations/mybb');ob_start();require 'global.php';ob_end_clean();
$fid=14;$uid=2;$author=get_user($uid);if(strtolower($author['username']??'')!=='dunerealms')exit(3);$mybb->user=$author;$permissions=forum_permissions($fid,$uid);if(empty($permissions['canpostthreads'])||empty($permissions['canview']))exit(3);
$current=$fid;$seen=[];while($current){if(isset($seen[$current]))exit(3);$seen[$current]=true;$forum=get_forum($current);$guest=forum_permissions($current,0,1);if(empty($forum['active'])||!empty($forum['password'])||empty($guest['canview'])||empty($guest['canviewthreads'])||!empty($guest['canonlyviewownthreads']))exit(3);$current=(int)$forum['pid'];}
$id=$db->escape_string($input['id']);$lock='dune-news-'.$id;$locked=$db->fetch_field($db->query("SELECT GET_LOCK('{$lock}',5) AS acquired"),'acquired');if(!$locked)exit(4);
try{
 $db->write_query('CREATE TABLE IF NOT EXISTS '.TABLE_PREFIX.'dune_news_delivery(post_id CHAR(36) PRIMARY KEY,state VARCHAR(16) NOT NULL,tid INT NOT NULL DEFAULT 0,pid INT NOT NULL DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');
 $db->write_query('CREATE TABLE IF NOT EXISTS '.TABLE_PREFIX.'dune_news_revision(post_id CHAR(36) PRIMARY KEY,revision VARCHAR(64) NOT NULL,state VARCHAR(16) NOT NULL,updated_at BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');
 $mapping=$db->fetch_array($db->simple_select('dune_news_delivery','*',"post_id='{$id}'"));
 $marker='DuneRealms source: '.$input['id'];
 if(!$mapping||!in_array($mapping['state'],['sent','archived'],true)){
  // Reconcile a timeout/crash by immutable marker; never blindly create twice.
  $query=$db->query('SELECT t.tid,p.pid FROM '.TABLE_PREFIX.'threads t JOIN '.TABLE_PREFIX.'posts p ON p.pid=t.firstpost WHERE t.fid=14 AND t.uid=2 AND p.message LIKE \'%'.$db->escape_string($marker).'%\' LIMIT 2');$matches=[];while($row=$db->fetch_array($query))$matches[]=$row;
  if(count($matches)===1){$mapping=['post_id'=>$input['id'],'state'=>'sent','tid'=>(int)$matches[0]['tid'],'pid'=>(int)$matches[0]['pid']];$db->replace_query('dune_news_delivery',$mapping,'post_id');}
  elseif($mapping||count($matches)>1)throw new Exception('Interrupted native creation requires review');
 }
 $thread=null;$first=null;
 if($mapping){
  $thread=get_thread((int)$mapping['tid']);$first=get_post((int)$mapping['pid']);
  if(!$thread||!$first||(int)$thread['fid']!==14||(int)$thread['visible']!==1||(int)$thread['uid']!==2||(int)$thread['firstpost']!==(int)$first['pid']||(int)$first['uid']!==2||(int)$first['tid']!==(int)$thread['tid']||(int)$first['visible']!==1||strpos($first['message'],$marker)===false)throw new Exception('Managed topic ownership or visibility changed');
 }
 if($status==='archived'&&!$mapping)throw new Exception('Withdrawal cannot create a new topic');
 $revision=$modern?$input['revision']:hash('sha256',json_encode($input));
 $saved=$db->fetch_array($db->simple_select('dune_news_revision','*',"post_id='{$id}'"));
 $state=$status==='published'?'sent':'archived';
 $reply=function($tid)use($state,$revision,$modern){$out=['state'=>$state,'thread_id'=>(int)$tid];if($modern)$out['revision']=$revision;echo json_encode($out);};
 if($saved&&!$mapping)throw new Exception('Revision mapping requires reconciliation');
 if($saved&&$saved['revision']===$revision&&$saved['state']===$state){$reply($mapping['tid']);exit;}
 $safe=fn($s)=>str_replace(['[',']'],['&#91;','&#93;'],(string)$s);$url='https://www.dunerealms.com/news-archive.html?post='.$input['id'];
 if($status==='published'){
  $labels=['world'=>'World','news'=>'NEWS','maintenance'=>'Maintenance','changelogs'=>'Changelogs','events'=>'Events','community'=>'Community','unis_blog'=>"Uni’s Blog"];$label=$labels[$input['category']??'news']??'NEWS';$prefix=$db->fetch_array($db->simple_select('threadprefixes','pid',"prefix='".$db->escape_string($label)."' AND forums='14'"));
  if(!$prefix){$prefix=['pid'=>$db->insert_query('threadprefixes',['prefix'=>$label,'displaystyle'=>'<span class="dune-news-tag">'.htmlspecialchars_uni($label).'</span>','forums'=>'14','groups'=>'-1'])];$cache->update_threadprefixes();}
  // MyBB's native validator limits subjects to85 characters; website titles allow120.
  $subject=my_substr($input['title'],0,85);$prefix_id=(int)$prefix['pid'];
  $message='[b]'.$safe($label).'[/b]'."\n".'Written by '.$safe($input['author_name']??'DuneRealms')."\n".'Originally published '.gmdate('j F Y, H:i',(int)($input['published_at']/1000))." UTC\n\n".$safe($input['body'])."\n\n[url=".$url."]Read the original article[/url]\n\n[size=x-small]".$marker.'[/size]';
  if(my_strlen($input['title'])>85)$message='[b]'.$safe($input['title'])."[/b]\n\n".$message;
 }else{
  $subject=my_substr('[Archived] '.preg_replace('/^\[Archived\] /','',$thread['subject']),0,85);$prefix_id=(int)$thread['prefix'];
  $message="[b]Archived announcement[/b]\n\nThis article has been withdrawn from the DuneRealms website. Earlier replies are retained for context.\n\n[size=x-small]".$marker.'[/size]';
 }
 require_once MYBB_ROOT.'inc/datahandlers/post.php';
 if($mapping){
  if(empty($permissions['caneditposts'])&&!is_moderator($fid,'caneditposts',$uid))throw new Exception('Native edit permission denied');
  $earliest=$db->fetch_array($db->simple_select('posts','pid',"tid='".(int)$thread['tid']."'",['order_by'=>'dateline,pid','limit'=>1]));if((int)($earliest['pid']??0)!==(int)$first['pid'])throw new Exception('Native first post requires review');
  if($first['subject']!==$subject||$first['message']!==$message||(int)$thread['prefix']!==$prefix_id){
   $handler=new PostDataHandler('update');$handler->set_data(['pid'=>(int)$first['pid'],'tid'=>(int)$thread['tid'],'fid'=>$fid,'uid'=>$uid,'edit_uid'=>$uid,'subject'=>$subject,'prefix'=>$prefix_id,'message'=>$message,'editreason'=>$status==='published'?'Synchronized website article revision':'Website article withdrawn; replies retained','options'=>['signature'=>0,'disablesmilies'=>1]]);
   if(!$handler->validate_post())throw new Exception('Native edit validation failed');$handler->update_post();
   $check=$db->fetch_array($db->simple_select('posts','message,subject,visible',"pid='".(int)$first['pid']."'"));$checkThread=$db->fetch_array($db->simple_select('threads','subject,prefix',"tid='".(int)$thread['tid']."'"));if(!$check||!$checkThread||$check['message']!==$message||$check['subject']!==$subject||(int)$check['visible']!==1||$checkThread['subject']!==$subject||(int)$checkThread['prefix']!==$prefix_id)throw new Exception('Native edit requires review');
  }
  $tid=(int)$thread['tid'];$pid=(int)$first['pid'];
 }else{
  $handler=new PostDataHandler('insert');$handler->action='thread';$handler->set_data(['fid'=>$fid,'uid'=>$uid,'username'=>$author['username'],'subject'=>$subject,'prefix'=>$prefix_id,'message'=>$message,'dateline'=>(int)($input['published_at']/1000),'ipaddress'=>'127.0.0.1','savedraft'=>0,'options'=>['signature'=>0,'disablesmilies'=>1,'subscriptionmethod'=>0]]);
  if(!$handler->validate_thread())throw new Exception('Native post validation failed');$db->insert_query('dune_news_delivery',['post_id'=>$input['id'],'state'=>'creating','tid'=>0,'pid'=>0]);$result=$handler->insert_thread();$tid=(int)($result['tid']??0);$pid=(int)($result['pid']??0);$thread=get_thread($tid);if(!$tid||!$pid||!$thread||(int)$thread['visible']!==1)throw new Exception('Native topic visibility requires review');
 }
 $db->update_query('dune_news_delivery',['state'=>$state,'tid'=>$tid,'pid'=>$pid],"post_id='{$id}'");
 $db->replace_query('dune_news_revision',['post_id'=>$input['id'],'revision'=>$revision,'state'=>$state,'updated_at'=>(int)(microtime(true)*1000)],'post_id');$reply($tid);
}catch(Throwable $e){fwrite(STDERR,'Forum news delivery requires review'.PHP_EOL);exit(5);}finally{$db->query("SELECT RELEASE_LOCK('{$lock}')");}
