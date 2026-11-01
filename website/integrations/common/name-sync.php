<?php
// Called only with a central identity redeemed through the authenticated SSO bridge.
function dune_sync_native_names($identity) {
 if(!preg_match('/^[a-f0-9-]{36}$/i',$identity['id']??'')||!preg_match('/^[a-z0-9_]{3,32}$/',$identity['username']??''))throw new RuntimeException('Invalid central identity');
 $c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);
 mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
 $sql=new mysqli($c['host'],$c['username'],$c['password'],$c['database']);
 $id=$identity['id'];$name=$identity['username'];$email=$id.'@accounts.dunerealms.invalid';
 $sql->begin_transaction();
 try{
  $q=$sql->prepare('SELECT uid FROM drforum_dune_accounts WHERE website_id=?');$q->bind_param('s',$id);$q->execute();$mapping=$q->get_result()->fetch_assoc();
  if($mapping){
   $uid=(int)$mapping['uid'];$q=$sql->prepare('SELECT uid FROM drforum_users WHERE username=? AND uid<>?');$q->bind_param('si',$name,$uid);$q->execute();if($q->get_result()->num_rows)throw new RuntimeException('Native username conflict');
   $q=$sql->prepare('UPDATE drforum_users SET username=? WHERE uid=? AND username<>?');$q->bind_param('sis',$name,$uid,$name);$q->execute();
  }
  $q=$sql->prepare('UPDATE drsupport_user u JOIN drsupport_user_email e ON e.id=u.default_email_id SET u.name=? WHERE e.address=? AND u.name<>?');$q->bind_param('sss',$name,$email,$name);$q->execute();
  $q=$sql->prepare('UPDATE drsupport_user_account a JOIN drsupport_user_email e ON e.user_id=a.user_id SET a.username=? WHERE e.address=? AND a.username<>?');$q->bind_param('sss',$name,$email,$name);$q->execute();
  // Agent authentication keeps its immutable internal identifier; the public staff name follows CP.
  $q=$sql->prepare('UPDATE drsupport_staff SET firstname=?,lastname=? WHERE email=? AND (firstname<>? OR lastname<>?)');$empty='';$q->bind_param('sssss',$name,$empty,$email,$name,$empty);$q->execute();
  $sql->commit();
 }catch(Throwable $e){$sql->rollback();throw $e;}finally{$sql->close();}
}
