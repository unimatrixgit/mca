<?php
// Installed Laravel + actual candidate request rules; synthetic presence verifier, no application bootstrap.
if(PHP_SAPI!=='cli')throw new RuntimeException('CLI only');
$root=$argv[1]??'/home/dunerealms/apps/azuriom';$candidate=$argv[2]??null;
if(!$candidate||!is_file($candidate))throw new RuntimeException('Explicit candidate request required');
require $root.'/vendor/autoload.php';require$candidate;
$factory=new Illuminate\Validation\Factory(new Illuminate\Translation\Translator(new Illuminate\Translation\ArrayLoader,'en'));
$factory->setPresenceVerifier(new class implements Illuminate\Validation\PresenceVerifierInterface {
    public function getCount($table,$column,$value,$excludeId=null,$idColumn=null,$extra=[]){
        if($column!=='id'||!in_array($table,['users','shop_packages'],true))throw new RuntimeException('Unexpected presence lookup');
        return in_array((int)$value,$table==='users'?[1,2]:[10,20],true)?1:0;
    }
    public function getMultiCount($table,$column,array$values,array$extra=[]){throw new RuntimeException('Array IDs must fail before a presence lookup');}
});
$rules=(new Azuriom\Plugin\Shop\Requests\PaymentRequest)->rules();$checks=0;
function same($a,$b){global$checks;$checks++;if($a!==$b)throw new RuntimeException('Manual payment validation assertion '.$checks);}
$base=['user_id'=>1,'transaction_id'=>'SYNTHETIC-QA','price'=>'10.00','packages'=>[['id'=>10,'quantity'=>1]]];
$invalid=[
    [['user_id'=>null],'user_id'],[['user_id'=>[]],'user_id'],[['user_id'=>true],'user_id'],[['user_id'=>1.0],'user_id'],[['user_id'=>9],'user_id'],
    [['transaction_id'=>'   '],'transaction_id'],[['transaction_id'=>['x']],'transaction_id'],[['transaction_id'=>str_repeat('é',101)],'transaction_id'],
    [['price'=>[]],'price'],[['price'=>'1000000000000000000'],'price'],[['price'=>'1000000.00'],'price'],[['price'=>'-0.01'],'price'],[['price'=>'0.001'],'price'],[['price'=>'1e2'],'price'],[['price'=>'<img>'],'price'],
    [['packages'=>'bad'],'packages'],[['packages'=>[]],'packages'],[['packages'=>['bad']],'packages.0'],
    [['packages'=>[['quantity'=>1]]],'packages.0.id'],[['packages'=>[['id'=>9]]],'packages.0.id'],[['packages'=>[['id'=>[10]]]],'packages.0.id'],
    [['packages'=>[['id'=>true]]],'packages.0.id'],[['packages'=>[['id'=>10.0]]],'packages.0.id'],[['packages'=>[['id'=>10],['id'=>'10']]],'packages.0.id'],
    [['packages'=>[['id'=>10,'extra'=>'unexpected']]],'packages.0'],
    [['packages'=>[['id'=>10,'quantity'=>-5]]],'packages.0.quantity'],[['packages'=>[['id'=>10,'quantity'=>0]]],'packages.0.quantity'],
    [['packages'=>[['id'=>10,'quantity'=>['x']]]],'packages.0.quantity'],[['packages'=>[['id'=>10,'quantity'=>true]]],'packages.0.quantity'],
    [['packages'=>[['id'=>10,'quantity'=>1.0]]],'packages.0.quantity'],[['packages'=>[['id'=>10,'quantity'=>'1x']]],'packages.0.quantity'],
    [['packages'=>[['id'=>10,'quantity'=>'１']]],'packages.0.quantity'],[['packages'=>[['id'=>10,'quantity'=>'1.5']]],'packages.0.quantity'],
    [['packages'=>[['id'=>10,'quantity'=>'4294967296']]],'packages.0.quantity'],[['packages'=>[['id'=>10,'quantity'=>str_repeat('9',100)]]],'packages.0.quantity'],
];
foreach($invalid as$index=>[$changes,$field]){$v=$factory->make(array_replace($base,$changes),$rules);same($v->passes(),false);same($v->errors()->has($field),true);}
$valid=[
    $base,array_replace($base,['user_id'=>'2','price'=>'0','packages'=>[['id'=>10],['id'=>'20','quantity'=>'3']]]),
    array_replace($base,['transaction_id'=>str_repeat('é',100),'price'=>'999999.00','packages'=>[['id'=>10,'quantity'=>null]]]),
    array_replace($base,['price'=>'0.01','packages'=>[['id'=>10,'quantity'=>'4294967295']]]),
];
foreach($valid as$payload){$v=$factory->make($payload,$rules);same($v->passes(),true);same($v->validated()['packages'],$payload['packages']);}
echo json_encode(['passed'=>$checks,'actualCandidateRequestRules'=>true,'installedLaravelValidator'=>true,'presence'=>'synthetic users/packages only','databaseOrProviderCalls'=>0,'receiptCreated'=>false,'duplicateReceiptProcessingProved'=>false]).PHP_EOL;
