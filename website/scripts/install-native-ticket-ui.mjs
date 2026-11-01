import {readFileSync,writeFileSync} from 'node:fs';
const root=process.argv[2];if(!root)throw Error('Provide the native osTicket root');
const path=root+'/include/staff/ticket-view.inc.php';let html=readFileSync(path,'utf8');
const start="                <tr>\n                    <th width=\"100\"><?php echo __('Assigned To');";
const end="                <?php\n                } else { ?>";
if(!html.includes('Assigned staff')){const a=html.indexOf(start),b=html.indexOf(end,a);if(a<0||b<a)throw Error('Native assignment template changed; review before patching');html=html.slice(0,a)+"                <?php foreach(['teams'=>'Assigned team','agents'=>'Assigned staff'] as $kind=>$label): $assignee=$kind==='teams'?$ticket->getTeam():$ticket->getStaff(); ?>\n                <tr><th width=\"130\"><?php echo $label; ?>:</th><td>\n                <?php if($role->hasPerm(Ticket::PERM_ASSIGN)): ?><a class=\"inline-edit\" href=\"#tickets/<?php echo $ticket->getId(); ?>/assign/<?php echo $kind; ?>\"><?php endif; ?>\n                <?php echo $assignee?Format::htmlchars($assignee->getName()):'<span class=\"faded\">Unassigned</span>'; ?>\n                <?php if($role->hasPerm(Ticket::PERM_ASSIGN)): ?></a><?php endif; ?>\n                </td></tr><?php endforeach; ?>\n"+html.slice(b);writeFileSync(path,html);}

