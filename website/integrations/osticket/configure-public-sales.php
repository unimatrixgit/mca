<?php
// Private CLI installer: a separate customer intake; the email/internal topic stays private.
if (PHP_SAPI !== 'cli') exit(1);
$apply = in_array('--apply', $argv, true);
$c = json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'), true);
$db = new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4', $c['username'], $c['password'], [PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
$source = $db->query("SELECT h.* FROM drsupport_help_topic h JOIN drsupport_department d ON d.id=h.dept_id WHERE h.topic='Sales enquiries' AND h.ispublic=0 AND d.name='Sales'")->fetch();
if (!$source || !$db->query('SELECT id FROM drsupport_form WHERE id=2')->fetchColumn()) throw new RuntimeException('Private Sales route or standard Ticket Details form missing');
$name = 'Customer sales enquiries';
$lookup = $db->prepare('SELECT * FROM drsupport_help_topic WHERE topic=?');
$lookup->execute([$name]);$existing=$lookup->fetch();
function checkSalesIntake($row, $source, $db) {
    if ((int)$row['ispublic']!==1 || (int)$row['dept_id']!==(int)$source['dept_id'] || (int)$row['team_id']!==(int)$source['team_id'] || (int)$row['staff_id']!==0) throw new RuntimeException('Customer Sales intake routing drift; review manually');
    $q=$db->prepare('SELECT form_id FROM drsupport_help_topic_form WHERE topic_id=? ORDER BY form_id');$q->execute([$row['topic_id']]);
    if (array_map('intval',$q->fetchAll(PDO::FETCH_COLUMN))!==[2]) throw new RuntimeException('Customer Sales intake has unexpected forms');
}
if ($existing) { checkSalesIntake($existing,$source,$db);echo json_encode(['topicId'=>(int)$existing['topic_id'],'created'=>false,'privateTopicUnchanged'=>true]).PHP_EOL;exit; }
if (!$apply) {echo json_encode(['wouldCreate'=>$name,'departmentId'=>(int)$source['dept_id'],'standardFormId'=>2,'privateTopicUnchanged'=>true]).PHP_EOL;exit;}
$engines=$db->query("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('drsupport_help_topic','drsupport_help_topic_form')")->fetchAll(PDO::FETCH_COLUMN);
if (count($engines)!==2 || array_filter($engines,static fn($v)=>strtoupper($v)!=='INNODB')) throw new RuntimeException('Transactional topic tables required');
if ((int)$db->query("SELECT GET_LOCK('dune:public-sales:328',5)")->fetchColumn()!==1) throw new RuntimeException('Sales intake installer busy');
try {
    $db->beginTransaction();
    $lookup->execute([$name]);$existing=$lookup->fetch();
    if ($existing) {checkSalesIntake($existing,$source,$db);$id=(int)$existing['topic_id'];$created=false;}
    else {
        $row=$source;unset($row['topic_id']);$row['topic']=$name;$row['ispublic']=1;$row['staff_id']=0;
        $row['notes']='Customer-facing Sales request. Manual review; no automatic refund, entitlement or payment action. Never request passwords or card details. Internal Sales email topic remains private.';
        $row['created']=$row['updated']=gmdate('Y-m-d H:i:s');
        $columns=array_keys($row);$q=$db->prepare('INSERT INTO drsupport_help_topic (`'.implode('`,`',$columns).'`) VALUES ('.implode(',',array_fill(0,count($columns),'?')).')');$q->execute(array_values($row));$id=(int)$db->lastInsertId();
        $q=$db->prepare("INSERT INTO drsupport_help_topic_form(topic_id,form_id,sort,extra) VALUES(?,2,1,'{}')");$q->execute([$id]);
        $lookup->execute([$name]);checkSalesIntake($lookup->fetch(),$source,$db);$created=true;
    }
    $db->commit();echo json_encode(['topicId'=>$id,'created'=>$created,'privateTopicUnchanged'=>true]).PHP_EOL;
} catch (Throwable $e) {if($db->inTransaction())$db->rollBack();throw $e;}
finally {$db->query("SELECT RELEASE_LOCK('dune:public-sales:328')");}
