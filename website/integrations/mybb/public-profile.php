<?php
function dune_forum_public_profile($uid){
 global$db;static$cache=[];if(isset($cache[$uid]))return$cache[$uid];
 $mapping=$db->fetch_array($db->simple_select('dune_accounts','website_id','uid='.(int)$uid));if(!$mapping)return null;
 try{require_once '/home/dunerealms/apps/integrations/managed-signatures.php';$q=dune_signature_db()->prepare('SELECT * FROM dune_public_profiles WHERE website_id=?');$q->execute([$mapping['website_id']]);$row=$q->fetch(PDO::FETCH_ASSOC)?:null;if($row){$q=dune_signature_db()->prepare('SELECT git_json FROM dune_public_git WHERE website_id=?');$q->execute([$mapping['website_id']]);$row['git']=json_decode($q->fetchColumn()?:'null',true);}return$cache[$uid]=$row;}catch(Throwable$e){return null;}
}
function dune_social_icon($key){
 $paths=[
 'youtube'=>'<rect x="2" y="5" width="20" height="14" rx="4" fill="#ff0033"/><path d="m10 8 6 4-6 4z" fill="white"/>',
 'twitch'=>'<path d="M4 2h18v14l-6 6h-5l-3 3v-3H4z" fill="#9146ff"/><path d="M8 6h10v8l-3 3h-4l-3 3v-3H8z" fill="white"/><path d="M11 8v5m4-5v5" stroke="#9146ff" stroke-width="2"/>',
 'twitter'=>'<path d="M4 3h5l11 18h-5zM20 3 4 21" fill="none" stroke="#eee6d2" stroke-width="2"/>',
 'facebook'=>'<rect x="2" y="2" width="20" height="20" rx="4" fill="#1877f2"/><path d="M14 22v-8h3l1-4h-4V8c0-1 1-2 3-1V3c-6-1-7 2-7 5v2H7v4h3v8z" fill="white"/>',
 'instagram'=>'<rect x="3" y="3" width="18" height="18" rx="5" fill="none" stroke="#f36dba" stroke-width="2"/><circle cx="12" cy="12" r="4" fill="none" stroke="#f36dba" stroke-width="2"/><circle cx="17" cy="7" r="1.5" fill="#f36dba"/>',
 'dunehub'=>'<path d="M3 18 8 6l4 8 3-5 6 9zM12 3v4M10 5h4" fill="none" stroke="#e0be71" stroke-width="2"/>',
 'github'=>'<circle cx="12" cy="12" r="10" fill="#e8e0c9"/><text x="12" y="16" font-size="10" font-weight="bold" text-anchor="middle" fill="#102d24">GH</text>',
 'tiktok'=>'<path d="M14 3v12a4 4 0 1 1-4-4m4-8c1 4 3 5 6 5" fill="none" stroke="#25f4ee" stroke-width="4"/><path d="M15 3v12a4 4 0 1 1-4-4m4-8c1 4 3 5 6 5" fill="none" stroke="#fe2c55" stroke-width="2"/>',
 'discord'=>'<path d="M5 5c5-2 9-2 14 0l3 13-5 3-2-3h-6l-2 3-5-3z" fill="#8895f2"/><circle cx="8" cy="12" r="2" fill="white"/><circle cx="16" cy="12" r="2" fill="white"/>',
 'website'=>'<circle cx="12" cy="12" r="9" fill="none" stroke="#e3ca8b" stroke-width="2"/><path d="M3 12h18M12 3c-5 5-5 13 0 18M12 3c5 5 5 13 0 18" fill="none" stroke="#e3ca8b" stroke-width="1.5"/>'
 ];return isset($paths[$key])?'<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">'.$paths[$key].'</svg>':'';
}
function dune_public_social_links($profile){
 $labels=['twitch'=>'Twitch','youtube'=>'YouTube','twitter'=>'X / Twitter','facebook'=>'Facebook','instagram'=>'Instagram','tiktok'=>'TikTok','discord'=>'Discord','github'=>'GitHub','dunehub'=>'DuneHub','website'=>'Website'];$socials=json_decode($profile['socials_json']??'{}',true)?:[];$html='';
 foreach($labels as$key=>$label)if(!empty($socials[$key]))$html.='<a href="'.htmlspecialchars($socials[$key],ENT_QUOTES).'" target="_blank" rel="noopener noreferrer nofollow ugc" title="'.$label.'" aria-label="'.$label.'" style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;margin:0 6px 6px 0;border:1px solid #6d754a;border-radius:6px;background:#102d24">'.dune_social_icon($key).'</a>';
 return$html?'<div class="dune-author-socials" style="display:flex;flex-wrap:wrap;align-items:center;margin-top:8px">'.$html.'</div>':'';
}
function dune_forum_author_profile($post){
 if(empty($post['uid']))return$post;
 global$db;$mapping=$db->fetch_array($db->simple_select('dune_accounts','website_id','uid='.(int)$post['uid']));
 if($mapping)try{require_once '/home/dunerealms/apps/integrations/managed-signatures.php';$q=dune_signature_db()->prepare('SELECT rank_label FROM dune_signature_rank_labels WHERE website_id=?');$q->execute([$mapping['website_id']]);if($label=$q->fetchColumn())$post['usertitle']=htmlspecialchars($label,ENT_QUOTES);}catch(Throwable$e){}
 if(!($profile=dune_forum_public_profile((int)$post['uid'])))return$post;
 $post['groupimage']=($post['groupimage']??'').dune_public_social_links($profile);
 if($profile['bio']!==''){$bio=function_exists('mb_strimwidth')?mb_strimwidth($profile['bio'],0,120,'…') :substr($profile['bio'],0,120);$post['groupimage'].='<div style="font-size:11px;line-height:16px;color:#c6d0bd;max-width:340px;margin-top:4px">'.htmlspecialchars($bio,ENT_QUOTES).'</div>';}
 $avatar=json_decode($profile['avatar_json']??'{}',true)?:[];if(($avatar['mode']??'')==='skin'){$url=htmlspecialchars($avatar['url'],ENT_QUOTES);$post['useravatar']='<div class="author_avatar"><a href="member.php?action=profile&amp;uid='.(int)$post['uid'].'"><span role="img" aria-label="Minecraft avatar" style="display:block;position:relative;width:64px;height:64px;background-image:url(\''.$url.'\');background-size:512px 512px;background-position:-64px -64px;image-rendering:pixelated;border:1px solid #8d8149;border-radius:6px"><span style="position:absolute;inset:0;background-image:url(\''.$url.'\');background-size:512px 512px;background-position:-320px -64px;image-rendering:pixelated"></span></span></a></div>';}
 return$post;
}
function dune_forum_member_public_profile(){
 global$memprofile,$profilefields,$usertitle,$avatar,$signature,$mybb,$db;
 $uid=(int)($memprofile['uid']??0);if(!$uid||!($p=dune_forum_public_profile($uid)))return;
 $decorated=dune_forum_author_profile(['uid'=>$uid]);if(isset($decorated['usertitle']))$usertitle=$decorated['usertitle'];if(isset($decorated['useravatar']))$avatar=$decorated['useravatar'];
 $text=($p['bio']!==''?'<p>'.nl2br(htmlspecialchars($p['bio'],ENT_QUOTES)).'</p>':'').($p['public_info']!==''?'<p>'.nl2br(htmlspecialchars($p['public_info'],ENT_QUOTES)).'</p>':'');
 $git=$p['git']??null;$gitActions=$git?'<div class="dune-git-actions" style="display:flex;gap:12px;flex-wrap:wrap;margin:12px 0"><a href="'.htmlspecialchars($git['profileUrl'],ENT_QUOTES).'">'.dune_social_icon('dunehub').' DuneGit · '.htmlspecialchars($git['login'],ENT_QUOTES).'</a><a href="https://www.dunerealms.com/git/">Browse repositories</a><a href="https://www.dunerealms.com/git/sso/start?return_to=%2Fgit%2Frepo%2Fcreate">Create a repository</a></div>':'';
 $profilefields='<section style="padding:16px;margin:12px 0;border:1px solid #6d754a;border-radius:10px;background:#143127;color:#eee6d2"><h3>About & social links</h3>'.$text.dune_public_social_links($p).$gitActions.'<a href="https://cp.dunerealms.com/#profile" style="font-size:11px">Manage your public profile in the Account center</a></section>'.$profilefields;
 if(empty($mybb->user['uid'])||!empty($mybb->user['showsigs'])){$mapping=$db->fetch_array($db->simple_select('dune_accounts','website_id','uid='.$uid));if($mapping)$signature=dune_render_managed_signature($mapping['website_id'],'forum');}
}
