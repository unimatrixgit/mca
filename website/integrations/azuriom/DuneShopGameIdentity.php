<?php
namespace Azuriom\Support;
use PDO;
class DuneShopGameIdentity
{
 public static function linked(string $account): ?array {
  $c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true,8,JSON_THROW_ON_ERROR);
  $db=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_EMULATE_PREPARES=>false]);
  $q=$db->prepare('SELECT minecraft_uuid,minecraft_name FROM site_game_links WHERE user_id=?');$q->execute([$account]);$r=$q->fetch(PDO::FETCH_ASSOC);
  return $r&&self::valid($r['minecraft_uuid'],$r['minecraft_name'])?['uuid'=>strtolower($r['minecraft_uuid']),'name'=>$r['minecraft_name']]:null;
 }
 public static function valid($uuid,$name): bool {return is_string($uuid)&&preg_match('/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i',$uuid)&&is_string($name)&&preg_match('/^[.a-z0-9_]{1,32}$/i',$name);}
 public static function expand(string $command,array $recipient): string {
  if(!self::valid($recipient['uuid']??null,$recipient['name']??null))throw new \RuntimeException('A verified Minecraft identity is required.');
  $command=strtr($command,['{uuid}'=>$recipient['uuid'],'{game_id}'=>$recipient['uuid'],'{player}'=>$recipient['name'],'{name}'=>$recipient['name']]);
  if(!preg_match('/^[A-Za-z][ -~]{0,239}$/D',$command))throw new \RuntimeException('Enter one console command without a leading slash.');
  return $command;
 }
 public static function mayConfigure(array $identity): bool { $p=$identity['permissions']??[];return in_array('web.menu.servers',$p,true)&&in_array('web.servers.console',$p,true); }
}
