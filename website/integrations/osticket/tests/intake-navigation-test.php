<?php
class Topic {static $public=[1=>'General support',11=>'Report a Problem / Access Issue',14=>'Parental consent request [Preview]',21=>'Customer sales enquiries'];static function getPublicHelpTopics(){return self::$public;}}
require dirname(__DIR__).'/intake-navigation.php';
$checks=0;
function intake_check($value,$label){global $checks;$checks++;if(!$value)throw new RuntimeException($label);}
foreach(['/open.php','/alpha-signup.php','/open.php?topicId=21','/open.php?topicId=11','/open.php?topicId=14']as$path)intake_check(dune_public_intake_path($path)===$path,'Public intake rejected');
foreach([null,[],false,'https://example.test/open.php','//example.test/open.php','/\\example.test','/../open.php','/%6fpen.php','/open.php#x',"/open.php\r\nLocation: https://example.test",'/open.php?topicId=17','/open.php?topicId=20','/open.php?topicId=0','/open.php?topicId=01','/open.php?topicId=-1','/open.php?topicId=999','/open.php?topicId=21&topicId=14','/open.php?topicId=21&next=https://example.test','/open.php?topicId[]=21','/open.php?topicId=999999999999999999','/scp/tickets.php','/dev-tasks.php','/tickets.php?id=1','/alpha-signup.php?anything=1']as$path)intake_check(dune_public_intake_path($path)===null,'Unsafe or private intake accepted');
intake_check(dune_requested_intake_path('open.php',['topicId'=>'21'],'GET')==='/open.php?topicId=21','Selected public topic lost');
intake_check(dune_requested_intake_path('open.php',[],'HEAD')==='/open.php','Generic intake lost');
intake_check(dune_requested_intake_path('alpha-signup.php',[],'GET')==='/alpha-signup.php','Alpha route lost');
foreach([['open.php',['topicId'=>['21']],'GET'],['open.php',['topicId'=>'17'],'GET'],['open.php',['topicId'=>'21'],'POST'],['tickets.php',[],'GET']]as$args)intake_check(dune_requested_intake_path(...$args)===null,'Invalid request remembered');
$session=['dune_sso_state'=>'existing-nonce','dune_session_ref'=>'existing-reference'];
dune_remember_intake_path($session,['intake'=>'/open.php?topicId=21'],100);
intake_check(dune_take_intake_path($session,101)==='/open.php?topicId=21','Successful sign-in route lost');
intake_check(dune_take_intake_path($session,102)===null,'Return context replayed');
intake_check($session===['dune_sso_state'=>'existing-nonce','dune_session_ref'=>'existing-reference'],'Authentication state changed');
foreach([['staff'=>'1'],['admin'=>'1'],['dev'=>'1'],['appeal'=>'']]as$special){dune_remember_intake_path($session,['intake'=>'/open.php?topicId=21'],100);dune_remember_intake_path($session,$special+['intake'=>'/open.php?topicId=21'],101);intake_check(dune_take_intake_path($session,102)===null,'Specialised flow overridden');}
dune_remember_intake_path($session,['intake'=>'/open.php?topicId=21'],100,'POST');intake_check(dune_take_intake_path($session,101)===null,'POST context remembered');
dune_remember_intake_path($session,['intake'=>'/open.php?topicId=21'],100);intake_check(dune_take_intake_path($session,400)===null,'Expired context accepted');
dune_remember_intake_path($session,['intake'=>'/open.php?topicId=21'],100);intake_check(dune_take_intake_path($session,99)===null,'Future context accepted');
dune_remember_intake_path($session,['intake'=>'/open.php?topicId=21'],100);unset(Topic::$public[21]);intake_check(dune_take_intake_path($session,101)===null,'Now-private topic accepted');
Topic::$public=null;intake_check(dune_public_intake_path('/open.php?topicId=21')===null,'Unavailable catalogue accepted');
echo "$checks public-intake navigation checks passed; nonce, identity, specialised flows and POST contents preserved.\n";
