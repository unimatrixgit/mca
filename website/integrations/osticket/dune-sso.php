<?php
require '/home/dunerealms/apps/integrations/bridge.php';
require __DIR__.'/main.inc.php';
require_once __DIR__.'/intake-navigation.php';
if(!isset($_GET['code'])){
 $_SESSION['dune_support_admin_requested']=($_GET['admin']??'')==='1';
 $_SESSION['dune_dev_requested']=($_GET['dev']??'')==='1';
 $_SESSION['dune_staff_requested']=($_GET['staff']??'')==='1';
 unset($_SESSION['dune_appeal_requested']);
 if(array_key_exists('appeal',$_GET))$_SESSION['dune_appeal_requested']=is_string($_GET['appeal'])&&preg_match('/^[a-f0-9-]{36}$/i',$_GET['appeal'])?$_GET['appeal']:'';
 dune_remember_intake_path($_SESSION,$_GET,time(),$_SERVER['REQUEST_METHOD']??'GET');
 dune_begin_sso('osticket');
}
$identity=dune_finish_sso('osticket');
// A newly verified central sign-in must issue fresh native idle-time tokens.
unset($_SESSION[':token']['client'],$_SESSION['TTD']);
if(!empty($_SESSION['dune_staff_requested']))unset($_SESSION[':token']['staff']);
$backend=UserAuthenticationBackend::getBackend('dunerealms-client');
if(!$backend||!($user=$backend->signOn())||!$backend->login($user,$backend)){http_response_code(503);exit('Support sign-in unavailable. Contact the operator.');}
$intakeReturn=dune_take_intake_path($_SESSION,time());
if(!empty($_SESSION['dune_staff_requested'])&&in_array('web.tickets.staff',$identity['permissions'],true)){
 if(!empty($_SESSION['dune_support_admin_requested'])&&!in_array('web.tickets.admin',$identity['permissions'],true)){unset($_SESSION['dune_support_admin_requested']);http_response_code(403);exit('Support administration permission required.');}
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
 $adminRequested=!empty($_SESSION['dune_support_admin_requested']);unset($_SESSION['dune_support_admin_requested']);header('Location: '.($adminRequested?'/scp/settings.php':'/scp/'));exit;
}
if(isset($_SESSION['dune_appeal_requested'])){$id=$_SESSION['dune_appeal_requested'];unset($_SESSION['dune_appeal_requested']);header('Location: /appeal.php?banId='.rawurlencode($id));exit;}
if(!empty($_SESSION['dune_dev_requested'])){unset($_SESSION['dune_dev_requested']);header('Location: /dev-tasks.php');exit;}
if($intakeReturn!==null){header('Location: '.$intakeReturn,true,303);exit;}
header('Location: /tickets.php');exit;
