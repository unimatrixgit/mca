<?php
// Run as application owner. Disabled catalogue placeholders only; no provisioning or charges.
chdir('/home/dunerealms/apps/azuriom');
require 'vendor/autoload.php';$app=require 'bootstrap/app.php';
$app->make(Illuminate\Contracts\Console\Kernel::class)->bootstrap();
use Illuminate\Support\Facades\DB;
$data=json_decode(file_get_contents(__DIR__.'/hosting-catalogue.json'),true,32,JSON_THROW_ON_ERROR);
$previous=is_file(__DIR__.'/applied-catalogue.json')?json_decode(file_get_contents(__DIR__.'/applied-catalogue.json'),true,32,JSON_THROW_ON_ERROR):null;
if($data['checkoutEnabled']!==false||$data['provisioningEnabled']!==false||count($data['packages'])!==45)throw new RuntimeException('Unexpected preview scope');
$marker='<!-- dune-hosting-preview-20261102 -->';
$prefix=$marker.'<link rel="stylesheet" href="/store/assets/css/hosting-preview.css?v=20261102-5">';
$backup=__DIR__.'/before.json';
if(!file_exists($backup)){file_put_contents($backup,json_encode(['categories'=>DB::table('shop_categories')->get(),'packages'=>DB::table('shop_packages')->get()],JSON_PRETTY_PRINT|JSON_THROW_ON_ERROR));chmod($backup,0600);}
$before=hash('sha256',DB::table('shop_payment_items')->orderBy('id')->get()->toJson());
DB::transaction(function()use($data,$previous,$marker,$prefix){
 $ids=[];$now=gmdate('Y-m-d H:i:s');$oldCats=[];$oldPackages=[];
 foreach($previous['categories']??[] as $c)$oldCats[$c['slug']]=$c;
 foreach($previous['packages']??[] as $p)$oldPackages[$p['name']]=$p;
 foreach($data['categories'] as $i=>$c){
  $old=DB::table('shop_categories')->where('slug',$c['slug'])->lockForUpdate()->first();
  $parent=isset($c['parent'])?($ids[$c['parent']]??null):null;
  if(isset($c['parent'])&&!$parent)throw new RuntimeException('Parent category missing');
  $attrs=['name'=>$c['name'],'description'=>$prefix.$c['description'],'parent_id'=>$parent,'position'=>$c['position']??(10+$i),'updated_at'=>$now];
  if($old){
   $expected=$oldCats[$c['slug']]['description']??null;
   $managed=str_contains($old->description??'',$marker);
   $unchanged=$old->description===$attrs['description']||($expected!==null&&in_array($old->description,array_map(fn($v)=>preg_replace('/20261102-[12345]/','20261102-'.$v,$prefix).$expected,[1,2,3,4,5]),true));
   $emptyWebsite=$c['slug']==='web'&&$old->name==='Website Hosting'&&empty($old->description)&&!DB::table('shop_packages')->where('category_id',$old->id)->exists();
   if(!$emptyWebsite&&(!$managed||!$unchanged))throw new RuntimeException('Category edited outside managed catalogue; stop');
   DB::table('shop_categories')->where('id',$old->id)->update($attrs);$ids[$c['slug']]=$old->id;
  }else $ids[$c['slug']]=DB::table('shop_categories')->insertGetId($attrs+['slug'=>$c['slug'],'icon'=>'bi bi-hdd-network','is_enabled'=>1,'created_at'=>$now]);
 }
 foreach($data['packages'] as $p){
  if($p['is_enabled']!==false||$p['commands']!==[])throw new RuntimeException('Unsafe placeholder');
  $suffix='<p>'.htmlspecialchars($p['note'],ENT_QUOTES,'UTF-8').'</p>';
  $old=DB::table('shop_packages')->where('name',$p['name'])->lockForUpdate()->first();
  if($old){
   $prev=$oldPackages[$p['name']]??null;
   $expected=$prev?$prev['description'].'<p>'.htmlspecialchars($prev['note'],ENT_QUOTES,'UTF-8').'</p>':null;
   if($old->is_enabled||$old->commands!=='[]'||!in_array((float)$old->price,[(float)$p['price'],(float)($prev['price']??$p['price'])],true)||!in_array($old->description,[$expected,$p['description'].$suffix],true))throw new RuntimeException('Package changed; stop');
   DB::table('shop_packages')->where('id',$old->id)->update(['category_id'=>$ids[$p['category']],'description'=>$p['description'].$suffix,'price'=>$p['price']]);continue;
  }
  DB::table('shop_packages')->insert(['category_id'=>$ids[$p['category']],'name'=>$p['name'],'short_description'=>'Disabled hosting preview · '.$p['termMonths'].' month term · EUR excluding VAT','description'=>$p['description'].$suffix,'position'=>$p['position'],'image'=>$p['image'],'price'=>$p['price'],'billing_type'=>'one-off','billing_period'=>null,'commands'=>'[]','files'=>'[]','is_enabled'=>0,'has_quantity'=>0,'dune_website_rank'=>'user','dune_required_ranks'=>'["user"]','created_at'=>$now,'updated_at'=>$now]);
 }
});
file_put_contents(__DIR__.'/applied-catalogue.json',json_encode($data,JSON_PRETTY_PRINT|JSON_THROW_ON_ERROR));chmod(__DIR__.'/applied-catalogue.json',0600);
$after=hash('sha256',DB::table('shop_payment_items')->orderBy('id')->get()->toJson());
echo json_encode(['categoryIds'=>DB::table('shop_categories')->whereIn('slug',array_column($data['categories'],'slug'))->pluck('id','slug'),'disabledHostingPackages'=>DB::table('shop_packages')->whereIn('category_id',DB::table('shop_categories')->select('id')->whereIn('slug',array_column($data['categories'],'slug')))->where('is_enabled',0)->count(),'paymentItemsUnchanged'=>$before===$after],JSON_PRETTY_PRINT);
