<?php
// Actual installed controller method, synthetic model side effects only; no Laravel bootstrap/provider/network.
namespace Azuriom\Http\Controllers {class Controller{}}
namespace Azuriom\Models {class User {function __construct(public int $id){}function is($other){return$other instanceof self&&$other->id===$this->id;}}}
namespace Azuriom\Plugin\Shop\Models {class Subscription {
    public int $cancelCalls=0;function __construct(public $user){}function cancel(){++$this->cancelCalls;}
}}
namespace {
class SubscriptionDenied extends \RuntimeException {function __construct(public int $status){parent::__construct('Denied');}}
function abort_if($condition,$status){if($condition)throw new SubscriptionDenied($status);}
function shop_user(){global$current;return$current;}
function redirect(){return new class {function route($name){return$name;}};}
require $argv[1];
use Azuriom\Models\User;use Azuriom\Plugin\Shop\Models\Subscription;use Azuriom\Plugin\Shop\Controllers\SubscriptionController;
$checks=0;function same($actual,$expected){global$checks;$checks++;if($actual!==$expected)throw new \RuntimeException('Subscription owner assertion '.$checks);}
$controller=new SubscriptionController;
foreach([null,new User(2)]as$current){$subscription=new Subscription(new User(1));foreach([1,2]as$attempt){try{$controller->cancel($subscription);throw new \RuntimeException('Wrong owner permitted');}catch(SubscriptionDenied$e){same($e->status,403);same($subscription->cancelCalls,0);}}}
$current=new User(1);$subscription=new Subscription(new User(1));same($controller->cancel($subscription),'shop.profile');same($subscription->cancelCalls,1);
echo json_encode(['passed'=>$checks,'actualControllerOwnerBoundary'=>true,'modelProviderCallsAreSynthetic'=>true,'realCancellationOrDatabaseCalls'=>0,'providerReplayNotClaimed'=>true]).PHP_EOL;
}
