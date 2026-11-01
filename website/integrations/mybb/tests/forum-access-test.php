<?php
require $argv[1]??__DIR__.'/../forum-access.php';
function check_acl($yes,$message){if(!$yes)throw new Exception($message);}
$tests=0;
for($mask=0;$mask<8;$mask++){
 $nodes=[];foreach(['web.forum.admin','web.forum.moderate','web.ranks.manage']as$bit=>$node)if($mask&(1<<$bit))$nodes[]=$node;
 $identity=['permissions'=>$nodes,'role'=>'owners','ranks'=>['owners']];
 $expected=$mask&1?4:($mask&2?6:2);
 check_acl(dune_forum_group_for($identity)===$expected,'Incorrect group for mask '.$mask);
 check_acl(dune_forum_can_admin($identity)===($expected===4),'Incorrect admin guard for mask '.$mask);
 $tests++;
}
foreach([null,[],['permissions'=>null],['permissions'=>'web.forum.admin'],['role'=>'owners'],['permissions'=>['web.forum.admin.fake']],['permissions'=>['WEB.FORUM.ADMIN']]]as$identity){check_acl(dune_forum_group_for($identity)===2,'Malformed or implicit identity must remain a reader');check_acl(!dune_forum_can_admin($identity),'Malformed or implicit identity cannot administer');$tests++;}
check_acl(dune_forum_group_for(['permissions'=>['web.ranks.manage','web.forum.moderate']])===6,'Rank management cannot turn a moderator into an administrator');$tests++;
echo $tests." native forum permission matrix cases passed.\n";
