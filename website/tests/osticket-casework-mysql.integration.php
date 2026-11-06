<?php
// Uses installed native classes; never loads the installed application's configuration.
if(PHP_SAPI!=='cli')throw new Exception('CLI only');
foreach(['mail','fsockopen','pfsockopen','stream_socket_client','curl_exec']as$function)if(function_exists($function))throw new Exception('Disable outbound function before this QA: '.$function);
$socket=getenv('DUNE_CASE_TEST_SOCKET');if(!preg_match('~^/tmp/dune-case324-qa-[a-zA-Z0-9_-]+/mysql\.sock$~',$socket))throw new Exception('Isolated socket required');
$qa=dirname($socket);if(trim(file_get_contents($qa.'/.codex-qa324'))!=='isolated-casework-324')throw new Exception('Missing isolation marker');
$_SERVER['SCRIPT_NAME']='native324-smoke.php';$_SERVER['REQUEST_METHOD']='POST';$_SERVER['REMOTE_ADDR']='127.0.0.1';$_SERVER['HTTP_HOST']='isolated.invalid';$_SERVER['REQUEST_URI']='/fixture';
define('ROOT_PATH','/');define('TABLE_PREFIX','drsupport_');define('SECRET_SALT','isolated-fixture');define('SESSION_SECRET','isolated-fixture');define('ADMIN_EMAIL','fixture@example.invalid');
$nativeRoot=rtrim(getenv('DUNE_NATIVE_ROOT')?:'/home/dunerealms/apps/integrations/osticket','/');require $nativeRoot.'/bootstrap.php';Bootstrap::defineTables(TABLE_PREFIX);Bootstrap::i18n_prep();Bootstrap::loadCode();
// db_connect/Bootstrap::connect are deliberately not called: DBHOST/DBUSER/DBPASS are not defined.
$__db=new mysqli('localhost','root','','website',0,$socket);
$info=$__db->query('SELECT @@datadir AS dir,@@skip_networking AS isolated')->fetch_assoc();if(realpath($info['dir'])!==realpath($qa.'/data')||$info['isolated']!=='1')throw new Exception('Non-isolated database');
$__db->query("SET SESSION sql_mode='' "); // Native db_connect uses this same per-connection mode.
foreach(json_decode(file_get_contents(getenv('DUNE_NATIVE_TEST_SCHEMA')?:'/tmp/native324-schema.json'),true,512,JSON_THROW_ON_ERROR)as$table=>$ddl){if(!preg_match('/^drsupport_[a-z0-9_]+$/',$table))throw new Exception('Unexpected table');$__db->query(preg_replace('/^CREATE TABLE /','CREATE TABLE IF NOT EXISTS ',$ddl,1));}
require_once INCLUDE_DIR.'class.config.php';require_once INCLUDE_DIR.'class.staff.php';
$cfg=new OsticketConfig();
Internationalization::bootstrap();
$staff=Staff::lookup(7);$ticket=Ticket::lookup(42);
$checks=0;function same($actual,$expected){global $checks;if($actual!==$expected)throw new Exception('Native assertion '.($checks+1).' failed: '.json_encode([$actual,$expected]));$checks++;}
function fixtureInsert($table,$values){global $__db;if(!preg_match('/^drsupport_[a-z0-9_]+$/',$table))throw new Exception('Invalid fixture table');$columns=$__db->query('SHOW COLUMNS FROM `'.$table.'`');while($column=$columns->fetch_assoc()){
 if(array_key_exists($column['Field'],$values)||$column['Null']==='YES'||$column['Default']!==null||str_contains($column['Extra'],'auto_increment'))continue;
 $type=$column['Type'];$values[$column['Field']]=preg_match('/^(tinyint|smallint|mediumint|int|bigint|decimal|float|double|bit)/',$type)?0:(str_starts_with($type,'datetime')||str_starts_with($type,'timestamp')?'2026-11-01 00:00:00':(str_starts_with($type,'date')?'2026-11-01':(preg_match("/^enum\('([^']*)'/",$type,$match)?$match[1]:'')));
 }$fields=array_keys($values);$statement=$__db->prepare('INSERT INTO `'.$table.'`(`'.implode('`,`',$fields).'`)VALUES('.implode(',',array_fill(0,count($fields),'?')).')');$statement->execute(array_values($values));}
