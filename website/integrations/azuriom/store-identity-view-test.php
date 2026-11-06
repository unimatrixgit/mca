<?php
// Render the real Blade fragment with installed Laravel, an isolated layout/cache and fixed route stub.
if(PHP_SAPI!=='cli')throw new RuntimeException('CLI only');
function route($name,$parameters=[],$absolute=true){if($name!=='shop.profile')throw new RuntimeException('Unexpected route');return'https://cp.dunerealms.com/store/shop/profile';}
$root=$argv[1]??'/home/dunerealms/apps/azuriom';$template=$argv[2]??__DIR__.'/views/dune-account.blade.php';
require $root.'/vendor/autoload.php';
$qa=sys_get_temp_dir().'/dune-store338-view-'.bin2hex(random_bytes(8));mkdir($qa,0700);mkdir($qa.'/layouts',0700);mkdir($qa.'/cache',0700);
copy($template,$qa.'/dune-account.blade.php');file_put_contents($qa.'/layouts/app.blade.php','<!doctype html><html><head><title>@yield("title")</title></head><body>@yield("content")</body></html>');
$files=new Illuminate\Filesystem\Filesystem;$container=new Illuminate\Container\Container;Illuminate\Container\Container::setInstance($container);
$resolver=new Illuminate\View\Engines\EngineResolver;$compiler=new Illuminate\View\Compilers\BladeCompiler($files,$qa.'/cache');
$resolver->register('blade',fn()=>new Illuminate\View\Engines\CompilerEngine($compiler));$resolver->register('php',fn()=>new Illuminate\View\Engines\PhpEngine($files));
$factory=new Illuminate\View\Factory($resolver,new Illuminate\View\FileViewFinder($files,[$qa]),new Illuminate\Events\Dispatcher($container));$factory->setContainer($container);$factory->share('__env',$factory);$container->instance('view',$factory);
$html=$factory->make('dune-account')->render();$dom=new DOMDocument;@$dom->loadHTML($html);$xpath=new DOMXPath($dom);$checks=0;
function same($actual,$expected){global$checks;$checks++;if($actual!==$expected)throw new RuntimeException('Account view assertion '.$checks);}
same($xpath->query('//form')->length,0);same($xpath->query('//input')->length,0);same($xpath->query('//h1')->item(0)->textContent,'Your DuneRealms account');
$expected=['https://cp.dunerealms.com/#site-profile','https://cp.dunerealms.com/#security','https://cp.dunerealms.com/#game'];
foreach($expected as$url){$anchors=$xpath->query('//a[@href="'.$url.'"]');same($anchors->length,1);same($anchors->item(0)->getAttribute('target'),'_top');}
same($xpath->query('//a[@href="https://cp.dunerealms.com/store/shop/profile"]')->length,1);
same($xpath->query('//img[@src="https://www.dunerealms.com/assets/nav-account-v2.webp"]')->length,1);
same(str_contains($html,'@section'),false);same(str_contains($html,'@extends'),false);
echo json_encode(['passed'=>$checks,'realBladeCompiled'=>true,'fixtureLayoutOnly'=>true,'databaseOrApplicationBootstrap'=>false,'visibleBrowserAcceptance'=>false]).PHP_EOL;
