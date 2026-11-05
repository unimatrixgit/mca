<?php
// Exercise the actual CLI helper with a transport double, never SMTP or live data.
$source=file_get_contents($argv[1]);$policy=$argv[2];
$stub=<<<'PHP'
class User {
 private $id;function __construct($id){$this->id=$id;}function getId(){return $this->id;}
 static function lookupByEmail($email){return str_ends_with($email,'.invalid')?new self(1):(getenv('CONTACT_TEST_CASE')==='collision'?new self(2):null);}
}
class Email {static function lookup($query){return new self;}}
class Sender {
 function send($to,$subject,$message,$files,$options){
  if($to!=='operator@dunerealms.com'||!str_contains($message,str_repeat('a',36))||empty($options['autoreply']))throw new RuntimeException('Missing delivery metadata');
  return getenv('CONTACT_TEST_CASE')!=='transport-failure';
 }
}
class Config {function getDefaultEmail(){return new Sender;}}
$cfg=new Config;
PHP;
$source=str_replace("require_once '/home/dunerealms/apps/integrations/osticket/contact-email-policy.php';",'require_once '.var_export($policy,true).';',$source);
$source=str_replace("require_once '/home/dunerealms/apps/integrations/osticket/main.inc.php';",$stub,$source);
$file=tempnam(sys_get_temp_dir(),'contact-transport-');file_put_contents($file,$source);$count=0;
try{
 $base=['action'=>'verify','userId'=>'11111111-1111-4111-8111-111111111111','email'=>'operator@dunerealms.com','code'=>str_repeat('a',36)];
 foreach([['operator',[],true],['collision',[],false],['transport-failure',[],false],['header',['email'=>"a\r\nBcc:b@example.org"],false],['bad-code',['code'=>'invalid'],false]]as[$mode,$changes,$expected]){
  $pipes=[];$proc=proc_open([PHP_BINARY,$file],[['pipe','r'],['pipe','w'],['pipe','w']],$pipes,null,['CONTACT_TEST_CASE'=>$mode]);fwrite($pipes[0],json_encode(array_merge($base,$changes)));fclose($pipes[0]);$out=stream_get_contents($pipes[1]);fclose($pipes[1]);$errors=stream_get_contents($pipes[2]);fclose($pipes[2]);$exit=proc_close($proc);$result=json_decode($out,true);
  if($errors||($result['ok']??null)!==$expected||($exit===0)!==$expected||str_contains($out,$base['code']))throw new RuntimeException('Transport regression: '.$mode);$count++;
 }
 echo "$count actual-helper transport/protocol checks passed; no email sent.\n";
}finally{unlink($file);}
