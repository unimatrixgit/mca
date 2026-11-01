<?php
if(PHP_SAPI!=='cli')exit(1);
$raw=stream_get_contents(STDIN,65537);if(strlen($raw)>65536)throw new Exception('Profile snapshot too large');$a=json_decode($raw,true);
if(!is_array($a)||!preg_match('/^[a-f0-9-]{36}$/i',$a['id']??'')||!is_string($a['username']??null)||strlen($a['username'])>64||!is_array($a['publicProfile']??null))throw new Exception('Verified public profile required');
$profile=$a['publicProfile'];$bio=(string)($profile['bio']??'');$info=(string)($profile['publicInfo']??'');if(strlen($bio)>4000||strlen($info)>2000)throw new Exception('Public profile text too long');
$domains=['website'=>[],'discord'=>['discord.com','discord.gg'],'youtube'=>['youtube.com','youtu.be'],'twitch'=>['twitch.tv'],'twitter'=>['twitter.com','x.com'],'facebook'=>['facebook.com','fb.com'],'github'=>['github.com'],'instagram'=>['instagram.com'],'tiktok'=>['tiktok.com']];$socials=[];
foreach($domains as$key=>$hosts){$value=$profile['socials'][$key]??'';if($value==='')continue;if(!is_string($value)||strlen($value)>500||!filter_var($value,FILTER_VALIDATE_URL))throw new Exception('Invalid public social URL');$u=parse_url($value);$host=strtolower($u['host']??'');if(($u['scheme']??'')!=='https'||isset($u['user'])||isset($u['pass'])||(isset($u['port'])&&$u['port']!==443)||($hosts&&!array_filter($hosts,fn($d)=>$host===$d||str_ends_with($host,'.'.$d))))throw new Exception('Wrong social provider URL');$socials[$key]=$value;}
$avatar=['url'=>'https://cp.dunerealms.com/api/avatars/fallback','mode'=>'image'];$candidate=$profile['avatar']??[];
if(is_array($candidate)&&is_string($candidate['url']??null)&&preg_match('~^https://cp\.dunerealms\.com/api/avatars/(?:fallback|(?:site|minecraft)/[a-f0-9-]{36})$~i',$candidate['url']))$avatar=['url'=>$candidate['url'],'mode'=>($candidate['mode']??'')==='skin'?'skin':'image'];
$git=null;$g=$profile['git']??null;
if(is_array($g)&&is_string($g['login']??null)&&preg_match('/^[A-Za-z0-9_.-]{1,100}$/',$g['login'])&&in_array($g['profileUrl']??'', ['https://www.dunerealms.com/git/'.rawurlencode($g['login']),'https://dunerealms.com/git/'.rawurlencode($g['login'])],true)){
 $count=max(0,(int)($g['ownedRepoCount']??0));$git=['login'=>$g['login'],'profileUrl'=>$g['profileUrl'],'ownedRepoCount'=>$count,'repositoryOwner'=>$count>0];$socials['dunehub']=$git['profileUrl'];
}
$c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);$p=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
$p->exec('CREATE TABLE IF NOT EXISTS dune_public_profiles(website_id VARCHAR(36) PRIMARY KEY,username VARCHAR(64) NOT NULL,display_name VARCHAR(80) NOT NULL,bio TEXT NOT NULL,public_info TEXT NOT NULL,socials_json TEXT NOT NULL,avatar_json TEXT NOT NULL,updated_at DATETIME NOT NULL) ENGINE=InnoDB');
$p->exec('CREATE TABLE IF NOT EXISTS dune_public_git(website_id VARCHAR(36) PRIMARY KEY,git_json TEXT NOT NULL) ENGINE=InnoDB');
$p->beginTransaction();try{
 $q=$p->prepare('INSERT INTO dune_public_git(website_id,git_json) VALUES(?,?) ON DUPLICATE KEY UPDATE git_json=VALUES(git_json)');$q->execute([$a['id'],json_encode($git)]);
 $q=$p->prepare('INSERT INTO dune_public_profiles(website_id,username,display_name,bio,public_info,socials_json,avatar_json,updated_at) VALUES(?,?,?,?,?,?,?,NOW()) ON DUPLICATE KEY UPDATE username=VALUES(username),display_name=VALUES(display_name),bio=VALUES(bio),public_info=VALUES(public_info),socials_json=VALUES(socials_json),avatar_json=VALUES(avatar_json),updated_at=NOW()');$q->execute([$a['id'],$a['username'],(function_exists('mb_substr')?mb_substr((string)($a['displayName']??$a['username']),0,80):substr((string)($a['displayName']??$a['username']),0,80)),$bio,$info,json_encode($socials),json_encode($avatar)]);
 $q=$p->prepare('UPDATE drforum_users u JOIN drforum_dune_accounts a ON a.uid=u.uid SET u.website=? WHERE a.website_id=?');$q->execute([$socials['website']??'',$a['id']]);
 if($avatar['mode']==='image'){$q=$p->prepare("UPDATE drforum_users u JOIN drforum_dune_accounts a ON a.uid=u.uid SET u.avatar=?,u.avatardimensions='64|64',u.avatartype='remote' WHERE a.website_id=?");$q->execute([$avatar['url'],$a['id']]);}
 $p->commit();echo "Verified public profile mirrored.\n";
}catch(Throwable$e){$p->rollBack();throw$e;}
