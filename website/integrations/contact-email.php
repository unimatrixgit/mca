<?php
// Local pipe only. No HTTP entry point or secrets in argv/stdout.
if(PHP_SAPI!=='cli')exit(1);
try{
 $raw=stream_get_contents(STDIN,4097);if(strlen($raw)>4096)throw new RuntimeException();$a=json_decode($raw,true);
 require_once '/home/dunerealms/apps/integrations/osticket/contact-email-policy.php';
 if(!is_array($a)||!in_array($a['action']??null,['verify','sync'],true)||!preg_match('/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/',$a['userId']??''))throw new RuntimeException();
 $email=dune_contact_address($a['email']??null);
 if($a['action']==='verify'&&($email===null||!preg_match('/^[a-f0-9]{36}$/',$a['code']??'')))throw new RuntimeException();
 if($a['action']==='sync'&&(!is_int($a['version']??null)||$a['version']<0||!preg_match('/^[a-z0-9_]{3,32}$/',$a['username']??'')))throw new RuntimeException();
 require_once '/home/dunerealms/apps/integrations/osticket/main.inc.php';
 if($a['action']==='verify'){
  $existing=User::lookupByEmail($email);$own=User::lookupByEmail($a['userId'].'@accounts.dunerealms.invalid');
  if($existing&&(!$own||$existing->getId()!==$own->getId()))throw new RuntimeException();
  $mailer=$cfg->getDefaultEmail();if(!$mailer||!$mailer->send($email,'Confirm your DuneRealms contact email',"You requested Support ticket notifications at this email address.\n\nPaste this verification code in Account centre > Account security within 15 minutes:\n\n".$a['code']."\n\nhttps://cp.dunerealms.com/#security\n\nIf you did not request this, ignore this message. Your account has not changed.",null,['text'=>true,'autoreply'=>true]))throw new RuntimeException();
 }else{
  $alias=$a['userId'].'@accounts.dunerealms.invalid';$user=User::lookupByEmail($alias)?:User::fromVars(['name'=>$a['username'],'email'=>$alias]);if(!$user)throw new RuntimeException();
  $c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);$db=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
  dune_contact_sync($db,$a['userId'],$email,$a['version']);
 }
 echo json_encode(['ok'=>true]);
}catch(Throwable $e){echo json_encode(['ok'=>false]);exit(1);}
