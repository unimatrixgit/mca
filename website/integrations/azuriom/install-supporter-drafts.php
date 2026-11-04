<?php
// Run as application owner from a PRIVATE stage directory containing source/schema/catalogue.
// Creates disabled drafts only. No existing VIP or receipt is updated.
chdir('/home/dunerealms/apps/azuriom');require 'vendor/autoload.php';$app=require 'bootstrap/app.php';$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();
use Illuminate\Support\Facades\DB;
$data=json_decode(file_get_contents(__DIR__.'/supporter-catalogue.json'),true,32,JSON_THROW_ON_ERROR);
if($data['version']!==1||$data['checkoutEnabled']!==false||count($data['packages'])!==4)throw new RuntimeException('Unexpected supporter scope');
$private='/home/dunerealms/.config/supporter329';if(!is_dir($private))mkdir($private,0700,true);
$manifest=$private.'/install.json';
if(is_file($manifest)){echo json_encode(['alreadyInstalled'=>true,'manifestUnchanged'=>true]).PHP_EOL;exit;}
$image='/home/dunerealms/apps/azuriom/storage/app/public/packages/supporter-hall-v1.png';
if(is_file($image)&&hash_file('sha256',$image)!==hash_file('sha256',__DIR__.'/supporter-hall-v1.png'))throw new RuntimeException('Existing product artwork collision');
if(!is_dir(dirname($image)))mkdir(dirname($image),0755,true);
if(!is_file($image))copy(__DIR__.'/supporter-hall-v1.png',$image);
$backup=fopen($private.'/before.json','x');if(!$backup)throw new RuntimeException('Existing backup requires manual review');
fwrite($backup,json_encode(['packages'=>DB::table('shop_packages')->get(),'categories'=>DB::table('shop_categories')->get()],JSON_THROW_ON_ERROR));fclose($backup);chmod($private.'/before.json',0600);
$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true,8,JSON_THROW_ON_ERROR);
$central=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
foreach(['supporter_receipts','supporter_cosmetics']as$table){$q=$central->prepare('SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?');$q->execute([$table]);if($q->fetchColumn())throw new RuntimeException('Unexpected existing cosmetic table');}
foreach(array_filter(array_map('trim',explode(';',file_get_contents(__DIR__.'/supporter-ledger-schema.sql'))))as$sql)$central->exec($sql);
DB::statement('CREATE TABLE dune_supporter_packages(package_id BIGINT UNSIGNED PRIMARY KEY,package_key VARCHAR(64) NOT NULL UNIQUE) ENGINE=InnoDB');
 DB::statement("CREATE TABLE dune_supporter_jobs(receipt_key VARCHAR(40) PRIMARY KEY,item_id BIGINT UNSIGNED NOT NULL,operation ENUM('grant','revoke') NOT NULL,state ENUM('pending','done') NOT NULL,updated_at TIMESTAMP NULL) ENGINE=InnoDB");
$result=DB::transaction(function()use($data){
 if(DB::table('shop_categories')->where('slug','patron-collection-preview')->exists())throw new RuntimeException('Category collision');
 $beforeItems=hash('sha256',DB::table('shop_payment_items')->orderBy('id')->get()->toJson());
 $beforePayments=hash('sha256',DB::table('shop_payments')->orderBy('id')->get()->toJson());
 $vipBefore=hash('sha256',json_encode(DB::table('shop_packages')->where('id',1)->first(),JSON_THROW_ON_ERROR));
 $now=gmdate('Y-m-d H:i:s');$category=DB::table('shop_categories')->insertGetId(['slug'=>'patron-collection-preview','name'=>'Patron Collection — Preview','description'=>'Disabled cosmetic designs awaiting delivery acceptance.','is_enabled'=>0,'position'=>90,'created_at'=>$now,'updated_at'=>$now]);$ids=[];
 foreach($data['packages']as$p){
  $prices=['caravan-supporter'=>1000,'guild-patron'=>2500,'imperial-patron'=>5000,'grand-benefactor'=>9000];
  if($p['enabled']!==false||($prices[$p['key']]??null)!==$p['priceCents']||!in_array($p['rank'],['user','vip'],true))throw new RuntimeException('Unsafe supporter draft');
  $id=DB::table('shop_packages')->insertGetId(['category_id'=>$category,'name'=>$p['name'],'short_description'=>'Disabled cosmetic preview — no purchase or gameplay benefits','description'=>$p['description'],'position'=>$p['position'],'image'=>'supporter-hall-v1.png','price'=>$p['priceCents']/100,'billing_type'=>'one-off','commands'=>'[]','files'=>'[]','is_enabled'=>0,'has_quantity'=>0,'dune_website_rank'=>$p['rank'],'dune_required_ranks'=>'["user"]','created_at'=>$now,'updated_at'=>$now]);
  DB::table('dune_supporter_packages')->insert(['package_id'=>$id,'package_key'=>$p['key']]);$ids[$p['key']]=$id;
 }
 $unchanged=$beforeItems===hash('sha256',DB::table('shop_payment_items')->orderBy('id')->get()->toJson())&&$beforePayments===hash('sha256',DB::table('shop_payments')->orderBy('id')->get()->toJson())&&$vipBefore===hash('sha256',json_encode(DB::table('shop_packages')->where('id',1)->first(),JSON_THROW_ON_ERROR));
 if(!$unchanged)throw new RuntimeException('Existing commerce data changed');
 return ['category'=>$category,'packageIds'=>$ids,'disabled'=>true,'existingVipAndPaymentsUnchanged'=>$unchanged];
});
file_put_contents($manifest,json_encode($result,JSON_PRETTY_PRINT|JSON_THROW_ON_ERROR));chmod($manifest,0600);echo json_encode($result).PHP_EOL;
