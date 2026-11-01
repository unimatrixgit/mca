<?php
require $argv[1]??dirname(__DIR__).'/staff-scope.php';
function check($condition){if(!$condition)throw new RuntimeException('Staff scope assertion failed');}
$identity=['id'=>'self','role'=>'web_support','ranks'=>['web_support','web_legal'],'permissions'=>['web.tickets.staff','web.tickets.support','web.tickets.legal']];
$scope=dune_ticket_staff_scope($identity);
check(is_string($scope)&&strlen($scope)===64);
check(dune_ticket_staff_scope_current($identity,$scope));
$reordered=$identity;$reordered['permissions']=array_reverse($reordered['permissions']);$reordered['ranks']=array_reverse($reordered['ranks']);
check(dune_ticket_staff_scope_current($reordered,$scope));
foreach([
 array_replace($identity,['id'=>'other']),
 array_replace($identity,['permissions'=>['web.tickets.staff','web.tickets.support']]),
 array_replace($identity,['permissions'=>['web.tickets.support']]),
 array_replace($identity,['ranks'=>['web_support']]),
 array_replace($identity,['permissions'=>['web.tickets.staff','web.tickets.admin']]),
 null,[],array_replace($identity,['permissions'=>'web.tickets.staff']),array_replace($identity,['ranks'=>'owners'])
]as$changed)check(!dune_ticket_staff_scope_current($changed,$scope));
check(!dune_ticket_staff_scope_current($identity,null));
check(!dune_ticket_staff_scope_current($identity,'forged'));
echo "Staff scope checks PASS\n";
