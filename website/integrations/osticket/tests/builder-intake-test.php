<?php
class Topic{static function lookup($id){return$id===29?new self:null;}function getName(){return'Builder Application';}function getForms(){return[new Definition];}}
class Definition{function getForm($source=false){return new Application;}}
class Application{function getFields(){return[new Field('builder_minecraft'),new Field('builder_focus')];}}
class Field{function __construct(public$name){}function get($key){return$this->name;}function getFormName(){return'new_'.$this->name;}function getFormNames(){return[$this->getFormName(),$this->name];}}
class TicketForm{static function objects(){return new self;}function one(){return$this;}function getForm($source=false){return$this;}function getField($name){return new Field($name);}}
require dirname(__DIR__).'/alpha-intake.php';
$_SESSION=['dune_alpha_field_maps'=>[['builder_minecraft'=>'old_builder_minecraft','builder_focus'=>'old_builder_focus']]];
$_SERVER['REQUEST_METHOD']='POST';$_POST=['old_builder_minecraft'=>'BuilderFixture','old_builder_focus'=>['cities','interiors'],'subject'=>'forged','message'=>'forged','other'=>'keep'];
dune_alpha_intake(29);ob_end_clean();
foreach([$_POST['new_subject']==='Builder application',$_POST['new_builder_minecraft']==='BuilderFixture',$_POST['new_builder_focus']===['cities','interiors'],$_POST['other']==='keep',str_contains($_POST['new_message'],'structured application')]as$ok)if(!$ok)throw new Exception('Builder mapping failed');
$before=$_POST;dune_alpha_intake(1);if($_POST!==$before)throw new Exception('Unrelated topic changed');
echo "Builder intake checks passed\n";
