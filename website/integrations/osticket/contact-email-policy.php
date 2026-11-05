<?php
// Notification addresses never replace the immutable DuneRealms identity alias.
function dune_contact_address($value) {
 if($value===null)return null;
 if(!is_string($value)||strlen($value)>254||preg_match('/[\r\n\x00]/',$value)||!filter_var($value,FILTER_VALIDATE_EMAIL)||str_ends_with(strtolower($value),'.invalid'))throw new RuntimeException('Invalid contact address');
 return strtolower($value);
}
function dune_contact_sync(PDO $db,string $websiteId,?string $address,int $version):void {
 if(!preg_match('/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/',$websiteId)||$version<0)throw new RuntimeException('Invalid account');
 $address=dune_contact_address($address);$alias=$websiteId.'@accounts.dunerealms.invalid';
 $mysql=$db->getAttribute(PDO::ATTR_DRIVER_NAME)==='mysql';
 $db->beginTransaction();try{
  $q=$db->prepare($mysql?'INSERT IGNORE INTO dune_contact_projection(website_id,version) VALUES(?,-1)':'INSERT OR IGNORE INTO dune_contact_projection(website_id,version) VALUES(?,-1)');$q->execute([$websiteId]);
  $q=$db->prepare('SELECT * FROM dune_contact_projection WHERE website_id=?'.($mysql?' FOR UPDATE':''));$q->execute([$websiteId]);$previous=$q->fetch(PDO::FETCH_ASSOC);
  if((int)$previous['version']>$version){$db->commit();return;}
  if((int)$previous['version']===$version&&$previous['address']!==$address)throw new RuntimeException('Conflicting contact revision');
  $q=$db->prepare('SELECT id,user_id FROM drsupport_user_email WHERE address=?');$q->execute([$alias]);$identity=$q->fetch(PDO::FETCH_ASSOC);
  if(!$identity)throw new RuntimeException('Support account is not provisioned');
  $targetId=(int)$identity['id'];
  if($address!==null){
   $q=$db->prepare('SELECT id,user_id FROM drsupport_user_email WHERE address=?');$q->execute([$address]);$target=$q->fetch(PDO::FETCH_ASSOC);
   if($target&&(int)$target['user_id']!==(int)$identity['user_id'])throw new RuntimeException('Contact address unavailable');
   if(!$target){$q=$db->prepare('INSERT INTO drsupport_user_email(user_id,address) VALUES(?,?)');$q->execute([$identity['user_id'],$address]);$targetId=(int)$db->lastInsertId();}else $targetId=(int)$target['id'];
  }
  $q=$db->prepare('UPDATE drsupport_user SET default_email_id=? WHERE id=?');$q->execute([$targetId,$identity['user_id']]);
  // Remove only the former address created by this feature. Never touch aliases,
  // other identities, ticket rows, threads, paid receipts or audit history.
  if($previous['address']!==null&&$previous['address']!==$address){$q=$db->prepare('DELETE FROM drsupport_user_email WHERE user_id=? AND address=? AND id<>?');$q->execute([$identity['user_id'],$previous['address'],$identity['id']]);}
  $q=$db->prepare('UPDATE dune_contact_projection SET address=?,version=? WHERE website_id=?');$q->execute([$address,$version,$websiteId]);
  $db->commit();
 }catch(Throwable $e){if($db->inTransaction())$db->rollBack();throw $e;}
}
