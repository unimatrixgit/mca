<?php
namespace Azuriom\Providers;

use Azuriom\Http\Middleware\DuneShopAccount;
use Illuminate\Support\Facades\Route;
use Illuminate\Support\ServiceProvider;

class DuneShopServiceProvider extends ServiceProvider
{
    public function boot(): void
    {
        $this->app->booted(function(){ $this->app->instance('game',new \Azuriom\Games\Minecraft\DuneShopMinecraftGame()); \Azuriom\Support\DuneShopRankHooks::register(); \Azuriom\Support\DuneSupporterHooks::register(); });
        $this->app->make(\Illuminate\Contracts\Http\Kernel::class)
            ->appendMiddlewareToGroup('web', DuneShopAccount::class);
        Route::middleware('web')->get('/dune-servers', fn () => \Azuriom\Support\DuneShopGameServers::render());
        Route::middleware('web')->get('/dune-login', fn (\Illuminate\Http\Request $request) => DuneShopAccount::login($request));

    }
}
