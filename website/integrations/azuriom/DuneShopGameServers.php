<?php
namespace Azuriom\Support;
class DuneShopGameServers
{
 public static function render(){
  abort_unless(DuneShopGameIdentity::mayConfigure(request()->attributes->get('dune_identity',[])),403);
  $rows='';foreach(\Azuriom\Models\Server::executable()->get() as $server){$data=$server->getData();$rows.='<tr><td>'.e($server->name).'</td><td>'.($data===null?'Awaiting connection':'Connected').'</td><td>AzLink · outbound HTTPS</td></tr>';}
  return response('<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Connected shop servers</title><link rel="stylesheet" href="https://www.dunerealms.com/styles.css"><body style="padding:32px;background:#10271f;color:#eee6ce"><h1>Connected shop servers</h1><p>Select these servers in a product’s command settings. Commands for online players wait for the matching Minecraft UUID. The proxy is excluded.</p><table style="width:100%;text-align:left;border-spacing:16px"><thead><tr><th>Server</th><th>Status</th><th>Connection</th></tr></thead><tbody>'.$rows.'</tbody></table><p><a href="https://cp.dunerealms.com/?center=staff&amp;staff-area=game-staff#server-operations" target="_top">Open CP network controls</a> · <a href="https://cp.dunerealms.com/store/admin/shop/packages">Back to products</a></p><p>Use {uuid} for UUID-based permission commands and {player} for the verified Minecraft name. Server keys remain private.</p></body></html>')->header('Cache-Control','private, no-store');
 }
}
