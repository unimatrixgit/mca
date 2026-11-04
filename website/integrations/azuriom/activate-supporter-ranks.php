<?php
chdir('/home/dunerealms/apps/azuriom');require 'vendor/autoload.php';$app=require 'bootstrap/app.php';$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();
use Illuminate\Support\Facades\DB;use Azuriom\Plugin\Shop\Models\Package;use Azuriom\Support\DuneSupporterHooks as Hooks;
if(!config('dunerealms.supporters_enabled'))throw new RuntimeException('Activation gate closed');
$private='/home/dunerealms/.config/supporter329/';
if(!file_exists($private.'sale-before.json')){file_put_contents($private.'sale-before.json',json_encode(['packages'=>DB::table('shop_packages')->get(),'categories'=>DB::table('shop_categories')->get(),'mapping'=>DB::table('dune_supporter_packages')->get()],JSON_THROW_ON_ERROR));chmod($private.'sale-before.json',0600);}
// Multiple native products can safely map to the same fixed cosmetic rank.
foreach(DB::select('SHOW INDEX FROM dune_supporter_packages')as$i)if($i->Key_name!=='PRIMARY'&&!$i->Non_unique&&$i->Column_name==='package_key'){
 if(!preg_match('/^[a-zA-Z0-9_]+$/',$i->Key_name))throw new RuntimeException('Unexpected index');DB::statement('ALTER TABLE dune_supporter_packages DROP INDEX `'.$i->Key_name.'`');
}
$payments=hash('sha256',DB::table('shop_payments')->orderBy('id')->get()->toJson());$items=hash('sha256',DB::table('shop_payment_items')->orderBy('id')->get()->toJson());
$result=DB::transaction(function(){
 $map=[47=>'caravan-supporter',48=>'guild-patron',49=>'imperial-patron',50=>'grand-benefactor'];
 foreach($map as$id=>$key){$p=Package::findOrFail($id);$rank=Hooks::RANKS[$key];$title=$p->name;
  $p->dune_website_rank=$rank;$p->short_description='Cosmetic supporter rank · website recognition and matching chat title';
  $p->description='<h2>'.htmlspecialchars($title).'</h2><p>Stand beside the realms as a recognised community patron. Your support helps sustain the project.</p><h3>Your recognition</h3><ul><li>The '.htmlspecialchars($title).' website rank.</li><li>A matching coloured chat title through your linked Minecraft account.</li></ul><p><strong>Cosmetic only:</strong> no gameplay advantage, items, currency, experience boost, staff authority or political powers. This is a one-time purchase in euro.</p><p>Keep your existing earned ranks and progression. Minecraft linking is available in your Account Center.</p>';
  $p->is_enabled=true;$p->save();if(!Hooks::safe($p,$key))throw new RuntimeException('Unsafe supporter '.$id);
 }
 DB::table('dune_supporter_packages')->updateOrInsert(['package_id'=>1],['package_key'=>'imperial-patron']);$vip=Package::findOrFail(1);$vip->has_quantity=false;$vip->save();if(!Hooks::safe($vip,'imperial-patron'))throw new RuntimeException('Existing VIP not cosmetic-safe');
 DB::table('shop_categories')->where('id',8)->update(['slug'=>'patron-collection','name'=>'Patron Collection','description'=>'Cosmetic community recognition. No gameplay advantage.','is_enabled'=>1]);
 return DB::table('shop_packages')->whereIn('id',array_keys($map))->get(['id','name','price','is_enabled','dune_website_rank']);
});
if($payments!==hash('sha256',DB::table('shop_payments')->orderBy('id')->get()->toJson())||$items!==hash('sha256',DB::table('shop_payment_items')->orderBy('id')->get()->toJson()))throw new RuntimeException('Commerce history changed during activation; review required');
file_put_contents($private.'sale-result.json',json_encode(['packages'=>$result,'priorPaymentsAndItemsUnchanged'=>true,'existingVipPrice'=>50,'realPaymentsCreated'=>0],JSON_PRETTY_PRINT));chmod($private.'sale-result.json',0600);echo file_get_contents($private.'sale-result.json').PHP_EOL;
