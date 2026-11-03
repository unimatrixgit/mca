<?php
class Topic {static function lookup($id){return $id===18?new self:null;}function getName(){return 'Alpha Tester Signup';}function getForms(){return [new Definition];}}
class Definition {function getForm($source=false){return new Application;}}
class Application {function getFields(){return [new Field('alpha_minecraft'),new Field('alpha_platform')];}}
class Field {static $epoch='new'; function __construct(public $name){}function get($key){return $this->name;}function getFormName(){return self::$epoch.'_'.$this->name;}function getFormNames(){return [$this->getFormName(),$this->name];}}
class TicketForm {static function objects(){return new self;}function one(){return $this;}function getForm($source=false){return $this;}function getField($name){return new Field($name);}}
require __DIR__.'/alpha-intake.php';
$count=0;function check($value){global $count;if(!$value)throw new Exception('Assertion failed '.($count+1));$count++;}
$_SESSION=['dune_alpha_field_maps'=>[['alpha_minecraft'=>'old_alpha_minecraft','alpha_platform'=>'old_alpha_platform']]];
$_SERVER['REQUEST_METHOD']='POST';$_POST=['topicId'=>18,'old_alpha_minecraft'=>'Test','old_alpha_platform'=>['pc'],'subject'=>'forged','message'=>'forged','other'=>'keep'];
dune_alpha_intake(18);ob_end_clean();
check($_POST['new_subject']==='Alpha tester application');check($_POST['subject']===$_POST['new_subject']);check(str_contains($_POST['new_message'],'structured application'));check($_POST['other']==='keep');check($_POST['new_alpha_minecraft']==='Test');check($_POST['new_alpha_platform']===['pc']);
$before=$_POST;dune_alpha_intake(1);check($_POST===$before);
$_SERVER['REQUEST_METHOD']='GET';ob_start();dune_alpha_intake(18);echo '<html><body></body></html>';ob_end_flush();$html=ob_get_clean();check(str_contains($html,'new_subject'));check(str_contains($html,'row.hidden=alpha'));check(str_contains($html,'MutationObserver'));check(str_contains($html,'field.disabled=alpha'));check(str_contains($html,'header.hidden=alpha'));
echo "$count Alpha intake checks passed\n";
