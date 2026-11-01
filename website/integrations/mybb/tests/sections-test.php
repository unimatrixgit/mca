<?php
define('IN_MYBB',1);
$plugins=new class{function add_hook(...$args){}};
$permissionMap=[];$locked=[];
function forum_permissions($id){global $permissionMap;return ['canview'=>$permissionMap[$id]??1];}
function forum_password_validated($forum,$silent){global $locked;return !in_array((int)$forum['fid'],$locked,true);}
function get_forum_link($id){return 'forumdisplay.php?fid='.(int)$id;}
require $argv[1]??dirname(__DIR__).'/dune_sections.php';
function check($value,$message){if(!$value)throw new RuntimeException($message);}
$all=[3=>['fid'=>3,'pid'=>0,'parentlist'=>'3','active'=>1,'disporder'=>1,'name'=>'Root'],4=>['fid'=>4,'pid'=>3,'parentlist'=>'3,4','active'=>1,'disporder'=>1,'name'=>'Category'],5=>['fid'=>5,'pid'=>4,'parentlist'=>'3,4,5','active'=>1,'disporder'=>1,'name'=>'Public <board>']];
check(dune_sections_visible($all[5],$all),'Public branch should be visible');
$permissionMap[4]=0;check(!dune_sections_visible($all[5],$all),'Hidden ancestor must hide descendant');check(!dune_sections_children(4,$all),'Hidden ancestor must remove child links');
$permissionMap[4]=1;$locked=[3];check(!dune_sections_visible($all[5],$all),'Locked ancestor must hide descendants');
$locked=[];$all[3]['active']=0;check(!dune_sections_visible($all[5],$all),'Inactive ancestor must hide descendants');
$all[3]['active']=1;check(str_contains(dune_sections_links([$all[5]]),'Public &lt;board&gt;'),'Names must be escaped');
echo "Forum section permission checks passed\n";
