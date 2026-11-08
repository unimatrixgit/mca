<?php
if(PHP_SAPI!=='cli')exit(1);
require __DIR__.'/ticket-identifiers.php';
$db=dune_identifier_db();$apply=in_array('--apply',$argv,true);$registry=dune_identifier_registry();
$depts=$db->query('SELECT id,name FROM drsupport_department ORDER BY id')->fetchAll();
foreach($depts as $d)if(!isset($registry[$d['name']]))throw new RuntimeException('Unmapped department: '.$d['name']);
if(!$apply){echo json_encode(['mode'=>'dry-run','ticketCount'=>(int)$db->query('SELECT COUNT(*) FROM drsupport_ticket')->fetchColumn(),'departments'=>array_map(fn($d)=>['name'=>$d['name'],'code'=>$registry[$d['name']]],$depts)])."\n";exit;}
$lock=$db->query("SELECT GET_LOCK('dune-ticket-identifiers-v1',15)")->fetchColumn();if((int)$lock!==1)throw new RuntimeException('Migration already running');
try{
    $before=$db->query('SELECT ticket_id,number,dept_id,topic_id,user_id,created FROM drsupport_ticket ORDER BY ticket_id')->fetchAll();
    $backup='/root/ticket-identifiers-'.gmdate('Ymd\THis\Z').'-'.bin2hex(random_bytes(3));
    mkdir($backup,0700);file_put_contents($backup.'/numbers-before.json',json_encode($before,JSON_PRETTY_PRINT));chmod($backup.'/numbers-before.json',0600);
    $counts=[];foreach(['drsupport_ticket','drsupport_thread','drsupport_thread_entry','drsupport_attachment','native_ban_appeals','dune_dev_tasks'] as $table)$counts[$table]=(int)$db->query('SELECT COUNT(*) FROM '.$table)->fetchColumn();
    dune_identifier_schema($db);$db->beginTransaction();
    $tickets=$db->query('SELECT ticket_id,number,dept_id FROM drsupport_ticket ORDER BY created,ticket_id FOR UPDATE')->fetchAll();$changed=0;$unchanged=0;$mapping=[];
    foreach($tickets as $t){
        $q=$db->prepare('SELECT public_number,format_version FROM dune_ticket_identifiers WHERE ticket_id=?');$q->execute([$t['ticket_id']]);$existing=$q->fetch();
        if($existing){if($existing['public_number']!==$t['number'])throw new RuntimeException('Mapped ticket was modified outside the migration');if((int)$existing['format_version']===2){$unchanged++;continue;}}
        $number=dune_identifier_reserve($db,$t['dept_id']);
        if($t['number']!==null&&$t['number']!==''){
            $q=$db->prepare('INSERT INTO dune_ticket_number_aliases(alias,ticket_id) VALUES(?,?)');$q->execute([$t['number'],$t['ticket_id']]);
        }
        if($existing){$q=$db->prepare('DELETE FROM dune_ticket_identifiers WHERE public_number=? AND ticket_id=?');$q->execute([$existing['public_number'],$t['ticket_id']]);}
        $q=$db->prepare('UPDATE drsupport_ticket SET number=? WHERE ticket_id=? AND number=?');$q->execute([$number,$t['ticket_id'],$t['number']]);if($q->rowCount()!==1)throw new RuntimeException('Ticket changed concurrently');
        dune_bind_ticket_identifier($t['ticket_id'],$number);
        $mapping[]=['ticket_id'=>$t['ticket_id'],'old'=>$t['number'],'new'=>$number];$changed++;
    }
    foreach($counts as $table=>$count)if((int)$db->query('SELECT COUNT(*) FROM '.$table)->fetchColumn()!==$count)throw new RuntimeException('Relational row count changed: '.$table);
    $duplicates=(int)$db->query('SELECT COUNT(*) FROM (SELECT number FROM drsupport_ticket GROUP BY number HAVING COUNT(*)>1) d')->fetchColumn();if($duplicates)throw new RuntimeException('Duplicate public references');
    $db->commit();file_put_contents($backup.'/mapping.json',json_encode($mapping,JSON_PRETTY_PRINT));chmod($backup.'/mapping.json',0600);
    $result=['changed'=>$changed,'unchanged'=>$unchanged,'total'=>count($tickets),'duplicates'=>$duplicates,'backup'=>$backup,'preservedCounts'=>$counts,'departmentCounts'=>$db->query('SELECT department_code,COUNT(*) AS tickets FROM dune_ticket_identifiers WHERE ticket_id IS NOT NULL GROUP BY department_code')->fetchAll()];
    file_put_contents($backup.'/result.json',json_encode($result,JSON_PRETTY_PRINT));echo json_encode($result)."\n";
}catch(Throwable $e){if($db->inTransaction())$db->rollBack();throw $e;}
finally{$db->query("SELECT RELEASE_LOCK('dune-ticket-identifiers-v1')");}
