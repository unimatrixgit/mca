<?php
require 'staff.inc.php';
require_once INCLUDE_DIR.'class.ticket.php';
require_once dirname(__DIR__).'/appeal-actions.php';
header('Cache-Control: no-store');
$ticket=Ticket::lookup((int)($_POST['id']??$_GET['id']??0));$identity=dune_ticket_identity();
if(!$ticket||!$ticket->checkStaffPerm($thisstaff)||!dune_can_decide_appeal($identity,$ticket,$thisstaff)){http_response_code(403);exit('Appeal decision access denied.');}
$appeal=dune_ticket_appeal($ticket->getId());$action=$_POST['action']??$_GET['action']??'';
if(!$appeal||!in_array($action,['approve','deny','reopen'],true)){http_response_code(404);exit('Linked appeal or action not found.');}
$error='';$reason=trim($_POST['reason']??'');
if($_SERVER['REQUEST_METHOD']==='POST'){
 if(strlen($reason)<5||strlen($reason)>500||preg_match('/[\x00-\x1f]/',$reason))$error='Enter a single-line reason of 5–500 characters.';
 else{
  $result=dune_bridge('ban',['sessionRef'=>$identity['sessionRef'],'banId'=>$appeal['ban_id'],'ticketId'=>$ticket->getId(),'operation'=>'decision','action'=>$action,'reason'=>$reason]);
  if(!$result||empty($result['ok']))$error='The decision could not be confirmed. Refresh the appeal register before retrying. Approval is never recorded without confirmed ban removal.';
  else{
   $status=dune_appeal_ticket_status($action==='reopen'?'open':'closed');$errors=[];
   if(!$status||!$ticket->setStatus($status,'Appeal '.$result['status'].': '.$reason,$errors))$error='The appeal decision was saved, but ticket status could not be updated. Review the ticket and register before retrying.';
   else{
    $noteErrors=[];$ticket->postNote(['title'=>'Ban appeal '.$result['status'],'note'=>Format::htmlchars($reason).(!empty($result['demo'])?' · Demonstration only; no real ban was changed.':(!empty($result['banRemoved'])?' · Game host confirmed the ban was removed.':''))],$noteErrors,'DuneRealms appeal workflow',false);
    header('Location: /scp/tickets.php?id='.$ticket->getId(),true,303);exit;
   }
  }
 }
}
$ost->setPageTitle('Ban appeal decision');require STAFFINC_DIR.'header.inc.php';$esc=fn($v)=>htmlspecialchars((string)$v,ENT_QUOTES,'UTF-8');
?>
<section style="max-width:760px;padding:24px;margin:20px auto;border:1px solid #b99b52;border-radius:10px;background:#142e26">
<h1><?= $esc(['approve'=>'Approve appeal & remove ban','deny'=>'Deny appeal','reopen'=>'Reopen appeal'][$action]) ?></h1>
<p>Ticket #<?= $esc($ticket->getNumber()) ?> · <?= $esc($appeal['player_name']) ?></p>
<p><?= $action==='approve'?'Approve removes the linked ban after confirmation from the game host, then closes the appeal.':($action==='deny'?'Deny closes the appeal and leaves the ban in place.':'Reopen returns the ticket to review. It does not reapply a previously removed ban.') ?></p>
<?php if(str_starts_with($appeal['source_key'],'demo:')):?><p><strong>Demonstration record: no real Minecraft ban will be changed.</strong></p><?php endif;?>
<?php if($error):?><p role="alert"><?= $esc($error) ?></p><?php endif;?>
<form method="post"><?php csrf_token();?><input type="hidden" name="id" value="<?= (int)$ticket->getId() ?>"><input type="hidden" name="action" value="<?= $esc($action) ?>"><label>Decision reason <input name="reason" value="<?= $esc($reason) ?>" minlength="5" maxlength="500" required style="width:100%;margin:12px 0"></label><button type="submit"><?= $esc(['approve'=>'Approve and remove ban','deny'=>'Deny appeal','reopen'=>'Reopen appeal'][$action]) ?></button> <a href="/scp/tickets.php?id=<?= (int)$ticket->getId() ?>">Cancel</a></form>
</section>
<?php require STAFFINC_DIR.'footer.inc.php';
