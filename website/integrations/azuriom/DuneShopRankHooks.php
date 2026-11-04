<?php
namespace Azuriom\Support;

use Azuriom\Plugin\Shop\Models\Package;
use Azuriom\Plugin\Shop\Models\PaymentItem;
use Azuriom\Plugin\Shop\Events\PaymentPaid;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Event;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

class DuneShopRankHooks
{
    public static function register(): void
    {
        Package::saving(function (Package $package) {
            if(request()->routeIs('shop.admin.packages.store','shop.admin.packages.update') && ($package->commands||$package->getOriginal('commands')) && $package->isDirty('commands') && !DuneShopGameIdentity::mayConfigure(request()->attributes->get('dune_identity',[])))throw ValidationException::withMessages(['commands'=>'Only authorised network console operators can configure server commands.']);

            if (request()->routeIs('shop.admin.packages.store','shop.admin.packages.update')) {
                $package->dune_website_rank = request()->input('dune_website_rank','user');
                $required=request()->input('dune_required_ranks',[]);
                if(!is_array($required)||count($required)>count(DuneShopRanks::PUBLIC_RANKS)||array_filter($required,fn($r)=>!is_string($r)||!DuneShopRanks::allowedRank($r))) throw ValidationException::withMessages(['dune_required_ranks'=>'Only public cosmetic website ranks are supported.']);
                if(request()->input('required_roles',[]))throw ValidationException::withMessages(['dune_required_ranks'=>'Select website ranks, not native roles.']);
                $package->dune_required_ranks=json_encode(array_values(array_unique($required)),JSON_THROW_ON_ERROR);
            }
            if (!DuneShopRanks::allowedRank($package->dune_website_rank??'user')) throw ValidationException::withMessages(['dune_website_rank'=>'Only public cosmetic ranks can be sold.']);
            if (request()->routeIs('shop.admin.packages.store','shop.admin.packages.update') && (request()->filled('role_id')||request()->filled('expired_role_id'))) throw ValidationException::withMessages(['dune_website_rank'=>'Native and staff roles cannot be sold. Select a website rank.']);
            $package->role_id=null;$package->expired_role_id=null;
            $package->required_roles=null;
        });
        PaymentItem::creating(function (PaymentItem $item) {
            if ($item->buyable instanceof Package) {
                $rank=$item->buyable->dune_website_rank??'user';
                if (!DuneShopRanks::allowedRank($rank)) throw new \RuntimeException('Protected package rank');
                $variables=$item->variables??[];$variables['_dune_website_rank']=$rank;
                $gameCommands=array_merge($item->buyable->commands??[],json_decode(setting('shop.commands')?:'[]',true,16,JSON_THROW_ON_ERROR));
                if($gameCommands){$recipient=DuneShopGameIdentity::linked($item->payment->user->dune_account_id??'');if(!$recipient)throw ValidationException::withMessages(['minecraft'=>'Link your Minecraft account before buying a package with in-game rewards.']);$variables['_dune_minecraft_uuid']=$recipient['uuid'];$variables['_dune_minecraft_name']=$recipient['name'];}
                $item->variables=$variables;
            }
        });
        Event::listen(PaymentPaid::class,function (PaymentPaid $event) {
            if ($event->payment->status!=='completed') return;
            foreach($event->payment->items as $item) if($item->buyable instanceof Package) self::enqueue($item,'grant');
            self::drain();
        });
        Artisan::command('dune-shop:deliver-ranks',function(){ DuneShopRankHooks::drain(); $this->info('Queued rank deliveries checked.'); });
    }
    public static function hasRequiredWebsiteRole(Package $package): bool
    {
        if(($package->commands||setting('shop.commands'))&&!DuneShopGameIdentity::linked(request()->attributes->get('dune_identity')['id']??''))return false;
        $required=json_decode($package->dune_required_ranks??'[]',true,8,JSON_THROW_ON_ERROR);
        if(!$required)return true;
        if(!is_array($required)||array_filter($required,fn($r)=>!is_string($r)||!DuneShopRanks::allowedRank($r)))return false;
        $identity=request()->attributes->get('dune_identity');
        if(!is_array($identity))return false;
        return in_array('user',$required,true)||count(array_intersect($required,$identity['ranks']??[]))>0;
    }
    public static function enqueue(PaymentItem $item,string $operation): void
    {
        $rank=$item->variables['_dune_website_rank']??null;
        if($rank===null)return; // No invented backfill for receipts predating this bridge.
        if(!DuneShopRanks::allowedRank($rank))throw new \RuntimeException('Protected receipt rank');
        $account=$item->payment->user->dune_account_id;
        if(!is_string($account)||!preg_match('/^[a-f0-9-]{36}$/i',$account))throw new \RuntimeException('Verified central identity required');
        $key='azuriom:item:'.$item->id;
        $payload=['receipt'=>$key,'account'=>$account,'rank'=>$rank,'status'=>$item->payment->status,'expires'=>$item->expires_at?->getTimestampMs()];
        $row=['receipt_key'=>$key,'item_id'=>$item->id,'operation'=>$operation,'payload'=>json_encode($payload,JSON_THROW_ON_ERROR),'state'=>'pending','updated_at'=>now()];
        if($operation==='grant')DB::table('dune_rank_delivery_jobs')->insertOrIgnore($row);
        else DB::table('dune_rank_delivery_jobs')->upsert([$row],['receipt_key'],['operation','state','updated_at']);
    }
    public static function revoke(PaymentItem $item): void
    {
        self::enqueue($item,'revoke');self::drain();
    }
    public static function drain(): void
    {
        $keys=DB::table('dune_rank_delivery_jobs')->where('state','pending')->orderBy('updated_at')->limit(50)->pluck('receipt_key');
        foreach($keys as $key) {
            try { DB::transaction(function()use($key){
                $job=DB::table('dune_rank_delivery_jobs')->where('receipt_key',$key)->lockForUpdate()->first();
                if(!$job||$job->state!=='pending')return;
                $bridge=new DuneShopRanks();
                if($job->operation==='revoke')$bridge->revoke($key);
                else {
                    $item=PaymentItem::findOrFail($job->item_id);
                    if($item->payment->status!=='completed')return;
                    $bridge->grant(json_decode($job->payload,true,16,JSON_THROW_ON_ERROR));
                }
                DB::table('dune_rank_delivery_jobs')->where('receipt_key',$key)->update(['state'=>'done','updated_at'=>now()]);
            }); } catch(\Throwable $e) { Log::warning('DuneRealms paid rank delivery queued for retry.', ['receipt'=>$key,'error_class'=>get_class($e)]); }
        }
    }
}
