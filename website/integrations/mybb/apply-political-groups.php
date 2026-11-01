<?php
if(PHP_SAPI!=='cli')exit(1);
$_SERVER['SCRIPT_NAME']='/dune-sso.php';$_SERVER['REQUEST_METHOD']='GET';$_SERVER['REMOTE_ADDR']='127.0.0.1';$_SERVER['HTTP_HOST']='forums.dunerealms.com';
define('IN_MYBB',1);chdir('/home/dunerealms/apps/integrations/mybb');require 'global.php';require_once './political-groups.php';
$rows=$db->simple_select('dune_accounts','website_id,uid');while($row=$db->fetch_array($rows))dune_apply_political_groups((int)$row['uid'],$row['website_id']);
echo "Player office memberships and managed seal signatures reconciled.\n";
