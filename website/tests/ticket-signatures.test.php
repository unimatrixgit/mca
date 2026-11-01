<?php
define('ROOT_DIR',__DIR__.'/');require __DIR__.'/../integrations/osticket/ticket-signatures.php';
class Format{static function sanitize($html){return$html;}}
class Body{function __construct(public $html){}function toHtml(){return$this->html;}}
class Agent{function getSignature(){return '<p>With regards,<br>Example</p><img src="https://www.dunerealms.com/assets/seals/owner-example-150.png">';}}
class Entry{function __construct(public $type='R',public $id=1,public $body='Original reply',public $agent=true){}function getStaffId(){return$this->id;}function getStaff(){return$this->agent?new Agent:null;}function getBody(){return new Body($this->body);}}
function check($truth,$name){if(!$truth)throw new Exception($name);echo "PASS $name\n";}
check(str_contains(dune_ticket_entry_signature(new Entry),'Current staff signature'),'staff replies display current signature');
check(dune_ticket_entry_signature(new Entry('M'))==='','customer messages never receive staff seals');
check(dune_ticket_entry_signature(new Entry('R',0))==='','system entries never get a staff identity');
check(dune_ticket_entry_signature(new Entry('R',1,'Original',false))==='','deleted staff are not impersonated');
check(dune_ticket_entry_signature(new Entry('R',1,(new Agent)->getSignature()))==='','already embedded seal is not duplicated');
check(str_contains(dune_ticket_entry_signature(new Entry('R',1,str_replace('Example','Other agent',(new Agent)->getSignature()))),'Current staff signature'),'quoted other agent signature does not hide current author');
check(str_contains(dune_ticket_entry_signature(new Entry('N')),'Current staff signature'),'staff internal notes display signatures');
