<?php
require $argv[1] ?? dirname(__DIR__).'/support-workspace.php';
$checks=0;
function check($v,$message){global $checks;$checks++;if(!$v)throw new RuntimeException($message);}
$form='<form method="post" action="tickets.php?id=7"><input name="__CSRFToken__" value="unchanged"><input type="file" name="attachments[]"><textarea name="message">&lt;private&gt;</textarea><button>Reply</button></form>';
$source='<html><head><title>Native</title></head><body class="native"><div id="container"><ul id="nav"><li><a href="tickets.php">Tickets</a></li></ul><div id="content">'.$form.'</div></div></body></html>';
foreach(['/index.php','/open.php','/tickets.php','/view.php','/login.php','/alpha-signup.php','/appeal.php','/scp/tickets.php','/scp/settings.php','/scp/login.php'] as $path){
 $out=dune_support_workspace($source,$path);
 check(substr_count($out,'<nav class="dune-support-submenu"')===1,'One integrated navigation');
 check(str_contains($out,'https://cp.dunerealms.com/'),'Account return link missing');
 check(str_contains($out,$form),'Native form changed: '.$path);
 check(substr_count($out,'<section class="dune-support-workspace-hero"')===1,'Exactly one hero: '.$path);
 check(str_contains($out,'/dune-support-workspace.css?v=364-4'),'Style missing');
 check(str_contains($out,'native dune-support-workspace'),'Native class lost');
 check(str_contains($out,str_starts_with($path,'/scp/')?'dune-support-staff':'dune-support-client'),'Workspace mismatch');
 check(dune_support_workspace($out,$path)===$out,'Not idempotent');
}
check(dune_support_workspace($source,'/dev-tasks.php')===$source,'Dev desk modified');
check(dune_support_workspace('<div id="content">PJAX fragment</div>','/scp/tickets.php')==='<div id="content">PJAX fragment</div>','PJAX fragment rewritten');
check(dune_support_workspace('{"error":"private"}','/ajax.php')==='{"error":"private"}','JSON changed');
check(str_contains(dune_support_workspace($source,'/appeal.php'),'department-ban-appeals-150.png'),'Appeal artwork missing');
check(str_contains(dune_support_workspace($source,'/alpha-signup.php'),'news-community.webp'),'Alpha artwork missing');
check(str_contains(dune_support_workspace(str_replace(' class="native"','',$source),'/index.php'),'class="dune-support-workspace dune-support-client"'),'Unclassed body failed');
echo "$checks presentation checks passed; forms, CSRF, attachments, native classes, PJAX and JSON preserved.\n";



