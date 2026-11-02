<?php
namespace Azuriom\Games\Minecraft\Servers;
use Azuriom\Models\User;
use Azuriom\Support\DuneShopGameIdentity;
class DuneShopAzLink extends AzLink
{
 public function sendCommands(array $commands,User $user,bool $needConnected=false): void {
  $recipient=['uuid'=>$user->getAttribute('dune_delivery_uuid'),'name'=>$user->getAttribute('dune_delivery_name')];
  $rows=[];foreach($commands as $command)$rows[]=['command'=>DuneShopGameIdentity::expand($command,$recipient),'user_id'=>$user->id,'need_online'=>$needConnected,'dune_minecraft_uuid'=>$recipient['uuid'],'dune_minecraft_name'=>$recipient['name'],'created_at'=>now(),'updated_at'=>now()];
  // Native AzLink queue; polling only, no new inbound game-server listener.
  if($rows)\Illuminate\Support\Facades\DB::table('server_commands')->insert(array_map(fn($r)=>['server_id'=>$this->server->id]+$r,$rows));
 }
}
