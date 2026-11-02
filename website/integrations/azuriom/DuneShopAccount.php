<?php

namespace Azuriom\Http\Middleware;

use Azuriom\Models\Role;
use Azuriom\Models\User;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;

/** Central account is authoritative; native passwords and ranks are not used. */
class DuneShopAccount
{
    public static function bridge(string $operation, array $payload): array
    {
        $config = json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'), true, 8, JSON_THROW_ON_ERROR);
        $response = Http::timeout(5)->withToken($config['secret'])
            ->post('http://127.0.0.1:3003/internal/integrations/'.$operation, $payload);
        abort_unless($response->successful(), $response->status() === 401 ? 401 : 503, 'DuneRealms sign-in unavailable. Please sign in again.');
        return $response->json();
    }

    public static function roleName(array $identity): string
    {
        if (in_array('owners', $identity['ranks'] ?? [], true)) return 'DuneRealms Owner';
        if (in_array('web.tickets.sales', $identity['permissions'] ?? [], true)) return 'DuneRealms Sales';
        return in_array('web.shop.support', $identity['permissions'] ?? [], true)
            ? 'DuneRealms Shop Support' : 'DuneRealms Player';
    }

    public static function supportMayAccess(string $method, string $path): bool
    {
        if (preg_match('#^admin/shop/(?:coupons|discounts)(?:/(?:create|[0-9]+)(?:/edit)?)?$#', $path)) {
            return in_array($method, ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE'], true);
        }
        return in_array($method, ['GET', 'HEAD'], true)
            && (preg_match('#^admin/shop/payments(?:/[0-9]+)?$#', $path) || $path === 'admin/shop/purchases');
    }

    public static function synchronize(array $identity): User
    {
        abort_unless(is_string($identity['id'] ?? null) && preg_match('/^[a-f0-9-]{36}$/i', $identity['id']), 401);
        $role = Role::where('name', self::roleName($identity))->firstOrFail();
        return DB::transaction(function () use ($identity, $role) {
            $user = User::where('dune_account_id', $identity['id'])->lockForUpdate()->first();
            if (! $user) {
                $user = new User;
                $user->dune_account_id = $identity['id'];
                $user->email = $identity['id'].'@accounts.dunerealms.invalid';
                $user->password = bin2hex(random_bytes(32));
                $user->password_changed_at = now();
                $user->email_verified_at = now();
            }
            $user->name = $identity['username'];
            $user->role_id = $role->id;
            if ($user->isDirty()) $user->save();
            return $user;
        });
    }

    public static function login($request)
    {
        if (! $request->has('code')) {
            $state = bin2hex(random_bytes(32));
            $request->session()->put('dune_state', ['value' => $state, 'expires' => time() + 300]);
            $request->session()->put('dune_destination', $request->query('admin') === '1' ? '/store/admin/shop/packages' : '/store/shop');
            return redirect('https://cp.dunerealms.com/sso/authorize?client=shop&state='.$state)
                ->header('Cache-Control', 'no-store')->header('Referrer-Policy', 'no-referrer');
        }
        $expected = $request->session()->pull('dune_state');
        abort_unless(is_array($expected) && ($expected['expires'] ?? 0) >= time()
            && is_string($request->query('state')) && hash_equals($expected['value'], $request->query('state')), 403, 'Invalid sign-in state.');
        $identity = self::bridge('redeem', ['client' => 'shop', 'code' => $request->query('code')]);
        $user = self::synchronize($identity);
        Auth::login($user, false);
        $request->session()->regenerate();
        $request->session()->put('dune_account_id', $identity['id']);
        $request->session()->put('dune_session_ref', $identity['sessionRef']);
        $destination = $request->session()->pull('dune_destination', '/store/shop');
        if ($destination === '/store/admin/shop/packages' && self::roleName($identity) === 'DuneRealms Shop Support') $destination = '/store/admin/shop/payments';
        return redirect('https://cp.dunerealms.com'.$destination)->header('Cache-Control', 'no-store')->header('Referrer-Policy', 'no-referrer');
    }

    public function handle($request, \Closure $next)
    {
        $path = $request->path();
        if ($path === 'user/login') {
            abort_unless($request->isMethod('GET'), 404);
            return redirect('https://cp.dunerealms.com/store/dune-login');
        }
        // Account credentials, game linkage and native role administration stay central.
        if (preg_match('#^(?:user/(?:register|password|verify|email)|profile/(?:password|email|delete)|api/)#', $path)) abort(403, 'Manage your account in the Account Center.');
        if (str_starts_with($path, 'profile') && ! $request->isMethod('GET') && $path !== 'profile/theme') abort(403, 'Manage your account in the Account Center.');
        if (! Auth::check() && $path === 'shop' && preg_match('/(?:^|;\s*)__Host-dune_session=/', $request->server('HTTP_COOKIE', ''))) {
            return redirect('https://cp.dunerealms.com/store/dune-login');
        }
        if (Auth::check()) {
            $ref = $request->session()->get('dune_session_ref');
            if (! is_string($ref) || ! preg_match('/^[a-f0-9]{64}$/', $ref)) {
                Auth::logout();
                $request->session()->invalidate();
                return redirect('https://cp.dunerealms.com/store/dune-login');
            }
            $identity = self::bridge('identity', ['sessionRef' => $ref]);
            abort_unless(($identity['id'] ?? null) === $request->session()->get('dune_account_id'), 401);
            $request->attributes->set('dune_identity', $identity);
            Auth::setUser(self::synchronize($identity));
        }
        if ($path === 'admin') return redirect('https://cp.dunerealms.com/store/admin/shop/'.(Auth::user()?->role->name === 'DuneRealms Shop Support' ? 'payments' : 'packages'));
        if (str_starts_with($path, 'admin/') && ! str_starts_with($path, 'admin/shop/')) abort(403);
        if (str_starts_with($path, 'admin/shop/') && Auth::user()?->role->name === 'DuneRealms Shop Support') {
            abort_unless(self::supportMayAccess($request->method(), $path), 403, 'Shop support permits purchase lookup and coupon/discount management only.');
        }
        return $next($request);
    }
}
