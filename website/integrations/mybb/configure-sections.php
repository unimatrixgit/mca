<?php
// Private CLI installer. The live forum hierarchy and content are managed in MyBB.
if(PHP_SAPI!=='cli')exit(1);
$_SERVER['SCRIPT_NAME']='/dune-sso.php';$_SERVER['REQUEST_METHOD']='GET';$_SERVER['REMOTE_ADDR']='127.0.0.1';$_SERVER['HTTP_HOST']='forums.dunerealms.com';
define('IN_MYBB',1);chdir('/home/dunerealms/apps/integrations/mybb');require 'global.php';
if(!$db->num_rows($db->simple_select('settings','sid',"name='dune_sections_enabled'")))$db->insert_query('settings',['name'=>'dune_sections_enabled','title'=>'DuneRealms category panels','description'=>'Display category and subcategory panels using the existing forum tree.','optionscode'=>'yesno','value'=>'1','disporder'=>99,'gid'=>0]);
$pluginsActive=$cache->read('plugins')?:[];$pluginsActive['active']['dune_sections']='dune_sections';$cache->update('plugins',$pluginsActive);
$cache->update_forums();$cache->update_forumpermissions();$cache->update_forumsdisplay();rebuild_settings();
echo "Native category panels enabled; no forums or posts created or moved.\n";