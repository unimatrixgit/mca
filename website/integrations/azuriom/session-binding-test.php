<?php
namespace Illuminate\Support\Facades {class Auth{static $logged=true;static $out=0;static function check(){return self::$logged;}static function logout(){self::$logged=false;self::$out++;}}}
namespace {
function abort($s,$m=''){throw new Exception('Unexpected abort '.$s);}function abort_unless($v,$s,$m=''){if(!$v)abort($s,$m);}function redirect($url,$status=302){return new class($url,$status){public $headers=[];function __construct(public $url,public $status){}function header($k,$v){$this->headers[$k]=$v;return $this;}};}
require $argv[1];use Azuriom\Http\Middleware\DuneShopAccount as P;
$n=0;function ck($v){global$n;$n++;if(!$v)throw new Exception('Session assertion '.$n);}
$a=str_repeat('a',43);$b=str_repeat('b',43);ck(P::centralSessionRef('__Host-dune_session='.$a)===hash('sha256',$a));ck(P::centralSessionRef('x=1; __Host-dune_session='.$a.'; y=2')===hash('sha256',$a));foreach(['','__Host-dune_session=bad','__Host-dune_session='.$a.'; __Host-dune_session='.$b,'fake__Host-dune_session='.$a]as$raw)ck(P::centralSessionRef($raw)===null);
foreach([['__Host-dune_session='.$a,hash('sha256',$b)],['',hash('sha256',$a)],['__Host-dune_session='.$a,'invalid']]as[$cookies,$ref]){
 Illuminate\Support\Facades\Auth::$logged=true;
 $session=new class($ref){public $invalid=false;public $regen=false;public $cart=['old-account-item'];function __construct(public $ref){}function get($k){return $k==='dune_session_ref'?$this->ref:'previous';}function invalidate(){$this->invalid=true;$this->cart=[];}function regenerateToken(){$this->regen=true;}};
 $request=new class($session,$cookies){function __construct(public $s,public $cookies){}function path(){return 'shop/payments/stripe/pay';}function isMethod($m){return $m==='POST';}function server($k,$default=''){return $this->cookies;}function session(){return $this->s;}};
 $called=false;$r=(new P)->handle($request,function()use(&$called){$called=true;});ck(!$called);ck($r->status===303);ck($r->url==='https://cp.dunerealms.com/store/dune-login');ck($r->headers['Cache-Control']==='no-store');ck($session->invalid&&$session->regen&&$session->cart===[]);ck(!Illuminate\Support\Facades\Auth::$logged);
}
echo "$n session-binding checks passed; no database, bridge or payment calls.\n";
}
