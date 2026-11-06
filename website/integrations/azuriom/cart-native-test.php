<?php
// Actual installed Cart/CartItem + candidate controller, in-memory session and synthetic Buyable.
if(PHP_SAPI!=='cli')throw new RuntimeException('CLI only');
$root=$argv[1]??'/home/dunerealms/apps/azuriom';$candidate=$argv[2]??null;
if(!$candidate||!is_file($candidate))throw new RuntimeException('Explicit candidate required');
$autoload=require $root.'/vendor/autoload.php';
$autoload->addPsr4('Azuriom\\Plugin\\Shop\\',$root.'/plugins/shop/src');
$container=new Illuminate\Container\Container;Illuminate\Container\Container::setInstance($container);
$translator=new Illuminate\Translation\Translator(new Illuminate\Translation\ArrayLoader,'en');
$factory=new Illuminate\Validation\Factory($translator,$container);
$container->instance(Illuminate\Contracts\Validation\Factory::class,$factory);$container->instance('validator',$factory);$container->instance('translator',$translator);
Illuminate\Support\Facades\Facade::setFacadeApplication($container);
require $candidate;
function to_route($route){return $route;}
class Cart342Buyable implements Azuriom\Plugin\Shop\Models\Concerns\Buyable {
    public function __construct(public int $id,private bool $multiple=true){}
    public function getId():int{return$this->id;}
    public function getName():string{return'Synthetic cart item';}
    public function getPrice():float{return 10.0;}
    public function getDescription():string{return'QA only';}
    public function hasQuantity():bool{return$this->multiple;}
    public function getMaxQuantity():int{return$this->multiple?10:1;}
    public function deliver(Azuriom\Plugin\Shop\Models\PaymentItem$item):void{throw new RuntimeException('Delivery forbidden');}
    public static function enabled(){return new class {public function findMany($ids){return collect([new Cart342Buyable(1),new Cart342Buyable(2,false)])->whereIn('id',$ids);}};}
}
$checks=0;function same($a,$b){global$checks;$checks++;if($a!==$b)throw new RuntimeException('Native cart assertion '.$checks);}
function session342(){
    $session=new Illuminate\Session\Store('qa',new Illuminate\Session\ArraySessionHandler(1));
    $cart=Azuriom\Plugin\Shop\Cart\Cart::createEmpty();$cart->set(new Cart342Buyable(1),5);$cart->set(new Cart342Buyable(2,false),1);
    $session->put('shop.cart',$cart->toArray());return$session;
}
function update342($session,$quantities){$r=Illuminate\Http\Request::create('/shop/cart','POST',['quantities'=>$quantities]);$r->setLaravelSession($session);return(new Azuriom\Plugin\Shop\Controllers\CartController)->update($r);}
$session=session342();$before=$session->get('shop.cart');
foreach([['Cart342Buyable-1'=>'2oops'],['Cart342Buyable-1'=>2,'Cart342Buyable-2'=>2],['Cart342Buyable-1'=>0,'Missing-9'=>1],['Cart342Buyable-1'=>11]]as$payload){
    try{update342($session,$payload);throw new RuntimeException('Invalid native cart update accepted');}catch(Illuminate\Validation\ValidationException$e){same($e->status,422);same($session->get('shop.cart'),$before);}
}
same(update342($session,['Cart342Buyable-1'=>10,'Cart342Buyable-2'=>1]),'shop.cart.index');
$cart=Azuriom\Plugin\Shop\Cart\Cart::fromSession($session);same($cart->getById('Cart342Buyable-1')->quantity,10);same($cart->getById('Cart342Buyable-2')->quantity,1);same($cart->total(),110.0);
$price=$cart->getById('Cart342Buyable-1')->originalPrice();
update342($session,['Cart342Buyable-1'=>10]);same(Azuriom\Plugin\Shop\Cart\Cart::fromSession($session)->count(),11);
update342($session,['Cart342Buyable-2'=>0]);$cart=Azuriom\Plugin\Shop\Cart\Cart::fromSession($session);same($cart->getById('Cart342Buyable-2'),null);same($cart->getById('Cart342Buyable-1')->originalPrice(),$price);
update342($session,['Cart342Buyable-1'=>'0']);same(Azuriom\Plugin\Shop\Cart\Cart::fromSession($session)->isEmpty(),true);
// Native single-quantity semantics would coerce setQuantity(0) to1: explicit remove is required.
$fixed=Azuriom\Plugin\Shop\Cart\Cart::createEmpty();$item=$fixed->set(new Cart342Buyable(2,false),1);$item->setQuantity(0);same($item->quantity,1);$fixed->remove($item->buyable());same($fixed->isEmpty(),true);
$session=session342();$request=Illuminate\Http\Request::create('/shop/cart/clear','POST');$request->setLaravelSession($session);
same((new Azuriom\Plugin\Shop\Controllers\CartController)->clear($request),'shop.cart.index');same(Azuriom\Plugin\Shop\Cart\Cart::fromSession($session)->isEmpty(),true);
same((new Azuriom\Plugin\Shop\Controllers\CartController)->clear($request),'shop.cart.index');same(Azuriom\Plugin\Shop\Cart\Cart::fromSession($session)->isEmpty(),true);
echo json_encode(['passed'=>$checks,'actualInstalledCartAndItem'=>true,'actualCandidateController'=>true,'session'=>'in-memory','buyable'=>'synthetic','databaseOrProviderCalls'=>0]).PHP_EOL;