function fresh(){ModelInstanceManager::flushCache();return[Staff::lookup(7),Ticket::lookup(42)];}
same($staff instanceof Staff,true);same($ticket instanceof Ticket,true);
$__db->query("UPDATE drsupport_department SET path=CONCAT('/',id,'/'),flags=4");$__db->query('UPDATE drsupport_staff SET assigned_only=0 WHERE staff_id=7');
fixtureInsert('drsupport_role',['id'=>1,'name'=>'Fixture close/assign','flags'=>1,'permissions'=>json_encode([Ticket::PERM_CLOSE=>1,Ticket::PERM_ASSIGN=>1,Ticket::PERM_CREATE=>1])]);
fixtureInsert('drsupport_role',['id'=>2,'name'=>'Fixture read only','flags'=>1,'permissions'=>'{}']);
[$staff,$ticket]=fresh();same($ticket->checkStaffPerm($staff),true);same($ticket->checkStaffPerm($staff,Ticket::PERM_CLOSE),true);same($ticket->checkStaffPerm($staff,Ticket::PERM_ASSIGN),true);
$__db->query('UPDATE drsupport_ticket SET dept_id=3,staff_id=0,team_id=0 WHERE ticket_id=42');[$staff,$ticket]=fresh();same($ticket->checkStaffPerm($staff),false);
fixtureInsert('drsupport_staff_dept_access',['staff_id'=>7,'dept_id'=>3,'role_id'=>2]);[$staff,$ticket]=fresh();same($ticket->checkStaffPerm($staff),true);same($ticket->checkStaffPerm($staff,Ticket::PERM_CLOSE),false);same($ticket->checkStaffPerm($staff,Ticket::PERM_ASSIGN),false);same($staff->hasPerm(Ticket::PERM_CLOSE,false),true);
$thisstaff=$staff;$errors=[];same($ticket->setStatus(TicketStatus::lookup(2),'',$errors),false);same((int)$__db->query('SELECT status_id FROM drsupport_ticket WHERE ticket_id=42')->fetch_assoc()['status_id'],1);
$__db->query('DELETE FROM drsupport_staff_dept_access');$__db->query('UPDATE drsupport_ticket SET dept_id=1 WHERE ticket_id=42');$__db->query('UPDATE drsupport_staff SET assigned_only=1 WHERE staff_id=7');[$staff,$ticket]=fresh();same($ticket->checkStaffPerm($staff),false);
$__db->query('UPDATE drsupport_ticket SET staff_id=7 WHERE ticket_id=42');[$staff,$ticket]=fresh();same($ticket->checkStaffPerm($staff),true);same($ticket->checkStaffPerm($staff,Ticket::PERM_ASSIGN),true);
$__db->query('UPDATE drsupport_ticket SET staff_id=0,team_id=9 WHERE ticket_id=42');fixtureInsert('drsupport_team',['team_id'=>9,'name'=>'Isolated fixture team','flags'=>1]);fixtureInsert('drsupport_team_member',['team_id'=>9,'staff_id'=>7]);[$staff,$ticket]=fresh();same($ticket->checkStaffPerm($staff),true);
$__db->query('DELETE FROM drsupport_team_member WHERE team_id=9 AND staff_id=7');[$staff,$ticket]=fresh();same($ticket->checkStaffPerm($staff),false);
// Invoke the actual installed bulk-status handler with one permitted and one read-only ticket.
$__db->query('UPDATE drsupport_staff SET assigned_only=0 WHERE staff_id=7');$__db->query('UPDATE drsupport_ticket SET staff_id=0,team_id=0,dept_id=1 WHERE ticket_id=42');
fixtureInsert('drsupport_staff_dept_access',['staff_id'=>7,'dept_id'=>3,'role_id'=>2]);
fixtureInsert('drsupport_ticket',['ticket_id'=>43,'number'=>'434343','user_id'=>11,'user_email_id'=>21,'status_id'=>1,'dept_id'=>3,'topic_id'=>1]);fixtureInsert('drsupport_ticket__cdata',['ticket_id'=>43,'subject'=>'Read-only isolated ticket']);fixtureInsert('drsupport_thread',['id'=>10,'object_id'=>43,'object_type'=>'T']);
fixtureInsert('drsupport_event',['id'=>4,'name'=>'closed']);fixtureInsert('drsupport_event',['id'=>5,'name'=>'reopened']);
foreach(['auto_refer_closed','require_topic_to_close','ticket_alert_active','ticket_status_alert_active']as$key)$cfg->set($key,0);
[$thisstaff,$ticket]=fresh();$ost=(new ReflectionClass('osTicket'))->newInstanceWithoutConstructor();$ost->config=$cfg;
require_once INCLUDE_DIR.'ajax.tickets.php';$_SESSION=[];$mode=getenv('DUNE_NATIVE_CASE')?:'status';if(!in_array($mode,['status','claim','team'],true))throw new Exception('Unknown isolated case');
if($mode==='team')fixtureInsert('drsupport_team_member',['team_id'=>9,'staff_id'=>7]);
$_POST=$_REQUEST=['tids'=>[42,43],'status_id'=>2,'assignee'=>[$mode==='team'?'t9':'s7'],'comments'=>''];
register_shutdown_function(function()use(&$checks,$__db,$mode){
 if($mode==='status'){same((int)$__db->query('SELECT status_id FROM drsupport_ticket WHERE ticket_id=42')->fetch_assoc()['status_id'],2);same((int)$__db->query('SELECT status_id FROM drsupport_ticket WHERE ticket_id=43')->fetch_assoc()['status_id'],1);same((int)$__db->query('SELECT COUNT(*) n FROM drsupport_thread_event WHERE thread_id=9 AND event_id=4')->fetch_assoc()['n'],1);same((int)$__db->query('SELECT COUNT(*) n FROM drsupport_thread_event WHERE thread_id=10')->fetch_assoc()['n'],0);}
 else{$field=$mode==='team'?'team_id':'staff_id';same((int)$__db->query('SELECT '.$field.' AS assigned FROM drsupport_ticket WHERE ticket_id=42')->fetch_assoc()['assigned'],$mode==='team'?9:7);same((int)$__db->query('SELECT '.$field.' AS assigned FROM drsupport_ticket WHERE ticket_id=43')->fetch_assoc()['assigned'],0);same((int)$__db->query('SELECT COUNT(*) n FROM drsupport_thread_event WHERE thread_id=9 AND event_id=3')->fetch_assoc()['n'],1);same((int)$__db->query('SELECT COUNT(*) n FROM drsupport_thread_event WHERE thread_id=10')->fetch_assoc()['n'],0);}
 same(isset($_SESSION['::sysmsgs']['warn']),true);echo "\n".json_encode(['passed'=>$checks,'installedNativeClasses'=>true,'actualBulkHandler'=>$mode,'isolation'=>'dedicated socket-only MySQL; synthetic rows only','productionWrites'=>false,'outboundFunctionsDisabled'=>true])."\n";
});
$api=new TicketsAjaxAPI();if($mode==='claim')$api->massProcess('claim');elseif($mode==='team')$api->massProcess('assign','teams');else $api->setSelectedTicketsStatus('closed');
throw new Exception('Expected native bulk handler completion');
