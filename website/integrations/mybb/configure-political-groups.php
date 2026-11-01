<?php
if(PHP_SAPI!=='cli')exit(1);
$_SERVER['SCRIPT_NAME']='/dune-sso.php';$_SERVER['REQUEST_METHOD']='GET';$_SERVER['REMOTE_ADDR']='127.0.0.1';$_SERVER['HTTP_HOST']='forums.dunerealms.com';
define('IN_MYBB',1);chdir('/home/dunerealms/apps/integrations/mybb');require 'global.php';
$db->write_query('CREATE TABLE IF NOT EXISTS '.TABLE_PREFIX.'dune_political_config (id INT PRIMARY KEY,groups_json TEXT NOT NULL) ENGINE=InnoDB');
$db->write_query('CREATE TABLE IF NOT EXISTS '.TABLE_PREFIX.'dune_political_roles (website_id VARCHAR(36) PRIMARY KEY,roles_json TEXT NOT NULL,expires_at BIGINT NOT NULL) ENGINE=InnoDB');
$db->write_query('CREATE TABLE IF NOT EXISTS '.TABLE_PREFIX.'dune_political_signatures (uid INT PRIMARY KEY,fragment TEXT NOT NULL) ENGINE=InnoDB');
$row=$db->fetch_array($db->simple_select('dune_political_config','groups_json','id=1'));$groups=$row?json_decode($row['groups_json'],true):[];
$base=$db->fetch_array($db->simple_select('usergroups','*','gid=2'));if(!$base)throw new Exception('Registered group missing');unset($base['gid']);
foreach(['emperor'=>'Emperor','house_leader'=>'Faction leader','specerhuis'=>'Specerhuis','welvaart'=>'House of Water','guild_staff'=>'Vaerkring','landsraad'=>'Landsraad']as$key=>$title){
 $gid=(int)($groups[$key]??0);
 if(!$gid||!$db->fetch_field($db->simple_select('usergroups','gid','gid='.$gid),'gid')){
  $data=$base;$data['title']='Imperial office: '.$title;$data['description']='Managed current game office. No forum staff authority.';$data['cancp']=0;$data['canmodcp']=0;$data['issupermod']=0;$data['namestyle']='{username}';$data['usertitle']=$title;$data['stars']=0;$data['starimage']='';$data['image']='https://forums.dunerealms.com/assets/seals/office-'.$key.'.png';$data['disporder']=50;
  $gid=(int)$db->insert_query('usergroups',$data);$groups[$key]=$gid;
 }
}
$data=['groups_json'=>$db->escape_string(json_encode($groups))];if($row)$db->update_query('dune_political_config',$data,'id=1');else$db->insert_query('dune_political_config',['id'=>1]+$data);
$cache->update_usergroups();echo "Six managed player-office groups configured; existing memberships unchanged.\n";
