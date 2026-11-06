<?php
// Render actual candidate Blades using installed Laravel and native includes; isolated layout/helpers only.
if(PHP_SAPI!=='cli')throw new RuntimeException('CLI only');
function route($name,$parameters=[],$absolute=true){return'/fixture/'.rawurlencode($name);}
function old($key=null,$default=null){return Illuminate\Support\Arr::get($GLOBALS['fixtureOld']??[],$key,$default);}
function csrf_field(){return new Illuminate\Support\HtmlString('<input type="hidden" name="_token" value="SYNTHETIC-QA-ONLY">');}
function use_site_money(){return false;}
function shop_format_amount($value){return number_format($value,2).' EUR';}
function shop_active_currency(){return'EUR';}
$root=$argv[1]??'/home/dunerealms/apps/azuriom';$cartBlade=$argv[2]??null;$manualBlade=$argv[3]??null;
if(!$cartBlade||!$manualBlade)throw new RuntimeException('Both candidate Blade paths required');
require$root.'/vendor/autoload.php';
$qa=sys_get_temp_dir().'/dune-payments342-view-'.bin2hex(random_bytes(8));mkdir($qa,0700);mkdir($qa.'/layouts',0700);mkdir($qa.'/admin',0700);mkdir($qa.'/admin/layouts',0700);mkdir($qa.'/cache',0700);
copy($cartBlade,$qa.'/cart.blade.php');copy($manualBlade,$qa.'/manual.blade.php');
$layout='<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>@yield("title")</title><!--NATIVE_STYLES--></head><body><main class="container py-4">@yield("content")</main>@stack("footer-scripts")</body></html>';
file_put_contents($qa.'/layouts/app.blade.php',$layout);file_put_contents($qa.'/admin/layouts/admin.blade.php',$layout);
$files=new Illuminate\Filesystem\Filesystem;$container=new Illuminate\Container\Container;Illuminate\Container\Container::setInstance($container);
$loader=new Illuminate\Translation\FileLoader($files,$root.'/resources/lang');$loader->addNamespace('shop',$root.'/plugins/shop/resources/lang');
$translator=new Illuminate\Translation\Translator($loader,'en');$container->instance('translator',$translator);
$resolver=new Illuminate\View\Engines\EngineResolver;$compiler=new Illuminate\View\Compilers\BladeCompiler($files,$qa.'/cache');$resolver->register('blade',fn()=>new Illuminate\View\Engines\CompilerEngine($compiler));
$finder=new Illuminate\View\FileViewFinder($files,[$qa]);$finder->addNamespace('shop',$root.'/plugins/shop/resources/views');
$factory=new Illuminate\View\Factory($resolver,$finder,new Illuminate\Events\Dispatcher($container));$factory->setContainer($container);$factory->share('__env',$factory);$container->instance('view',$factory);
class View342Item {public $itemId='Package-1';public $id=1;public $quantity=5;public function name(){return'Synthetic <script>item</script>'; }public function price(){return 10;}public function total(){return 50;}public function maxQuantity(){return 10;}public function hasQuantity(){return true;}}
class View342Cart {public function isEmpty(){return false;}public function content(){return collect([new View342Item]);}public function coupons(){return collect();}public function giftcards(){return collect();}public function total(){return 50;}public function payableTotal(){return 50;}}
$checks=0;function same($a,$b){global$checks;$checks++;if($a!==$b)throw new RuntimeException('Payment view assertion '.$checks);}
function errors342($messages){$bag=new Illuminate\Support\ViewErrorBag;return$bag->put('default',new Illuminate\Support\MessageBag($messages));}
function dom342($html){$dom=new DOMDocument;@$dom->loadHTML($html);return new DOMXPath($dom);}
$error=errors342(['quantities.Package-1'=>'<script>Bad quantity</script>']);
$html=$factory->make('cart',['cart'=>new View342Cart,'terms'=>null,'emailRequired'=>false,'errors'=>$error])->render();$xpath=dom342($html);
same($xpath->query('//input[@name="quantities[Package-1]" and contains(@class,"is-invalid")]')->length,1);same($xpath->query('//div[@role="alert" and contains(@class,"invalid-feedback")]')->length,1);
same($xpath->query('//input[@name="quantities[Package-1]" and @aria-invalid="true" and @aria-describedby="quantity-error-Package-1"]')->length,1);same($xpath->query('//div[@id="quantity-error-Package-1"]')->length,1);
same(str_contains($html,'&lt;script&gt;Bad quantity&lt;/script&gt;'),true);same(str_contains($html,'Synthetic &lt;script&gt;item&lt;/script&gt;'),true);same(str_contains($html,'<script>Bad quantity'),false);
same($xpath->query('//form[@action="/fixture/shop.cart.update" and @method="POST"]/input[@name="_token"]')->length,1);same($xpath->query('//input[@name="quantities[Package-1]" and @value="5" and @min="0" and @max="10"]')->length,1);
same(str_contains($html,'Your cart was not updated.'),true);
if(($argv[4]??'')==='preview')file_put_contents('/tmp/cart342-view-preview.html',$html);
foreach([
    ['user_id'=>['bad'],'transaction_id'=>['bad'],'price'=>['bad'],'packages'=>'bad'],
    ['packages'=>[['id'=>['bad'],'quantity'=>['bad']],'bad']],
    ['packages'=>[['id'=>10,'quantity'=>'3']]],
]as$index=>$old){
    $GLOBALS['fixtureOld']=$old;
    $html=$factory->make('manual',['categories'=>collect(),'quantifiablePackages'=>collect(),'errors'=>errors342(['packages.0.quantity'=>'<img src=x onerror=alert(1)>'])])->render();
    same(str_contains($html,'&lt;img src=x onerror=alert(1)&gt;'),true);same(str_contains($html,'<img src=x onerror=alert(1)>'),false);same(str_contains($html,'The payment was not recorded.'),true);
    preg_match('/const shopPaymentPackages = (.*);/',$html,$m);$rows=json_decode($m[1],true,512,JSON_THROW_ON_ERROR);
    same(is_array($rows),true);same(count($rows)>0,true);foreach($rows as$row){same(is_int($row['id']),true);same(is_int($row['quantity']),true);}
    if($index===2)same($rows,[['id'=>10,'quantity'=>3]]);
    $xpath=dom342($html);same($xpath->query('//form[@action="/fixture/shop.admin.payments.store" and @method="POST"]/input[@name="_token"]')->length,1);
}
echo json_encode(['passed'=>$checks,'actualCandidateBlades'=>true,'nativeIncludesAndLaravelCompiler'=>true,'layoutAndRoutes'=>'isolated fixtures','databaseOrProviderCalls'=>0,'browserAcceptance'=>false]).PHP_EOL;
