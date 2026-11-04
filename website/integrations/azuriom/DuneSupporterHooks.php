<?php
namespace Azuriom\Support;
use Azuriom\Plugin\Shop\Models\Package;
use Azuriom\Plugin\Shop\Models\PaymentItem;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

/** Cosmetic ranks only. Actual receipt delivery/revocation uses DuneShopRankHooks. */
final class DuneSupporterHooks
{
    private static bool $registered=false;
    private static ?\PDO $central=null;
    public const RANKS=['caravan-supporter'=>'patron_caravan','guild-patron'=>'patron_guild','imperial-patron'=>'vip','grand-benefactor'=>'patron_grand'];
    public static function safe(Package $p,string $key): bool
    {
        return isset(self::RANKS[$key]) && ($p->dune_website_rank??'user')===self::RANKS[$key]
            && !$p->commands && !$p->files && !$p->money && !$p->giftcard_balance && !$p->role_id && !$p->expired_role_id
            && !$p->has_quantity && !$p->custom_price && $p->billing_type==='one-off'
            && (int)round($p->price*100)===(DuneSupporterReceipt::PRICES[$key]??null)
            && !json_decode(setting('shop.commands')?:'[]',true,16,JSON_THROW_ON_ERROR)
            && (new DuneShopRanks(self::$central))->safeForSale(self::RANKS[$key]);
    }
    public static function register(?\PDO $central=null): void
    {
        if($central)self::$central=$central;
        if(self::$registered)return;self::$registered=true;
        Package::saving(function(Package $p){
            $key=$p->exists?DB::table('dune_supporter_packages')->where('package_id',$p->id)->value('package_key'):null;
            if($key && (!self::safe($p,$key)||($p->is_enabled&&!config('dunerealms.supporters_enabled',false))))
                throw ValidationException::withMessages(['package'=>'Supporter products may grant their fixed cosmetic rank only. Gameplay rewards and staff roles are forbidden.']);
        });
        PaymentItem::creating(function(PaymentItem $item){
            $vars=$item->variables??[];unset($vars['_dune_supporter'],$vars['_dune_supporter_sig']);$item->variables=$vars;
            $p=$item->buyable;
            $key=$p instanceof Package?DB::table('dune_supporter_packages')->where('package_id',$p->id)->value('package_key'):null;
            if(!$key)return;
            if(!config('dunerealms.supporters_enabled',false)||!$p->is_enabled||!self::safe($p,$key)||$item->quantity!==1||$item->expires_at!==null)
                throw ValidationException::withMessages(['package'=>'This cosmetic rank is not available with those purchase options.']);
            $account=(string)($item->payment->user->dune_account_id??'');
            $db=self::$central;
            if(!$db){
                $c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true,8,JSON_THROW_ON_ERROR);
                $db=new \PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[\PDO::ATTR_ERRMODE=>\PDO::ERRMODE_EXCEPTION]);
            }
            $q=$db->prepare('SELECT 1 FROM site_account_policies WHERE user_id=? AND game_link_exempt=1');$q->execute([$account]);
            if($q->fetchColumn())throw ValidationException::withMessages(['account'=>'Service accounts cannot buy player ranks.']);
            // Existing bridge snapshots this server-selected rank, never a client-supplied role.
            $vars=$item->variables;$vars['_dune_website_rank']=self::RANKS[$key];$item->variables=$vars;
        });
    }
}
