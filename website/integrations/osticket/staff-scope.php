<?php
// Fingerprint authoritative access, never a client-supplied native staff ID.
function dune_ticket_staff_scope($identity){
 if(!is_array($identity)||!is_string($identity['id']??null)||!is_array($identity['permissions']??null))return null;
 $nodes=$identity['permissions'];
 if(!in_array('web.tickets.staff',$nodes,true))return null;
 foreach($nodes as$node)if(!is_string($node))return null;
 $ranks=$identity['ranks']??[$identity['role']??''];
 if(!is_array($ranks))return null;
 foreach($ranks as$rank)if(!is_string($rank))return null;
 sort($nodes,SORT_STRING);sort($ranks,SORT_STRING);
 return hash('sha256',json_encode([$identity['id'],array_values(array_unique($nodes)),array_values(array_unique($ranks))],JSON_UNESCAPED_SLASHES|JSON_THROW_ON_ERROR));
}
function dune_ticket_staff_scope_current($identity,$stored){
 $scope=dune_ticket_staff_scope($identity);
 return is_string($scope)&&is_string($stored)&&hash_equals($scope,$stored);
}
