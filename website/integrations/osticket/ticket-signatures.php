<?php
if(!defined('ROOT_DIR'))exit;
/** Presentation only: never rewrites historic ticket messages or invents customer authorship. */
function dune_ticket_entry_signature($entry){
 $community='';$helper='/home/dunerealms/apps/integrations/community-seals.php';if(is_file($helper))require_once$helper;$managed='/home/dunerealms/apps/integrations/managed-signatures.php';if(is_file($managed))require_once$managed;
 if($entry->type==='M'){
  $user=method_exists($entry,'getUser')?$entry->getUser():null;
  if($user&&function_exists('dune_native_central_id')){$id=dune_native_central_id($user->getAccount(),'dunerealms-client');if($id)$community=dune_community_seal_html($id);}
  return$community;
 }
 if(!in_array($entry->type,['R','N'],true)||!$entry->getStaffId()||!($staff=$entry->getStaff()))return '';
 if(function_exists('dune_native_central_id')){$id=dune_native_central_id($staff,'dunerealms-staff');if($id)$community=dune_community_seal_html($id);}
 $signature=trim($staff->getSignature());if(!empty($id)&&function_exists('dune_render_managed_signature'))$signature=dune_render_managed_signature($id,'ticket');if($signature==='')return$community;
 if(str_contains($signature,'dune-managed-signature-panel'))$community='';
 $body=$entry->getBody()->toHtml();
 if(str_contains($body,'data-dune-signature="staff-'.$entry->getStaffId().'"'))return '';
 preg_match_all('~(?:https://(?:www|support)\.dunerealms\.com)?/assets/seals/[^"\s<>]+~',$signature,$matches);
 $images=array_unique($matches[0]);
 $text=trim(html_entity_decode(strip_tags($signature),ENT_QUOTES));
 if($images&&!array_filter($images,fn($path)=>!str_contains($body,$path))&&($text===''||str_contains(html_entity_decode(strip_tags($body),ENT_QUOTES),$text)))return '';
 if(!$images&&$text!==''&&str_contains(html_entity_decode(strip_tags($body),ENT_QUOTES),$text))return '';
 return '<div class="dune-ticket-signature" data-dune-signature="staff-'.(int)$entry->getStaffId().'" style="margin-top:16px;padding-top:12px;border-top:1px solid #617554">'.Format::sanitize($signature).'<div style="font-size:11px;color:#aebcad;margin-top:5px">Current staff signature</div></div>'.$community;
}
