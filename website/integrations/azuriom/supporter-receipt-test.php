<?php
require __DIR__.'/DuneSupporterReceipt.php';
use Azuriom\Support\DuneSupporterReceipt as R;
$n=0;function verify329($ok){global$n;if(!$ok)throw new RuntimeException('Receipt assertion '.($n+1));$n++;}
function reject329($call){try{$call();}catch(Throwable$e){verify329(true);return;}verify329(false);}
$a='11111111-1111-4111-8111-111111111111';$secret=str_repeat('s',40);
foreach(['49d2fe76-7977-4d49-bd39-e48fc8c648b8','00000000-0000-0000-0009-01f5413c46e7']as$uuid){
 foreach(array_keys(R::PRICES)as$package){$s=R::sign($a,$package,1,2,$uuid,$secret);$decoded=R::verify($s,$a,1,2,$secret);verify329($decoded['uuid']===$uuid&&$decoded['package']===$package);}
}
$s=R::sign($a,'guild-patron',1,2,'00000000-0000-0000-0009-01f5413c46e7',$secret);
verify329(R::verify([],$a,1,2,$secret)===null);
reject329(fn()=>R::verify($s,$a,2,2,$secret));reject329(fn()=>R::verify($s,$a,1,3,$secret));
reject329(fn()=>R::verify($s,'22222222-2222-4222-8222-222222222222',1,2,$secret));
reject329(fn()=>R::verify($s,$a,1,2,str_repeat('x',40)));
$tamper=$s;$tamper['_dune_supporter']=str_replace('guild-patron','grand-benefactor',$tamper['_dune_supporter']);reject329(fn()=>R::verify($tamper,$a,1,2,$secret));
reject329(fn()=>R::verify(['_dune_supporter'=>[]],$a,1,2,$secret));
reject329(fn()=>R::sign($a,'owners',1,2,$a,$secret));reject329(fn()=>R::sign($a,'guild-patron',0,2,$a,$secret));reject329(fn()=>R::sign($a,'guild-patron',1,2,'invalid',$secret));
reject329(fn()=>R::sign($a,'guild-patron',1,2,$a,'short'));
echo json_encode(['assertions'=>$n,'javaAndBedrockIdentitySnapshots'=>true,'realPayment'=>false]).PHP_EOL;
