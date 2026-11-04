<?php
// Candidate classes loaded before app boot; all model writes and central reads use memory fixtures.
chdir('/home/dunerealms/apps/azuriom');require 'vendor/autoload.php';
foreach(['DuneShopRanks','DuneShopRankHooks','DuneSupporterReceipt','DuneSupporterHooks']as$c)require_once __DIR__.'/'.$c.'.php';
$app=require 'bootstrap/app.php';$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();
use Illuminate\Support\Facades\DB;
use Azuriom\Plugin\Shop\Models\Package;
use Azuriom\Plugin\Shop\Models\PaymentItem;
use Azuriom\Support\DuneSupporterHooks as Hooks;
setting('shop.commands');
config(['database.connections.test329'=>['driver'=>'sqlite','database'=>':memory:','prefix'=>''],'dunerealms.supporters_enabled'=>false]);DB::setDefaultConnection('test329');
foreach([
'users(id INTEGER PRIMARY KEY,name TEXT,dune_account_id TEXT)',
'shop_payments(id INTEGER PRIMARY KEY,user_id INTEGER,status TEXT)',
'shop_packages(id INTEGER PRIMARY KEY,name TEXT,price REAL,is_enabled INTEGER,billing_type TEXT,commands TEXT,files TEXT,money REAL,giftcard_balance REAL,role_id INTEGER,expired_role_id INTEGER,has_quantity INTEGER,custom_price INTEGER,required_roles TEXT,dune_website_rank TEXT,created_at TEXT,updated_at TEXT)',
'shop_payment_items(id INTEGER PRIMARY KEY,payment_id INTEGER,buyable_type TEXT,buyable_id INTEGER,variables TEXT,quantity INTEGER,price REAL,expires_at TEXT,created_at TEXT,updated_at TEXT)',
'dune_supporter_packages(package_id INTEGER PRIMARY KEY,package_key TEXT)']as$s)DB::statement('CREATE TABLE '.$s);
$central=new PDO('sqlite::memory:',null,null,[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
$central->exec('CREATE TABLE cp_ranks(rank_key TEXT PRIMARY KEY,kind TEXT,rank_level INT,permissions_json TEXT)');
$central->exec('CREATE TABLE site_account_policies(user_id TEXT PRIMARY KEY,game_link_exempt INT)');
foreach(['user'=>10,'patron_caravan'=>12,'patron_guild'=>15,'vip'=>21,'patron_grand'=>25]as$r=>$l)$central->prepare('INSERT INTO cp_ranks VALUES(?,?,?,?)')->execute([$r,'access',$l,'["web.menu.game"]']);
Hooks::register($central);$n=0;
$check=function($ok)use(&$n){if(!$ok)throw new RuntimeException('Native rank assertion '.($n+1));$n++;};
$reject=function($fn)use($check){try{$fn();}catch(Illuminate\Validation\ValidationException $e){$check(true);return;}$check(false);};
$a='11111111-1111-4111-8111-111111111111';DB::table('users')->insert(['id'=>1,'name'=>'Fixture','dune_account_id'=>$a]);DB::table('shop_payments')->insert(['id'=>1,'user_id'=>1,'status'=>'pending']);
foreach(['caravan-supporter'=>10,'guild-patron'=>25,'imperial-patron'=>50,'grand-benefactor'=>90]as$key=>$price){
$id=$price;DB::table('shop_packages')->insert(['id'=>$id,'name'=>$key,'price'=>$price,'is_enabled'=>0,'billing_type'=>'one-off','commands'=>'[]','files'=>'[]','has_quantity'=>0,'custom_price'=>0,'dune_website_rank'=>Hooks::RANKS[$key]]);DB::table('dune_supporter_packages')->insert(['package_id'=>$id,'package_key'=>$key]);
$p=Package::findOrFail($id);$check(Hooks::safe($p,$key));$p->is_enabled=true;$reject(fn()=>$p->save());
config(['dunerealms.supporters_enabled'=>true]);$p->save();$check((bool)$p->fresh()->is_enabled);
foreach(['commands'=>['give {uuid} diamond'],'money'=>100,'has_quantity'=>true,'custom_price'=>true,'dune_website_rank'=>'owners','price'=>1]as$field=>$value){$bad=$p->fresh();$bad->$field=$value;$reject(fn()=>$bad->save());}
$neutral=$p->fresh();$neutral->role_id=1;$check(!Hooks::safe($neutral,$key));$neutral->save();$check($neutral->fresh()->role_id===null);
$item=new PaymentItem(['price'=>$price,'quantity'=>1,'variables'=>['_dune_website_rank'=>'owners','_dune_supporter'=>'forged']]);$item->payment_id=1;$item->buyable_id=$id;$item->buyable_type=$p->getMorphClass();$item->save();$check($item->variables['_dune_website_rank']===Hooks::RANKS[$key]);$check(!isset($item->variables['_dune_supporter']));
$central->prepare('INSERT INTO site_account_policies VALUES(?,1)')->execute([$a]);$bad=new PaymentItem(['price'=>$price,'quantity'=>1]);$bad->payment_id=1;$bad->buyable_id=$id;$bad->buyable_type=$p->getMorphClass();$reject(fn()=>$bad->save());$central->exec('DELETE FROM site_account_policies');
config(['dunerealms.supporters_enabled'=>false]);
}
$central->exec("UPDATE cp_ranks SET permissions_json='[\"web.ranks.manage\"]' WHERE rank_key='vip'");$check(!Hooks::safe(Package::findOrFail(50),'imperial-patron'));
echo json_encode(['nativeFrameworkAssertions'=>$n,'databases'=>'memory fixtures only','realPayments'=>0,'gatewayCalled'=>false]).PHP_EOL;
