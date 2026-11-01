<?php
// Email-only intake controls. Existing ticket replies are processed before Ticket::create.
function dune_email_intake_allowed($origin,$vars,&$errors){
 if(strcasecmp((string)$origin,'email')!==0)return true;
 $sender=trim((string)($vars['email']??''));
 if(!filter_var($sender,FILTER_VALIDATE_EMAIL)){$errors['err']='A valid sender address is required.';return false;}
 if($user=TicketUser::lookupByEmail($sender)){
  if($user->getNumOpenTickets()>0){$errors['err']='One open email ticket is allowed per sender. Reply to your existing ticket.';return false;}
 }
 return true;
}

