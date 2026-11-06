<?php
namespace Illuminate\Support\Facades {
class Auth {
    static $logged=false,$out=0;
    static function check(){return self::$logged;}
    static function logout(){self::$logged=false;self::$out++;}
}
}
namespace {
class IdentityDenied extends \RuntimeException {function __construct(public int $status){parent::__construct('Identity route denied');}}
function abort($status,$message=''){throw new IdentityDenied($status);}
function abort_unless($ok,$status,$message=''){if(!$ok)abort($status,$message);}
function response(){return new class {
    function view($name){return new class($name) {
        public $headers=[];function __construct(public string $name){}
        function header($key,$value){$this->headers[$key]=$value;return $this;}
    };}
};}
function redirect($url,$status=302){return new class($url,$status) {
    public $headers=[];function __construct(public $url,public $status){}
    function header($key,$value){$this->headers[$key]=$value;return $this;}
};}
require $argv[1]??__DIR__.'/DuneShopAccount.php';
use Azuriom\Http\Middleware\DuneShopAccount as Policy;
$checks=0;function same($actual,$expected){global$checks;$checks++;if($actual!==$expected)throw new \RuntimeException('Identity assertion '.$checks.' failed');}
function request($path,$method,$body=[],$cookies='',$session=null){return new class($path,$method,$body,$cookies,$session) {
    public $bridge=false;
    function __construct(public $p,public $m,public $body,public $cookies,public $s){}
    function path(){return$this->p;}function method(){return$this->m;}function isMethod($method){return$this->m===$method;}
    function server($key,$default=''){return$this->cookies;}function session(){return$this->s;}
};}
$policy=new Policy;
foreach(['GET','HEAD']as$method){
    $called=false;$result=$policy->handle(request('profile',$method,['next'=>'https://invalid.example/']),function()use(&$called){$called=true;});
    same($called,false);same($result->name,'dune-account');same($result->headers['Cache-Control'],'private, no-store');same($result->headers['Referrer-Policy'],'no-referrer');
}
$mutations=['user/register','user/password/reset','user/password/email','user/password/confirm','user/email/verification-notification','profile/email','profile/password','profile/name','profile/avatar','profile/discord/unlink','profile/2fa/enable','profile/2fa/disable','profile/delete/send','profile/delete/confirm','profile/money/transfer'];
foreach($mutations as$path)foreach([[],['email'=>'   ','password'=>[]],['email'=>'Élodie@example.invalid','name'=>'<script>','id'=>-1]]as$body){
    $called=false;
    try{$policy->handle(request($path,'POST',$body),function()use(&$called){$called=true;});throw new \RuntimeException('Native mutation unexpectedly reached controller');}
    catch(IdentityDenied$e){same($e->status,403);same($called,false);}
}
foreach(['GET','HEAD']as$method){$called=false;$result=$policy->handle(request('shop/profile',$method),function()use(&$called){$called=true;return'purchase history';});same($called,true);same($result,'purchase history');}
$called=false;$result=$policy->handle(request('profile/theme','POST'),function()use(&$called){$called=true;return'cosmetic theme';});same($called,true);same($result,'cosmetic theme');
$called=false;$result=$policy->handle(request('user/login','GET'),function()use(&$called){$called=true;});same($called,false);same($result->url,'https://cp.dunerealms.com/store/dune-login');
foreach(['POST','PUT','PATCH','DELETE']as$method){try{$policy->handle(request('user/login',$method),fn()=>null);throw new \RuntimeException('Legacy native login allowed');}catch(IdentityDenied$e){same($e->status,404);}}
// An old native login still loses its basket before it can reach the account landing page.
\Illuminate\Support\Facades\Auth::$logged=true;
$session=new class {public $invalid=false,$token=false;function get($key){return str_repeat('0',64);}function invalidate(){$this->invalid=true;}function regenerateToken(){$this->token=true;}};
$called=false;$result=$policy->handle(request('profile','GET',[],'__Host-dune_session='.str_repeat('a',43),$session),function()use(&$called){$called=true;});
same($called,false);same($result->status,303);same($session->invalid&&$session->token,true);same(\Illuminate\Support\Facades\Auth::$logged,false);
echo json_encode(['passed'=>$checks,'databaseBridgeOrProviderCalls'=>0,'productionMutations'=>false]).PHP_EOL;
}
