<?php
// Private read-only ticket feed. Called only by the CP notification worker.
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
ini_set('display_errors','0');
try{
 $v=json_decode(stream_get_contents(STDIN,4096),true,8,JSON_THROW_ON_ERROR);$after=isset($v['after'])?(int)$v['after']:null;
 $c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);
 $db=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
 $max=(int)$db->query('SELECT COALESCE(MAX(ticket_id),0) FROM drsupport_ticket')->fetchColumn();
 if($after===null){echo json_encode(['ok'=>true,'latest'=>$max,'tickets'=>[]]);exit;}
 $q=$db->prepare('SELECT t.ticket_id AS id,t.number,t.user_id,t.staff_id,t.team_id,t.dept_id,d.name AS department,c.subject FROM drsupport_ticket t JOIN drsupport_department d ON d.id=t.dept_id LEFT JOIN drsupport_ticket__cdata c ON c.ticket_id=t.ticket_id WHERE t.ticket_id>? ORDER BY t.ticket_id LIMIT 25');$q->execute([max(0,$after)]);$tickets=$q->fetchAll();
 foreach($tickets as &$t){$q=$db->prepare('SELECT address FROM drsupport_user_email WHERE user_id=? ORDER BY id');$q->execute([$t['user_id']]);$t['ownerEmails']=$q->fetchAll(PDO::FETCH_COLUMN);
 $q=$db->prepare('SELECT DISTINCT s.email FROM drsupport_staff s WHERE s.isactive=1 AND (s.staff_id=? OR (?=0 AND s.staff_id IN (SELECT staff_id FROM drsupport_team_member WHERE team_id=?)))');$q->execute([$t['staff_id'],$t['staff_id'],$t['team_id']]);$t['staffEmails']=$q->fetchAll(PDO::FETCH_COLUMN);unset($t['user_id'],$t['staff_id'],$t['team_id'],$t['dept_id']);}
 echo json_encode(['ok'=>true,'latest'=>$max,'tickets'=>$tickets]);
}catch(Throwable $e){fwrite(STDOUT,'{"error":"Ticket notification feed unavailable"}');exit(1);}
