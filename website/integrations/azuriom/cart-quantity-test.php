<?php
// Actual candidate controller + installed Laravel validation; synthetic cart only, no application bootstrap.
namespace Azuriom\Plugin\Shop\Cart {
    class Cart {
        public static $fixture;
        public $items=[];
        public $saved=0;
        public $removed=[];
        public static function fromSession($session){return self::$fixture;}
        public function getById($id){return $this->items[$id]??null;}
        public function save(){$this->saved++;}
        public function remove($buyable){$this->removed[]=$buyable->id;unset($this->items[$buyable->id]);}
    }
    class QaItem {
        public $writes=0;
        public function __construct(public string $id,public int $quantity=5,public int $limit=10,public bool $multiple=true){}
        public function setQuantity($value){$this->writes++;$this->quantity=$value;}
        public function maxQuantity(){return $this->limit;}
        public function hasQuantity(){return $this->multiple;}
        public function buyable(){return $this;}
    }
}
namespace {
if(PHP_SAPI!=='cli')throw new RuntimeException('CLI only');
$root=$argv[1]??'/home/dunerealms/apps/azuriom';$candidate=$argv[2]??null;
if(!$candidate||!is_file($candidate))throw new RuntimeException('Explicit candidate controller required');
require $root.'/vendor/autoload.php';
$container=new Illuminate\Container\Container;Illuminate\Container\Container::setInstance($container);
$loader=new Illuminate\Translation\ArrayLoader;
$loader->addMessages('en','validation',['required'=>'Required.','array'=>'Must be an array.','integer'=>'Must be an integer.','min'=>['numeric'=>'Must be nonnegative.']]);
$translator=new Illuminate\Translation\Translator($loader,'en');
$factory=new Illuminate\Validation\Factory($translator,$container);
$container->instance(Illuminate\Contracts\Validation\Factory::class,$factory);$container->instance('validator',$factory);$container->instance('translator',$translator);
Illuminate\Support\Facades\Facade::setFacadeApplication($container);
require $candidate;
function to_route($route){return $route;}
$checks=0;
function same($a,$b){global$checks;$checks++;if($a!==$b)throw new RuntimeException('Cart quantity assertion '.$checks);}
function fixture(){
    $cart=new Azuriom\Plugin\Shop\Cart\Cart;
    $cart->items=['Package-1'=>new Azuriom\Plugin\Shop\Cart\QaItem('Package-1'),'Package-2'=>new Azuriom\Plugin\Shop\Cart\QaItem('Package-2'),'Fixed-3'=>new Azuriom\Plugin\Shop\Cart\QaItem('Fixed-3',1,10,false)];
    Azuriom\Plugin\Shop\Cart\Cart::$fixture=$cart;return$cart;
}
function submit($payload,$json=false){
    $r=$json?Illuminate\Http\Request::create('/shop/cart','POST',[],[],[],['CONTENT_TYPE'=>'application/json'],json_encode($payload,JSON_PRESERVE_ZERO_FRACTION)):Illuminate\Http\Request::create('/shop/cart','POST',$payload);
    $r->setLaravelSession(new Illuminate\Session\Store('qa',new Illuminate\Session\ArraySessionHandler(1)));
    return(new Azuriom\Plugin\Shop\Controllers\CartController)->update($r);
}
set_error_handler(function($level,$message){throw new ErrorException($message,0,$level);});
$invalid=[
    [[], 'quantities'],[['quantities'=>null],'quantities'],[['quantities'=>'bad'],'quantities'],[['quantities'=>[]],'quantities'],
    [['quantities'=>['Package-1'=>'']],'quantities.Package-1'],[['quantities'=>['Package-1'=>'   ']],'quantities.Package-1'],
    [['quantities'=>['Package-1'=>'2oops']],'quantities.Package-1'],[['quantities'=>['Package-1'=>'2.75']],'quantities.Package-1'],
    [['quantities'=>['Package-1'=>['x']]],'quantities.Package-1'],[['quantities'=>['Package-1'=>true]],'quantities.Package-1'],
    [['quantities'=>['Package-1'=>2.0]],'quantities.Package-1'],[['quantities'=>['Package-1'=>-1]],'quantities.Package-1'],
    [['quantities'=>['Package-1'=>str_repeat('9',100)]],'quantities.Package-1'],[['quantities'=>['Package-1'=>'２']],'quantities.Package-1'],
    [['quantities'=>['Package-1'=>'<img src=x>']],'quantities.Package-1'],[['quantities'=>['Package-1'=>'1e2']],'quantities.Package-1'],
    [['quantities'=>['Package-1'=>11]],'quantities.Package-1'],[['quantities'=>['Missing-8'=>1]],'quantities.Missing-8'],
    [['quantities'=>['Fixed-3'=>2]],'quantities.Fixed-3'],
    [['quantities'=>['Package-1'=>2,'Package-2'=>11]],'quantities.Package-2'],
    [['quantities'=>['Package-1'=>0,'Missing-8'=>1]],'quantities.Missing-8'],
];
foreach($invalid as$index=>[$payload,$field]){
    $cart=fixture();try{submit($payload,true);throw new RuntimeException('Invalid payload accepted at index '.$index);}catch(Illuminate\Validation\ValidationException$e){same(array_key_exists($field,$e->errors()),true);same($e->status,422);}
    same($cart->saved,0);same($cart->removed,[]);same($cart->items['Package-1']->quantity,5);same(array_sum(array_map(fn($i)=>$i->writes,$cart->items)),0);
}
$cart=fixture();same(submit(['quantities'=>['Package-1'=>'2','Package-2'=>10,'Fixed-3'=>'1']]),'shop.cart.index');same($cart->items['Package-1']->quantity,2);same($cart->items['Package-2']->quantity,10);same($cart->items['Fixed-3']->quantity,1);same($cart->saved,1);
submit(['quantities'=>['Package-1'=>'2']]);same($cart->items['Package-1']->quantity,2);same($cart->saved,2);
$cart=fixture();submit(['quantities'=>['Package-1'=>'0','Fixed-3'=>0]]);same(array_keys($cart->items),['Package-2']);same($cart->removed,['Package-1','Fixed-3']);same($cart->saved,1);
$cart=fixture();submit(['quantities'=>['Package-1'=>1,'Package-2'=>0]],true);same($cart->items['Package-1']->quantity,1);same(isset($cart->items['Package-2']),false);
echo json_encode(['passed'=>$checks,'actualCandidateController'=>true,'installedLaravelValidator'=>true,'cart'=>'synthetic','databaseOrProviderCalls'=>0]).PHP_EOL;
}
