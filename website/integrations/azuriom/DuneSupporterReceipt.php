<?php
namespace Azuriom\Support;
use RuntimeException;

/** Versioned, signed immutable purchase snapshot. Native item/account binding is mandatory. */
final class DuneSupporterReceipt
{
    public const PRICES = ['caravan-supporter'=>1000,'guild-patron'=>2500,'imperial-patron'=>5000,'grand-benefactor'=>9000];
    public static function sign(string $account, string $package, int $payment, int $buyable, string $uuid, string $secret): array
    {
        if (!isset(self::PRICES[$package]) || $payment < 1 || $buyable < 1 || strlen($secret)<32
            || !self::uuid($account) || !self::uuid($uuid)) throw new RuntimeException('Invalid cosmetic snapshot');
        $data=['account'=>$account,'package'=>$package,'payment'=>$payment,'buyable'=>$buyable,'uuid'=>strtolower($uuid),'version'=>1];
        return ['_dune_supporter'=>json_encode($data,JSON_THROW_ON_ERROR), '_dune_supporter_sig'=>hash_hmac('sha256',json_encode($data,JSON_THROW_ON_ERROR),$secret)];
    }
    public static function verify(array $variables, string $account, int $payment, int $buyable, string $secret): ?array
    {
        if (!isset($variables['_dune_supporter'])) return null;
        $raw=$variables['_dune_supporter'];$sig=$variables['_dune_supporter_sig']??null;
        if (!is_string($raw)||strlen($raw)>1024||!is_string($sig)||strlen($secret)<32
            || !hash_equals(hash_hmac('sha256',$raw,$secret),$sig)) throw new RuntimeException('Invalid cosmetic signature');
        $s=json_decode($raw,true,8,JSON_THROW_ON_ERROR);
        if (!is_array($s)||count($s)!==6||($s['account']??null)!==$account||($s['payment']??null)!==$payment
            ||($s['buyable']??null)!==$buyable||($s['version']??null)!==1||!isset(self::PRICES[$s['package']??''])
            ||!self::uuid($s['uuid']??null)) throw new RuntimeException('Cosmetic receipt ownership mismatch');
        return $s;
    }
    public static function uuid(mixed $value): bool
    {
        return is_string($value)&&preg_match('/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/iD',$value)===1;
    }
}
