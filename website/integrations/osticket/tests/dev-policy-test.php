<?php
if(PHP_SAPI!=='cli')exit(1);
require($argv[1]??dirname(__DIR__).'/dev-policy.php');$n=0;function check($v){global$n;if(!$v)throw new Exception('Assertion '.($n+1).' failed');$n++;}
$member=['id'=>'m','username'=>'m','ranks'=>['user'],'permissions'=>[]];$vip=['id'=>'v','username'=>'v','ranks'=>['vip'],'permissions'=>[]];$staff=['id'=>'s','username'=>'s','ranks'=>['web_news'],'permissions'=>['web.posts.edit']];$dev=['id'=>'d','username'=>'dev','ranks'=>['web_dev'],'permissions'=>['web.tickets.dev']];$owner=['id'=>'o','username'=>'unimatrix999','ranks'=>['owners'],'permissions'=>[]];
check(!dune_dev_submitter($member));check(dune_dev_submitter($vip));check(dune_dev_submitter($staff));check(!dune_dev_team($vip));check(!dune_dev_team($staff));check(dune_dev_team($dev));check(dune_dev_owner($owner));check(!dune_dev_owner(['username'=>'unimatrix999','ranks'=>['vip']]));
foreach(['owners','corestaff','web_network','web_net_admin']as$r)check(dune_dev_team(['ranks'=>[$r]]));
check(dune_dev_transition($owner,'approve','pending','Approved for delivery')==='approved');check(dune_dev_transition($owner,'deny','pending','Not in current scope')==='denied');check(dune_dev_transition($dev,'complete','approved','Tests pass; merge and deployment documented')==='completed');
foreach([[$dev,'approve','pending'],[$dev,'deny','pending'],[$vip,'complete','approved'],[$owner,'complete','pending'],[$owner,'approve','denied'],[$owner,'deny','completed']]as[$u,$a,$s]){try{dune_dev_transition($u,$a,$s,'Valid detailed reason');check(false);}catch(RuntimeException$e){check(true);}}
[$v,$err]=dune_dev_validate([]);check(count($err)>=7);$valid=['title'=>'A task','type'=>'Feature','system'=>'Web','objective'=>'Implement a thing','current'=>'Existing behaviour','expected'=>'Desired behaviour','acceptance'=>'Observable result','priority'=>'Medium','privacy'=>'1'];[$v,$err]=dune_dev_validate($valid);check(!$err);check(str_contains(dune_dev_prompt($v),'Observable result'));$valid['type']='malicious';[, $err]=dune_dev_validate($valid);check(count($err)===1);$valid['type']='Feature';$valid['objective']=str_repeat('x',12001);[, $err]=dune_dev_validate($valid);check(count($err)===1);check(dune_dev_owner(['username'=>'grmacken','ranks'=>['web_net_admin']]));check(!dune_dev_owner(['username'=>'grmacken','ranks'=>['user']]));check(dune_dev_priority_editor($dev));check(!dune_dev_priority_editor($staff));check(str_contains(dune_dev_execution_brief(['objective'=>'Test','acceptance'=>'Pass']),'## Acceptance criteria'));
$valid['objective']='Valid objective';$valid['title']=str_repeat('é',160);[, $err]=dune_dev_validate($valid);check(!$err);
$valid['title']=str_repeat('é',161);[, $err]=dune_dev_validate($valid);check(count($err)===1);
$valid['title']='Valid';$valid['risk']=['array'];[, $err]=dune_dev_validate($valid);check(count($err)===1);
$valid['risk']='';$valid['title']="\xC3\x28";[, $err]=dune_dev_validate($valid);check(count($err)===1);
foreach(['1','344','2147483647']as$id)check(dune_dev_id($id)>0);
foreach(['0','-1','1.2','1e2',' 1','1 OR 1=1',[],null,str_repeat('9',30)]as$id)check(dune_dev_id($id)===false);
check(dune_dev_origin_valid(null));check(dune_dev_origin_valid('https://support.dunerealms.com'));check(!dune_dev_origin_valid('https://evil.invalid'));check(!dune_dev_origin_valid('null'));
check(dune_dev_text_valid(str_repeat('水',10000),10,10000));check(!dune_dev_text_valid(str_repeat('水',10001),10,10000));
check(!dune_dev_text_valid('          ',10,10000));check(!dune_dev_text_valid([],0,10000));
check(dune_dev_transition($owner,'approve','pending',str_repeat('水',10000))==='approved');
$base=['title'=>'Task','type'=>'Feature','system'=>'Web','objective'=>'Outcome','current'=>'Current','expected'=>'Expected','acceptance'=>'Checks','priority'=>'Medium','privacy'=>'1'];
foreach(dune_dev_fields()as$key=>[$label,$required,$max,$type,$hint]){
 $input=$base;$input[$key]=[];[, $err]=dune_dev_validate($input);check((bool)$err);
 if($type==='select')continue;
 $input=$base;$input[$key]=str_repeat('水',$max);[, $err]=dune_dev_validate($input);check(!$err);
 $input[$key].='水';[, $err]=dune_dev_validate($input);check((bool)$err);
 if($required){$input=$base;$input[$key]='   ';[, $err]=dune_dev_validate($input);check((bool)$err);}
}
$input=$base;$input['privacy']='0';[, $err]=dune_dev_validate($input);check((bool)$err);
foreach(['owners','official_account','corestaff','staff','web_network','web_net_owner','web_net_admin','web_dev','web_streamer','web_news','web_forum','web_support','web_sales','web_legal','web_appeal','game_mod','game_admin','game_support','game_appeal','game_quests','game_restoration'] as $rank) check(dune_dev_submitter(['ranks'=>[$rank],'permissions'=>[]]));
check(!dune_dev_submitter(['ranks'=>['crysknife'],'permissions'=>[]]));check(!dune_dev_submitter(['ranks'=>['memorial'],'permissions'=>[]]));
check(dune_dev_submitter(['ranks'=>['council_member'],'permissions'=>[]]));check(!dune_dev_team(['ranks'=>['council_member'],'permissions'=>[]]));check(!dune_dev_owner(['username'=>'council','ranks'=>['council_member']]));
echo "$n policy assertions passed\n";
