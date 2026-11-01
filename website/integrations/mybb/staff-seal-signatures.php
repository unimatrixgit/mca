<?php
function dune_forum_staff_signature($post){
 global $db,$mybb,$parser;
 if(empty($post['uid'])||(!empty($mybb->user['uid'])&&empty($mybb->user['showsigs'])))return$post;
 if(!empty($post['suspendsignature'])&&(empty($post['suspendsigtime'])||$post['suspendsigtime']>TIME_NOW))return$post;
 $managed='/home/dunerealms/apps/integrations/managed-signatures.php';if(is_file($managed)){require_once$managed;$mapping=$db->fetch_array($db->simple_select('dune_accounts','website_id','uid='.(int)$post['uid']));if($mapping&&($html=dune_render_managed_signature($mapping['website_id'],'forum'))){$post['signature']=$html;return$post;}}
 $community='';$helper='/home/dunerealms/apps/integrations/community-seals.php';if(is_file($helper)){require_once$helper;$mapping=$db->fetch_array($db->simple_select('dune_accounts','website_id','uid='.(int)$post['uid']));if($mapping)$community=dune_community_seal_html($mapping['website_id'],71);}
 if(!empty($post['signature'])){$post['signature'].=$community;return$post;}
 $staff=$db->fetch_array($db->simple_select('dune_staff_seal_fragments','fragment','uid='.(int)$post['uid']));
 $office=$db->fetch_array($db->simple_select('dune_political_signatures','fragment','uid='.(int)$post['uid']));
 $bb=trim($staff['fragment']??'');$political=trim($office['fragment']??'');
 if($bb===''&&$political===''&&$community==='')return$post;
 if($bb==='')$bb="With regards,\n\n[b]".str_replace(['[',']'],['&#91;','&#93;'],$post['username'])."[/b]\n";
 $bb.=$political!==''?"\n".$political:'';
 $post['signature']='<div class="signature dune-managed-signature" style="max-width:640px;margin-top:12px;padding-top:10px;border-top:1px solid #617554">'.$parser->parse_message($bb,['allow_html'=>0,'allow_mycode'=>1,'allow_smilies'=>0,'allow_imgcode'=>!empty($mybb->user['showimages'])||empty($mybb->user['uid']),'filter_badwords'=>1,'nofollow_on'=>1]).$community.'</div>';
 return$post;
}
function dune_apply_staff_seal($uid,$websiteId){
 global $db;
 $managed='/home/dunerealms/apps/integrations/managed-signatures.php';if(is_file($managed)){require_once$managed;if(dune_signature_account($websiteId)){$signature=dune_signature_bbcode($websiteId);$current=$db->fetch_array($db->simple_select('users','signature','uid='.(int)$uid));if($current&&$current['signature']!==$signature)$db->update_query('users',['signature'=>$db->escape_string($signature)],'uid='.(int)$uid);return;}}
 $row=$db->fetch_array($db->simple_select('dune_staff_seal_accounts','bbcode',"website_id='".$db->escape_string($websiteId)."'"));
 $old=$db->fetch_array($db->simple_select('dune_staff_seal_fragments','fragment','uid='.(int)$uid));
 $user=$db->fetch_array($db->simple_select('users','signature','uid='.(int)$uid));if(!$user)return;
 $fragment=$row&&!empty($row['bbcode'])?"\n".$row['bbcode']:'';
 $base=!empty($old['fragment'])?str_replace($old['fragment'],'',$user['signature']):$user['signature'];
 $signature=rtrim($base).$fragment;
 if($signature!==$user['signature'])$db->update_query('users',['signature'=>$db->escape_string($signature)],'uid='.(int)$uid);
 if(!$old)$db->insert_query('dune_staff_seal_fragments',['uid'=>(int)$uid,'fragment'=>$db->escape_string($fragment)]);
 elseif($old['fragment']!==$fragment)$db->update_query('dune_staff_seal_fragments',['fragment'=>$db->escape_string($fragment)],'uid='.(int)$uid);
}
function dune_mybb_lock_signature_data($handler){
 global$db;$uid=(int)($handler->uid?:($handler->data['uid']??0));if(!$uid||!array_key_exists('signature',$handler->user_update_data))return;
 if($db->fetch_array($db->simple_select('dune_accounts','website_id','uid='.$uid)))unset($handler->user_update_data['signature']);
}
function dune_mybb_signature_settings(){
 global$mybb,$db,$header,$footer,$headerinclude;
 if(!in_array($mybb->get_input('action'),['editsig','do_editsig'],true))return;
 $mapping=$db->fetch_array($db->simple_select('dune_accounts','website_id','uid='.(int)$mybb->user['uid']));if(!$mapping)return;
 require_once '/home/dunerealms/apps/integrations/managed-signatures.php';$id=$mapping['website_id'];$notice='';
 if($mybb->request_method==='post'){
  verify_post_check($mybb->get_input('my_post_key'));
  if(!array_key_exists('dune_signature_closing',$mybb->input)){http_response_code(403);error('Signature seals are managed automatically. Use the closing line and personal banner settings.');}
  try{dune_save_signature_preferences($id,$mybb->get_input('dune_signature_closing'),$mybb->get_input('dune_signature_banner'));$notice='<p class="success_message">Signature preferences saved.</p>';}catch(Throwable$e){$notice='<p class="error">'.htmlspecialchars($e->getMessage(),ENT_QUOTES).'</p>';}
 }
 $p=dune_signature_preferences($id);
 $form='<div style="max-width:900px;margin:20px auto;padding:20px;border:1px solid #8d8149;border-radius:12px;background:#143127"><h2>Your signature</h2><p>Your identity and medals are managed automatically. Personalise your closing and optionally add your own forum banner.</p>'.$notice.'<form method="post" action="usercp.php"><input type="hidden" name="action" value="do_editsig"><input type="hidden" name="my_post_key" value="'.htmlspecialchars($mybb->post_code,ENT_QUOTES).'"><label for="dune_signature_closing">Closing line</label><textarea id="dune_signature_closing" name="dune_signature_closing" maxlength="160" rows="2" style="display:block;width:100%;margin:8px 0 16px">'.htmlspecialchars($p['closing'],ENT_QUOTES).'</textarea><label for="dune_signature_banner">Personal banner image URL (optional, HTTPS)</label><input id="dune_signature_banner" name="dune_signature_banner" type="url" maxlength="1024" value="'.htmlspecialchars($p['banner_url'],ENT_QUOTES).'" style="display:block;width:100%;margin:8px 0 16px"><button type="submit">Save signature preferences</button></form><h3>Preview</h3>'.dune_render_managed_signature($id,'forum').'</div>';
 output_page('<!DOCTYPE html><html lang="en"><head><title>Your signature · DuneRealms</title>'.$headerinclude.'</head><body>'.$header.$form.$footer.'</body></html>');exit;
}
