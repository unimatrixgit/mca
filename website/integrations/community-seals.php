<?php
if(!defined('IN_MYBB')&&!defined('ROOT_DIR'))exit;
require_once __DIR__.'/recognition.php';
function dune_community_seal_keys($websiteId){
 static $cache=[],$pdo;
 if(!preg_match('/^[a-f0-9-]{36}$/i',$websiteId))return[];
 if(isset($cache[$websiteId]))return$cache[$websiteId];
 try{
  if(!$pdo){$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);$pdo=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);}
  $q=$pdo->prepare('SELECT badge_key FROM dune_community_badges WHERE website_id=? ORDER BY FIELD(badge_key,\'alpha-tester\',\'beta-tester\',\'launch-contributor\',\'dedicated-tester\',\'season-tavren-ii\')');$q->execute([$websiteId]);
  $keys=$q->fetchAll(PDO::FETCH_COLUMN);$catalog=array_merge(['alpha-tester','beta-tester','launch-contributor','dedicated-tester','season-tavren-ii','dunegit-owner'],dune_recognition_keys());
  $q=$pdo->prepare('SELECT a.username,u.postnum FROM site_accounts a LEFT JOIN drforum_dune_accounts m ON m.website_id=a.id LEFT JOIN drforum_users u ON u.uid=m.uid WHERE a.id=?');$q->execute([$websiteId]);$row=$q->fetch(PDO::FETCH_ASSOC);
  $showcase=strtolower($row['username']??'')==='dunerealms';if($showcase)return$cache[$websiteId]=$catalog;
  // Native MyBB updates postnum on approved post/reply changes. No stale snapshot or cron delay.
  $forumKeys=array_column(dune_recognition_catalog()['forum'],'key');$keys=array_values(array_diff($keys,$forumKeys));
  $keys=array_merge($keys,array_column(dune_forum_awards_for($row['postnum']??0),'key'));
  return$cache[$websiteId]=array_values(array_unique(array_intersect($keys,$catalog)));
 }catch(Throwable$e){return[];}
}
function dune_signature_is_staff($websiteId){
 try{static$pdo,$staff=[];if(!array_key_exists($websiteId,$staff)){if(!$pdo){$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);$pdo=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);}$q=$pdo->prepare('SELECT role AS rank_key FROM site_accounts WHERE id=? UNION SELECT rank_key FROM cp_account_ranks WHERE user_id=?');$q->execute([$websiteId,$websiteId]);$staff[$websiteId]=dune_signature_staff_ranks($q->fetchAll(PDO::FETCH_COLUMN));}return$staff[$websiteId];}catch(Throwable$e){return true;}
}
function dune_signature_recognition_keys($websiteId){return dune_signature_filter_awards(dune_community_seal_keys($websiteId),dune_signature_is_staff($websiteId));}
function dune_community_seal_html($websiteId,$size=48){
 $labels=array_merge(['alpha-tester'=>'Alpha Tester','beta-tester'=>'Beta Tester','launch-contributor'=>'Launch Contributor','dedicated-tester'=>'Dedicated Tester','season-tavren-ii'=>'Season · Tavren II Aedrecht','dunegit-owner'=>'DuneGit Repository Owner'],dune_recognition_labels());$cells='';
 foreach(dune_signature_recognition_keys($websiteId)as$key)if(is_file(__DIR__.'/osticket/assets/seals/'.$key.'.png')){$label=htmlspecialchars($labels[$key],ENT_QUOTES);$cells.='<td style="padding:0 3px"><img src="https://support.dunerealms.com/assets/seals/'.$key.'.png" title="'.$label.'" alt="'.$label.'" width="48" height="48" style="display:block;width:48px;height:48px;object-fit:contain"></td>';}
 return $cells?'<div class="dune-community-seals" style="display:inline-block;max-width:100%;padding:8px;margin-top:8px;border:1px solid #65754d;border-radius:8px;background:#102d24;overflow-x:auto"><div style="font-size:10px;color:#d8c58e;margin-bottom:5px">Recognition</div><table role="presentation" cellpadding="0" cellspacing="0" border="0" style="width:auto;margin:0;white-space:nowrap"><tbody><tr>'.$cells.'</tr></tbody></table></div>':'';
}
function dune_native_central_id($account,$backend){
 if(!$account||($account->backend??'')!==$backend)return'';
 $id=$account->getUserName();if(!preg_match('/^[a-f0-9]{32}$/i',$id))return'';
 return substr($id,0,8).'-'.substr($id,8,4).'-'.substr($id,12,4).'-'.substr($id,16,4).'-'.substr($id,20,12);
}
