<?php
// Central permission nodes are authoritative; rank names never imply forum powers.
function dune_forum_can_admin($identity){
 return is_array($identity)&&is_array($identity['permissions']??null)&&in_array('web.forum.admin',$identity['permissions'],true);
}
function dune_forum_group_for($identity){
 if(dune_forum_can_admin($identity))return 4;
 $nodes=is_array($identity)&&is_array($identity['permissions']??null)?$identity['permissions']:[];
 return in_array('web.forum.moderate',$nodes,true)?6:2;
}
