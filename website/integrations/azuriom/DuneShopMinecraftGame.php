<?php
namespace Azuriom\Games\Minecraft;
class DuneShopMinecraftGame extends MinecraftOfflineGame
{
 public function getSupportedServers(): array {return ['mc-azlink'=>\Azuriom\Games\Minecraft\Servers\DuneShopAzLink::class];}
}
