<?php
require '/home/dunerealms/apps/integrations/bridge.php';
require __DIR__.'/main.inc.php';
if(!isset($_GET['code'])){if(($_GET['dev']??'')==='1')$_SESSION['dune_dev_requested']=true;$_SESSION['dune_staff_requested']=($_GET['staff']??'')==='1';if(array_key_exists('appeal',$_GET))$_SESSION['dune_appeal_requested']=preg_match('/^[a-f0-9-]{36}$/i',$_GET['appeal'])?$_GET['appeal']:'';dune_begin_sso('osticket');}
$identity=dune_finish_sso('osticket');
// A newly verified central sign-in must issue fresh native idle-time tokens.
unset($_SESSION[':token']['client'],$_SESSION['TTD']);
if(!empty($_SESSION['dune_staff_requested']))unset($_SESSION[':token']['staff']);
$backend=UserAuthenticationBackend::getBackend('dunerealms-client');
if(!$backend||!($user=$backend->signOn())||!$backend->login($user,$backend)){http_response_code(503);exit('Support sign-in unavailable. Contact the operator.');}
if(!empty($_SESSION['dune_staff_requested'])&&in_array('web.tickets.staff',$identity['permissions'],true)){
 unset($_SESSION['dune_staff_requested']);$name=str_replace('-','',$identity['id']);
 $role=Role::lookup(['name'=>'DuneRealms support']);
 if(!$role){http_response_code(503);exit('Support staff role is unavailable.');}
 $staff=Staff::lookup(['username'=>$name]);
 if(!$staff){$staff=Staff::create(['username'=>$name,'firstname'=>substr($identity['username'],0,32),'lastname'=>'','email'=>$identity['id'].'@accounts.dunerealms.invalid','dept_id'=>1,'role_id'=>$role->getId(),'backend'=>'dunerealms-staff','passwd'=>null,'signature'=>'','phone'=>'','mobile'=>'','isactive'=>1,'isadmin'=>0,'created'=>SqlFunction::NOW(),'updated'=>SqlFunction::NOW()]);if(!$staff->save()){http_response_code(503);exit('Could not provision support staff.');}}
 $staff->isadmin=in_array('web.tickets.admin',$identity['permissions'],true)?1:0;$staff->save();
 require_once __DIR__.'/department-access.php';
 require_once __DIR__.'/staff-scope.php';
 dune_sync_ticket_departments($staff,$identity,$role);
 $_SESSION['dune_staff_scope']=dune_ticket_staff_scope($identity);

 $staffBackend=StaffAuthenticationBackend::getBackend('dunerealms-staff');
 $session=StaffSession::lookup($name);
 if(!$staffBackend||!$session||!$staffBackend->login($session,$staffBackend)){http_response_code(503);exit('Support staff sign-in unavailable.');}
 header('Location: /scp/');exit;
}
if(isset($_SESSION['dune_appeal_requested'])){$id=$_SESSION['dune_appeal_requested'];unset($_SESSION['dune_appeal_requested']);header('Location: /appeal.php?banId='.rawurlencode($id));exit;}
if(!empty($_SESSION['dune_dev_requested'])){unset($_SESSION['dune_dev_requested']);header('Location: /dev-tasks.php');exit;}
header('Location: /tickets.php');exit;
