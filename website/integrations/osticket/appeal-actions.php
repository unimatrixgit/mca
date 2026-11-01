<?php
function dune_appeal_db(){
 $c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);
 return new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
}
function dune_ticket_appeal($ticketId){
 $q=dune_appeal_db()->prepare('SELECT a.*,b.player_name,b.source_key FROM native_ban_appeals a JOIN moderation_bans b ON b.id=a.ban_id WHERE a.ticket_id=?');$q->execute([(int)$ticketId]);return $q->fetch(PDO::FETCH_ASSOC);
}
function dune_appeal_ticket_status($state){
 $q=dune_appeal_db()->prepare('SELECT id FROM drsupport_ticket_status WHERE state=? ORDER BY id LIMIT 1');$q->execute([$state]);$id=$q->fetchColumn();return $id?TicketStatus::lookup((int)$id):null;
}
function dune_can_decide_appeal($identity,$ticket,$staff){
 return is_array($identity)&&!array_diff(['web.bans.manage','web.tickets.staff','web.tickets.appeals'],$identity['permissions']??[])&&$ticket->checkStaffPerm($staff,Ticket::PERM_CLOSE);
}
function dune_appeal_controls($ticket,$staff){
 $appeal=dune_ticket_appeal($ticket->getId());if(!$appeal)return;
 $identity=dune_ticket_identity();if(!in_array($appeal['status'],['approved','denied','approving'],true))$appeal['status']=$ticket->isClosed()?'closed':'pending';$esc=fn($v)=>htmlspecialchars((string)$v,ENT_QUOTES,'UTF-8');
 echo '<section class="dune-appeal-actions" style="margin:16px 0;padding:18px;border:1px solid #b99b52;border-radius:8px;background:#142e26"><h2>Ban appeal · '.$esc($appeal['player_name']).'</h2><p>Decision: '.$esc($appeal['status']).'. Closing a ticket alone does not approve an appeal or remove a ban.</p>';
 if(dune_can_decide_appeal($identity,$ticket,$staff))foreach(['approve'=>'Approve appeal & remove ban','deny'=>'Deny appeal','reopen'=>'Reopen appeal']as$action=>$label)echo '<a class="button dune-appeal-choice dune-appeal-'.$action.'" data-dune-action="'.$action.'" style="display:inline-block;margin:4px 10px 4px 0" href="/scp/appeal-action.php?id='.(int)$ticket->getId().'&amp;action='.$action.'">'.$label.'</a>';
 echo '</section>';
 dune_appeal_evidence($appeal,$identity);
}

function dune_appeal_evidence($appeal,$identity){
 if(!is_array($identity)||array_diff(['web.menu.bans','web.bans.view'],$identity['permissions']??[]))return;
 $esc=fn($v)=>htmlspecialchars((string)$v,ENT_QUOTES,'UTF-8');
 echo '<section class="dune-appeal-evidence" style="margin:16px 0;padding:18px;border:1px solid #b99b52;border-radius:8px;background:#142e26"><h3>Private ban evidence</h3>';
 $archive='https://cp.dunerealms.com/?banId='.rawurlencode($appeal['ban_id']).'#ban-evidence';
 echo '<p>Linked to the original ban. Staff access is required for all downloads. <a href="'.$esc($archive).'" target="_blank" rel="noopener">Open evidence archive</a></p>';
 try{$q=dune_appeal_db()->prepare('SELECT id,label,actor_name,notes,link_url,file_name,file_size,mime,created_at FROM moderation_evidence WHERE ban_id=? ORDER BY created_at DESC,id DESC LIMIT 200');$q->execute([$appeal['ban_id']]);$rows=$q->fetchAll(PDO::FETCH_ASSOC);
 if(!$rows)echo '<p>No evidence has been attached to this ban yet.</p>';
 foreach($rows as $row){echo '<article style="padding:12px;margin:10px 0;border:1px solid #426458;border-radius:6px"><h4>'.$esc($row['label']).'</h4><p>'.$esc($row['actor_name']).' · '.$esc(gmdate('Y-m-d H:i',(int)floor($row['created_at']/1000))).' UTC</p>';
 if($row['notes'])echo '<p style="white-space:pre-wrap">'.$esc($row['notes']).'</p>';
 if($row['link_url']&&parse_url($row['link_url'],PHP_URL_SCHEME)==='https')echo '<p><a href="'.$esc($row['link_url']).'" target="_blank" rel="noopener noreferrer">Open source</a></p>';
 if($row['file_name'])dune_evidence_media($row['mime'],'https://cp.dunerealms.com/api/ban-evidence/file?id='.rawurlencode($row['id']),$row['label']);
 if($row['file_name'])echo '<p><a href="https://cp.dunerealms.com/api/ban-evidence/file?id='.rawurlencode($row['id']).'">Download '.$esc($row['file_name']).' · '.(int)ceil($row['file_size']/1024).' KB</a></p>';
 echo '</article>';}
 dune_ticket_evidence_media($appeal['ban_id']);
 }catch(Throwable $e){echo '<p>Evidence is temporarily unavailable. Use the secure archive link above.</p>';}
 echo '</section>';
}

function dune_evidence_media($mime,$url,$title){
 $esc=fn($v)=>htmlspecialchars((string)$v,ENT_QUOTES,'UTF-8');$src=$esc($url.'&inline=1');$style='display:block;width:100%;max-width:760px;max-height:440px;object-fit:contain;background:#091b17;border:1px solid #466453;border-radius:10px;margin:14px 0';
 if(in_array($mime,['image/png','image/jpeg','image/webp','image/gif'],true))echo '<img loading="lazy" src="'.$src.'" alt="'.$esc($title).'" style="'.$style.'">';
 elseif(in_array($mime,['video/mp4','video/webm'],true))echo '<video controls preload="metadata" playsinline src="'.$src.'" style="'.$style.'">Use the download link if your browser cannot play this clip.</video>';
}
function dune_ticket_evidence_media($banId){
 $q=dune_appeal_db()->prepare("SELECT a.id,n.ticket_id,f.name,f.type,f.size,e.poster,e.staff_id FROM native_ban_appeals n JOIN drsupport_thread t ON t.object_id=n.ticket_id AND t.object_type='T' JOIN drsupport_thread_entry e ON e.thread_id=t.id JOIN drsupport_attachment a ON a.object_id=e.id AND a.type='H' JOIN drsupport_file f ON f.id=a.file_id WHERE n.ban_id=? ORDER BY e.created DESC,a.id DESC LIMIT 200");$q->execute([$banId]);$rows=$q->fetchAll(PDO::FETCH_ASSOC);if(!$rows)return;$esc=fn($v)=>htmlspecialchars((string)$v,ENT_QUOTES,'UTF-8');echo '<h3>Player &amp; staff appeal attachments</h3>';
 foreach($rows as $row){$url='https://cp.dunerealms.com/api/ban-evidence/file?nativeId='.(int)$row['id'].'&banId='.rawurlencode($banId);echo '<article style="padding:12px;margin:10px 0;border:1px solid #426458;border-radius:8px"><h4>'.$esc($row['name']).'</h4><p>'.$esc($row['poster']).' · '.($row['staff_id']?'Staff':'Player').' · Ticket #'.(int)$row['ticket_id'].'</p>';dune_evidence_media($row['type'],$url,$row['name']);echo '<p><a href="'.$esc($url).'">Download '.$esc($row['name']).' · '.(int)ceil($row['size']/1024).' KB</a></p></article>';}
}
