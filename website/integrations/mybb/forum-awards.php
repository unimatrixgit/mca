<?php
require_once dirname(__DIR__).'/recognition.php';
function dune_forum_awards_html($user,$compact=false){
 $awards=dune_forum_awards_for($user['postnum']??0,strtolower($user['username']??'')==='dunerealms');if(!$awards)return'';
 $esc=fn($s)=>htmlspecialchars($s,ENT_QUOTES,'UTF-8');
 $image=fn($a)=>'<img src="https://www.dunerealms.com/assets/seals/'.$esc($a['file']).'" alt="'.$esc($a['label'].' · '.$a['colour']).'" title="'.$esc($a['label'].' · '.number_format($a['threshold']).' approved forum posts').'" loading="lazy" style="display:block;max-width:100%;width:180px;height:90px;object-fit:contain;margin:3px auto">';
 if($compact){$top=$awards[count($awards)-1];$html='<div class="dune-forum-awards" aria-label="Forum awards" style="max-width:190px;margin:8px auto">'.$image($top);if(count($awards)>1)$html.='<details><summary style="color:#dec98e;font-size:11px;cursor:pointer">All '.count($awards).' forum awards</summary>'.implode('',array_map($image,array_slice($awards,0,-1))).'</details>';return$html.'</div>';}
 return'<section class="dune-forum-awards" style="padding:16px;margin:12px 0;border:1px solid #9c8448;border-radius:10px;background:#143127"><h3>Forum Awards</h3><div style="display:flex;flex-wrap:wrap;gap:12px">'.implode('',array_map($image,$awards)).'</div></section>';
}
function dune_forum_post_awards($post){
 if(empty($post['uid']))return$post;
 // Use the authoritative numeric counter, not a formatted/client-supplied post field.
 global$db;static$users=[];$uid=(int)$post['uid'];if(!array_key_exists($uid,$users))$users[$uid]=$db->fetch_array($db->simple_select('users','username,postnum','uid='.$uid));
 if($users[$uid])$post['groupimage']=($post['groupimage']??'').dune_forum_awards_html($users[$uid],true);
 return$post;
}
function dune_forum_profile_awards(){global$memprofile,$profilefields;if(!empty($memprofile['uid']))$profilefields=dune_forum_awards_html($memprofile).($profilefields??'');}
