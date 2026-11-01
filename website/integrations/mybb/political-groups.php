<?php
// Included by the central account guard after identity verification.
function dune_apply_political_groups($uid,$websiteId){
 global $db,$mybb;
 if(PHP_SAPI!=='cli'){
  $socket=@stream_socket_client('unix:///home/dunerealms/apps/integrations/politics/office-sync.sock',$errno,$error,1);
  if($socket){stream_set_timeout($socket,1);fwrite($socket,$websiteId."\n");$status=fgets($socket,16);fclose($socket);}
 }
 $registry=$db->fetch_array($db->simple_select('dune_political_config','groups_json','id=1'));
 if(!$registry)return;
 $groups=json_decode($registry['groups_json'],true);if(!is_array($groups))return;
 $row=$db->fetch_array($db->simple_select('dune_political_roles','roles_json,expires_at',"website_id='".$db->escape_string($websiteId)."'"));
 $roles=$row&&(int)$row['expires_at']>time()?json_decode($row['roles_json'],true):[];
 $roles=is_array($roles)?$roles:[];
 $user=$db->fetch_array($db->simple_select('users','additionalgroups,displaygroup,signature','uid='.(int)$uid));
 if(!$user)return;
 $managed=array_map('intval',array_values($groups));
 $wanted=array_values(array_diff(array_filter(array_map('intval',explode(',',$user['additionalgroups']))),$managed));
 foreach($roles as $role)if(isset($groups[$role]))$wanted[]=(int)$groups[$role];
 $wanted=array_unique($wanted);sort($wanted,SORT_NUMERIC);$value=implode(',',$wanted);
 $update=[];if($value!==$user['additionalgroups'])$update['additionalgroups']=$value;
 $previous=$db->fetch_array($db->simple_select('dune_political_signatures','fragment','uid='.(int)$uid));
 $signature=!empty($previous['fragment'])?str_replace($previous['fragment'],'',$user['signature']):$user['signature'];
 $badges=[];foreach($roles as$role)if(isset($groups[$role]))$badges[]='[img=71x71]https://forums.dunerealms.com/assets/seals/office-'.$role.'.png[/img]';
 $fragment=$badges?"\n".implode(' ',$badges):'';$signature=rtrim($signature).$fragment;
 if(!$previous)$db->insert_query('dune_political_signatures',['uid'=>(int)$uid,'fragment'=>$db->escape_string($fragment)]);
 elseif($previous['fragment']!==$fragment)$db->update_query('dune_political_signatures',['fragment'=>$db->escape_string($fragment)],'uid='.(int)$uid);
 if($signature!==$user['signature'])$update['signature']=$db->escape_string($signature);
 if(in_array((int)$user['displaygroup'],$managed,true)&&!in_array((int)$user['displaygroup'],$wanted,true))$update['displaygroup']=0;
 if($update)$db->update_query('users',$update,'uid='.(int)$uid);
}
