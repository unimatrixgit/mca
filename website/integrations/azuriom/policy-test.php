<?php
require __DIR__.'/DuneShopAccount.php';
use Azuriom\Http\Middleware\DuneShopAccount as Policy;
function check($ok) { if (!$ok) throw new RuntimeException('Shop policy regression'); }
check(Policy::roleName(['ranks'=>['user'],'permissions'=>[]])==='DuneRealms Player');
check(Policy::roleName(['ranks'=>['web_news'],'permissions'=>['web.shop.support']])==='DuneRealms Shop Support');
check(Policy::roleName(['ranks'=>['owners'],'permissions'=>['web.shop.support']])==='DuneRealms Owner');
check(Policy::roleName(['ranks'=>['web_sales'],'permissions'=>['web.tickets.sales','web.shop.support']])==='DuneRealms Sales');
foreach (['GET','HEAD'] as $method) { check(Policy::supportMayAccess($method,'admin/shop/payments'));check(Policy::supportMayAccess($method,'admin/shop/payments/12'));check(Policy::supportMayAccess($method,'admin/shop/purchases')); }
foreach (['POST','PUT','PATCH','DELETE'] as $method) { check(!Policy::supportMayAccess($method,'admin/shop/payments'));check(!Policy::supportMayAccess($method,'admin/shop/payments/12')); }
foreach (['coupons','discounts'] as $area) foreach (['GET','POST','PUT','PATCH','DELETE'] as $method) { check(Policy::supportMayAccess($method,'admin/shop/'.$area));check(Policy::supportMayAccess($method,'admin/shop/'.$area.'/12')); }
foreach (['packages','gateways','settings','giftcards','payments/create','subscriptions','stats'] as $path) foreach (['GET','POST'] as $method) check(!Policy::supportMayAccess($method,'admin/shop/'.$path));
echo "Shop role and route policy checks passed\n";
