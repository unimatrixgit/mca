<?php
// Private CLI bridge only. The authenticated CP supplies the immutable reporter ID.
if (PHP_SAPI !== 'cli') { http_response_code(404); exit; }
ini_set('display_errors','0');
try {
 $raw=stream_get_contents(STDIN,16*1024*1024);$input=json_decode($raw,true,16,JSON_THROW_ON_ERROR);
 foreach(['reportId','reporterId']as$key)if(!is_string($input[$key]??null)||!preg_match('/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i',$input[$key]))throw new RuntimeException('Invalid report identity');
 $config=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true);
 $db=new PDO('mysql:host='.$config['host'].';dbname='.$config['database'].';charset=utf8mb4',$config['username'],$config['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
 $db->exec('CREATE TABLE IF NOT EXISTS dune_mail_report_tickets(report_id CHAR(36) PRIMARY KEY,reporter_id CHAR(36) NOT NULL,ticket_id INT UNSIGNED NULL,state VARCHAR(16) NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB');
 $lock='dune-mail-report:'.$input['reportId'];$q=$db->prepare('SELECT GET_LOCK(?,10)');$q->execute([$lock]);if((int)$q->fetchColumn()!==1)throw new RuntimeException('Report conversion is busy');
 $q=$db->prepare('SELECT r.*,t.number,t.dept_id,t.team_id FROM dune_mail_report_tickets r LEFT JOIN drsupport_ticket t ON t.ticket_id=r.ticket_id WHERE r.report_id=?');$q->execute([$input['reportId']]);$existing=$q->fetch();
 if($existing){if($existing['reporter_id']!==$input['reporterId'])throw new RuntimeException('Reporter binding conflict');if(!$existing['ticket_id'])throw new RuntimeException('Previous conversion needs review before retrying');echo json_encode(['ok'=>true,'ticketId'=>(int)$existing['ticket_id'],'ticketNumber'=>$existing['number'],'replay'=>true]);exit;}
 $department=$db->query("SELECT id FROM drsupport_department WHERE name='Legal' LIMIT 1")->fetchColumn();$team=$db->query("SELECT team_id FROM drsupport_team WHERE name='Legal' LIMIT 1")->fetchColumn();
 if(!$department||!$team)throw new RuntimeException('Legal routing is unavailable');
 $q=$db->prepare("SELECT topic_id FROM drsupport_help_topic WHERE dept_id=? AND topic IN ('Reported internal mail','Legal enquiries') ORDER BY CASE WHEN topic='Reported internal mail' THEN 0 ELSE 1 END");$q->execute([$department]);$topicIds=$q->fetchAll(PDO::FETCH_COLUMN);if(!$topicIds)throw new RuntimeException('Legal intake is unavailable');
 $data=$input['attachmentBase64']??'';if(!is_string($data)||strlen($data)>14*1024*1024||base64_decode($data,true)===false)throw new RuntimeException('Invalid original message attachment');
 $subject=mb_substr(preg_replace('/[\r\n\x00]/',' ',(string)($input['subject']??'Reported mail')),0,160);$body=(string)($input['text']??'');if(strlen($body)>1048576)throw new RuntimeException('Report text exceeds limit');
 $_SERVER['SCRIPT_NAME']='/mail-report-ticket.php';$_SERVER['REMOTE_ADDR']='127.0.0.1';$_SERVER['REQUEST_METHOD']='POST';$_SERVER['SERVER_NAME']=$_SERVER['HTTP_HOST']='support.dunerealms.com';
 chdir(__DIR__);require __DIR__.'/main.inc.php';
 $teamRecord=Team::lookup((int)$team);if(!$teamRecord||!$teamRecord->isActive())throw new RuntimeException('Legal team is inactive');$topic=null;foreach($topicIds as$topicId){$candidate=Topic::lookup((int)$topicId);if($candidate&&$candidate->isActive()){$topic=$topicId;break;}}if(!$topic)throw new RuntimeException('No active Legal topic');
 $email=$input['reporterId'].'@accounts.dunerealms.invalid';$name=mb_substr((string)($input['reporterName']??'DuneRealms member'),0,40);
 $user=User::lookupByEmail($email)?:User::fromVars(['name'=>$name,'email'=>$email]);if(!$user)throw new RuntimeException('Reporter support identity is unavailable');
 $q=$db->prepare("INSERT INTO dune_mail_report_tickets(report_id,reporter_id,state) VALUES(?,?,'processing')");$q->execute([$input['reportId'],$input['reporterId']]);
 $errors=[];$vars=['uid'=>$user->getId(),'name'=>$name,'email'=>$email,'topicId'=>(int)$topic,'deptId'=>(int)$department,'teamId'=>(int)$team,'subject'=>'Mail report · '.$subject,'message'=>nl2br(htmlspecialchars($body,ENT_QUOTES,'UTF-8')),'source'=>'API','ip'=>'127.0.0.1','autorespond'=>false,'attachments'=>[['name'=>'reported-message.eml','type'=>'message/rfc822','data'=>$data,'encoding'=>'base64']]];
 $ticket=Ticket::create($vars,$errors,'API',false,false);
 if(!$ticket){$q=$db->prepare("DELETE FROM dune_mail_report_tickets WHERE report_id=? AND ticket_id IS NULL");$q->execute([$input['reportId']]);throw new RuntimeException('Legal ticket could not be created: '.json_encode($errors));}
 if((int)$ticket->getDeptId()!==(int)$department||(int)$ticket->getTeamId()!==(int)$team)throw new RuntimeException('Ticket routing requires review');
 $q=$db->prepare("UPDATE dune_mail_report_tickets SET ticket_id=?,state='created' WHERE report_id=?");$q->execute([$ticket->getId(),$input['reportId']]);
 echo json_encode(['ok'=>true,'ticketId'=>$ticket->getId(),'ticketNumber'=>$ticket->getNumber(),'department'=>'Legal','team'=>'Legal']);
} catch(Throwable $error) {error_log('Dune mail report conversion: '.substr(str_replace(["\r","\n"],' ',$error->getMessage()),0,500));fwrite(STDOUT,json_encode(['error'=>'Mail report conversion requires review. No automatic duplicate will be created.']));exit(1);}
