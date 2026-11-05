<?php
namespace Azuriom\Plugin\Shop\Payment { class PaymentMethod { protected $gateway; protected function createPayment($c,$a,$u){return new \Azuriom\Plugin\Shop\Models\Payment;} protected function getUserEmail($u){return 'fixture@example.invalid';} } }
namespace Azuriom\Models { class User {public $id=11;} }
namespace Azuriom\Plugin\Shop\Models { class Package {public $dune_website_rank='vip';public $name='VIP';public $short_description='Cosmetic';public $price=50;public $category=1;public $id=47;function getDescription(){return 'Cosmetic access';}function subscriptionPeriodUnit(){return 'months';}function subscriptionPeriodCount(){return 1;}} class Payment {public $id=9;public $user;function __construct(){$this->user=new \Azuriom\Models\User;}function giftcards(){return new class {function sum($x){return 0;}};}} class Subscription{} }
namespace Azuriom\Plugin\Shop\Cart { class Cart {public $package;function __construct($p){$this->package=$p;}function itemsPrice(){return new \FixtureMap([['unit_price'=>50,'item'=>new \FixtureItem($this->package)]]);}} }
namespace Illuminate\Support {class Arr {static function whereNotNull($v){return array_filter($v,fn($x)=>$x!==null);}}}
namespace Illuminate\Validation {class ValidationException extends \Exception {static function withMessages($m){return new self('Unclassified');}}}
namespace Stripe\Checkout {class Session {static $payload;static function create($p){self::$payload=$p;return (object)['url'=>'https://checkout.stripe.test/fixture'];}}}
namespace {
class FixtureMap{function __construct(public $items){}function map($f){return new self(array_map($f,$this->items));}function all(){return $this->items;}}
class FixtureItem{public $quantity=2;function __construct(public $p){}function buyable(){return $this->p;}function name(){return 'VIP';}}
function route($n,$p=null){return 'https://example.invalid/'.$n.'/%id%';}function currency(){return 'EUR';}function redirect(){return new class {function away($u){return $u;}};}
require $argv[1];
class FixtureStripe extends \Azuriom\Plugin\Shop\Payment\Method\StripeMethod{protected function setup():void{}}
$n=0;function ck($v,$m){global $n;$n++;if(!$v)throw new Exception($m);}
$p=new \Azuriom\Plugin\Shop\Models\Package;$s=new FixtureStripe;
$s->startPayment(new \Azuriom\Plugin\Shop\Cart\Cart($p),100,'EUR');$a=\Stripe\Checkout\Session::$payload;
ck($a['managed_payments']['enabled']===true,'Managed stays enabled');ck(!isset($a['invoice_creation']),'No unsupported invoice');ck($a['line_items'][0]['price_data']['product_data']['tax_code']==='txcd_10000000','Tax classification');ck($a['line_items'][0]['price_data']['tax_behavior']==='inclusive','Displayed price includes tax');ck($a['line_items'][0]['price_data']['unit_amount']===5000,'Cents unchanged');ck($a['line_items'][0]['quantity']===2,'Quantity unchanged');ck($a['client_reference_id']===9,'Payment reference unchanged');
$s->startSubscription(new \Azuriom\Models\User,$p);$a=\Stripe\Checkout\Session::$payload;ck($a['mode']==='subscription','Subscription preserved');ck($a['managed_payments']['enabled']===true,'Subscription managed');ck($a['line_items'][0]['price_data']['tax_behavior']==='inclusive','Recurring inclusive');ck($a['line_items'][0]['price_data']['product_data']['tax_code']==='txcd_10000000','Recurring classification');ck($a['line_items'][0]['price_data']['recurring']===['interval'=>'month','interval_count'=>1],'Interval preserved');ck($a['metadata']===['user'=>11,'package'=>47],'Ownership preserved');
foreach(['vip','patron_caravan','patron_guild','patron_grand','council_member'] as $r){$p->dune_website_rank=$r;ck($s::managedTaxCode($p)==='txcd_10000000','Reviewed rank');}
foreach(['','owners','hosting'] as $r){$p->dune_website_rank=$r;try{$s::managedTaxCode($p);throw new Exception('Accepted unknown');}catch(\Illuminate\Validation\ValidationException $e){ck(true,'Rejected unclassified');}}
echo "$n payload checks passed; no database or provider requests.\n";
}

