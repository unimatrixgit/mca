<?php
if (!defined('IN_MYBB')) die('Direct access denied');
require_once MYBB_ROOT.'forum-access.php';
if(is_file(MYBB_ROOT.'forum-awards.php')){require_once MYBB_ROOT.'forum-awards.php';$plugins->add_hook('postbit','dune_forum_post_awards');$plugins->add_hook('postbit_prev','dune_forum_post_awards');$plugins->add_hook('postbit_announcement','dune_forum_post_awards');$plugins->add_hook('member_profile_end','dune_forum_profile_awards');}
if(is_file(MYBB_ROOT.'public-profile.php')){require_once MYBB_ROOT.'public-profile.php';$plugins->add_hook('postbit','dune_forum_author_profile');$plugins->add_hook('postbit_prev','dune_forum_author_profile');$plugins->add_hook('postbit_announcement','dune_forum_author_profile');$plugins->add_hook('member_profile_end','dune_forum_member_public_profile');}
if(is_file(MYBB_ROOT.'staff-seal-signatures.php')){require_once MYBB_ROOT.'staff-seal-signatures.php';$plugins->add_hook('postbit','dune_forum_staff_signature');$plugins->add_hook('postbit_prev','dune_forum_staff_signature');$plugins->add_hook('postbit_announcement','dune_forum_staff_signature');$plugins->add_hook('usercp_start','dune_mybb_signature_settings');$plugins->add_hook('datahandler_user_update','dune_mybb_lock_signature_data');}
$plugins->add_hook('global_start','dune_accounts_guard');
$plugins->add_hook('global_start','dune_forum_age_guard',1);
$plugins->add_hook('pre_output_page','dune_accounts_theme');
$plugins->add_hook('admin_load','dune_accounts_admin');
$plugins->add_hook('admin_login','dune_accounts_admin_login');
$plugins->add_hook('admin_page_show_login_start','dune_accounts_admin_login');
$plugins->add_hook('admin_page_output_header','dune_accounts_admin_theme');
$plugins->add_hook('member_login','dune_accounts_login');
$plugins->add_hook('member_do_login_start','dune_accounts_login');
$plugins->add_hook('member_register_start','dune_accounts_login');
$plugins->add_hook('member_do_register_start','dune_accounts_login');
function dune_accounts_info(){return ['name'=>'DuneRealms accounts','description'=>'Central CP sign-in; no password copying.','website'=>'https://www.dunerealms.com','author'=>'DuneRealms','version'=>'1.0.0','compatibility'=>'18*'];}
function dune_accounts_login(){header('Location: /dune-sso.php');exit;}
function dune_accounts_theme($html){require_once '/home/dunerealms/apps/integrations/shared-header.php';return dune_shared_header(str_replace('</head>','<link rel="stylesheet" href="/dune-theme.css"><style>.signature img[src*="/assets/seals/"]{width:71px!important;height:71px!important;object-fit:contain;margin:6px!important;vertical-align:middle}</style></head>',$html));}
function dune_accounts_admin_login(){header('Location: /dune-sso.php?admin=1');exit;}
function dune_accounts_admin(){
 require_once '/home/dunerealms/apps/integrations/shared-header.php';ob_start('dune_shared_header');
 if(session_status()!==PHP_SESSION_ACTIVE)session_start(['cookie_secure'=>true,'cookie_httponly'=>true,'cookie_samesite'=>'Lax']);
 require_once '/home/dunerealms/apps/integrations/bridge.php';
 $identity=dune_bridge('identity',['sessionRef'=>$_SESSION['dune_session_ref']??'']);
 global $mybb,$db;
 $mapping=$db->fetch_array($db->simple_select('dune_accounts','website_id','uid='.(int)$mybb->user['uid']));
 if(!$identity||!$mapping||$mapping['website_id']!==$identity['id']||!dune_forum_can_admin($identity)){http_response_code(403);exit('Forum administration permission required.');}
}
function dune_accounts_guard(){
 global $mybb,$db;
 if (session_status()!==PHP_SESSION_ACTIVE) session_start(['cookie_secure'=>true,'cookie_httponly'=>true,'cookie_samesite'=>'Lax']);
 if (basename($_SERVER['SCRIPT_NAME']??'')==='dune-sso.php') return;
 if (empty($mybb->user['uid'])) return;
 require_once '/home/dunerealms/apps/integrations/bridge.php';
 $identity=dune_bridge('identity',['sessionRef'=>$_SESSION['dune_session_ref']??'']);
 $mapping=$db->fetch_array($db->simple_select('dune_accounts','website_id','uid='.(int)$mybb->user['uid']));
 if (!$identity || !$mapping || $mapping['website_id']!==$identity['id']) {my_unsetcookie('mybbuser');header('Location: /dune-sso.php');exit;}
 require_once MYBB_ROOT.'political-groups.php';dune_apply_political_groups((int)$mybb->user['uid'],$identity['id']);
 require_once MYBB_ROOT.'staff-seal-signatures.php';dune_apply_staff_seal((int)$mybb->user['uid'],$identity['id']);
 $group=dune_forum_group_for($identity);
 if ((int)$mybb->user['usergroup']!==$group){$db->update_query('users',['usergroup'=>$group],'uid='.(int)$mybb->user['uid']);header('Location: /');exit;}
 if (!$identity['linked']&&empty($identity['gameLinkExempt']) && $_SERVER['REQUEST_METHOD']==='POST' && in_array(basename($_SERVER['SCRIPT_NAME']),['newthread.php','newreply.php','editpost.php'],true)) {header('Location: https://cp.dunerealms.com/account-required.html?context=forum');exit;}
}

function dune_accounts_admin_theme($args){$args['this']->extra_header.='<link rel="stylesheet" href="/dune-theme.css">';}

function dune_forum_age_guard(){
 if(basename($_SERVER['SCRIPT_NAME']??'')==='dune-sso.php')return;
 if(session_status()!==PHP_SESSION_ACTIVE)session_start(['cookie_secure'=>true,'cookie_httponly'=>true,'cookie_samesite'=>'Lax']);
 require_once '/home/dunerealms/apps/integrations/bridge.php';
 $identity=dune_bridge('identity',['sessionRef'=>$_SESSION['dune_session_ref']??'']);
 if(!$identity){header('Location: /dune-sso.php');exit;}
 if(($identity['forumAllowed']??true)===false){header('Location: https://cp.dunerealms.com/error.html?reason=age-restricted');exit;}
}
