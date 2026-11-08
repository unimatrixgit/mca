<?php
// Public IDs are immutable; relational ticket_id and all ticket permissions stay native.
if (PHP_SAPI !== 'cli' && !defined('INCLUDE_DIR')) { http_response_code(404); exit; }
function dune_identifier_db() {
    static $db;
    if (!$db) {
        $c=json_decode(file_get_contents('/home/dunerealms/.config/integrations.json'),true,8,JSON_THROW_ON_ERROR);
        $db=new PDO('mysql:host='.$c['host'].';dbname='.$c['database'].';charset=utf8mb4',$c['username'],$c['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
    }
    return $db;
}
function dune_identifier_registry() {
    return ['Support'=>'SP','Sales'=>'SA','Maintenance'=>'MT','Ban appeals'=>'BA',
            'Forum support'=>'FS','Legal'=>'LG','Network staff'=>'NW','Dev team'=>'DEV'];
}
function dune_identifier_schema(PDO $db) {
    $db->exec("CREATE TABLE IF NOT EXISTS dune_ticket_identifiers (
        public_number VARCHAR(20) CHARACTER SET ascii COLLATE ascii_general_ci PRIMARY KEY,
        ticket_id INT UNSIGNED NULL UNIQUE, department_id INT UNSIGNED NOT NULL,
        department_code VARCHAR(8) NOT NULL, workflow_type VARCHAR(3) NOT NULL DEFAULT 'SP',
        original_created DATETIME NULL, internal_reference VARCHAR(64) NULL,
        assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
    $columns=$db->query('SHOW COLUMNS FROM dune_ticket_identifiers')->fetchAll(PDO::FETCH_COLUMN);
    if(!in_array('format_version',$columns,true))$db->exec('ALTER TABLE dune_ticket_identifiers ADD format_version SMALLINT NOT NULL DEFAULT 1');
    $db->exec('CREATE TABLE IF NOT EXISTS dune_ticket_number_sequences (department_code VARCHAR(8) PRIMARY KEY,last_issued BIGINT UNSIGNED NOT NULL DEFAULT 0) ENGINE=InnoDB');
    $db->exec("CREATE TABLE IF NOT EXISTS dune_ticket_number_aliases (
        alias VARCHAR(64) CHARACTER SET ascii COLLATE ascii_general_ci PRIMARY KEY,
        ticket_id INT UNSIGNED NOT NULL,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        INDEX(ticket_id)) ENGINE=InnoDB");
}
function dune_identifier_department(PDO $db,$deptId) {
    $q=$db->prepare('SELECT name FROM drsupport_department WHERE id=?');$q->execute([(int)$deptId]);$name=$q->fetchColumn();
    $map=dune_identifier_registry();
    if (!$name || !isset($map[$name])) throw new RuntimeException('Department needs an approved ticket identifier code');
    return $map[$name];
}
function dune_identifier_suffix($value) {
    if(!is_int($value)||$value<1||$value>78364164095)throw new RuntimeException('Department ticket sequence exhausted');
    $alphabet='0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';$suffix='';
    do{$suffix=$alphabet[$value%36].$suffix;$value=intdiv($value,36);}while($value>0);
    return str_pad($suffix,7,'0',STR_PAD_LEFT);
}
function dune_identifier_reserve(PDO $db,$deptId) {
    $code=dune_identifier_department($db,$deptId);
    if((int)$db->query("SELECT GET_LOCK('dune-ticket-identifiers-v1',15)")->fetchColumn()!==1)throw new RuntimeException('Ticket numbering is busy');
    $own=!$db->inTransaction();
    try{
        if($own)$db->beginTransaction();
        $q=$db->prepare('INSERT IGNORE INTO dune_ticket_number_sequences(department_code,last_issued) VALUES(?,0)');$q->execute([$code]);
        $q=$db->prepare('SELECT last_issued FROM dune_ticket_number_sequences WHERE department_code=? FOR UPDATE');$q->execute([$code]);$value=(int)$q->fetchColumn();
        for($i=0;$i<1000;$i++){
            $number='DR_'.$code.'-'.dune_identifier_suffix(++$value);
            $q=$db->prepare('SELECT public_number FROM dune_ticket_identifiers WHERE public_number=? UNION SELECT number FROM drsupport_ticket WHERE number=? UNION SELECT alias FROM dune_ticket_number_aliases WHERE alias=?');$q->execute([$number,$number,$number]);if($q->fetch())continue;
            $q=$db->prepare('INSERT INTO dune_ticket_identifiers(public_number,department_id,department_code,format_version) VALUES(?,?,?,2)');$q->execute([$number,(int)$deptId,$code]);
            $q=$db->prepare('UPDATE dune_ticket_number_sequences SET last_issued=? WHERE department_code=?');$q->execute([$value,$code]);
            if($own)$db->commit();return $number;
        }
        throw new RuntimeException('Could not allocate a unique sequence value');
    }catch(Throwable $e){if($own&&$db->inTransaction())$db->rollBack();throw $e;}
    finally{$db->query("SELECT RELEASE_LOCK('dune-ticket-identifiers-v1')");}
}
function dune_generate_ticket_number($deptId,$topicId=0){return dune_identifier_reserve(dune_identifier_db(),(int)$deptId);}
function dune_bind_ticket_identifier($id,$number) {
    $db=dune_identifier_db();
    $q=$db->prepare('SELECT t.created,t.topic_id,d.name AS department,h.topic FROM drsupport_ticket t JOIN drsupport_department d ON d.id=t.dept_id LEFT JOIN drsupport_help_topic h ON h.topic_id=t.topic_id WHERE t.ticket_id=?');$q->execute([(int)$id]);$row=$q->fetch();
    if(!$row)throw new RuntimeException('Ticket disappeared during identifier binding');
    $kind=$row['department']==='Dev team'?'DEV':(preg_match('/application|signup|election|internal system/i',$row['topic']??'')?'INT':'SP');
    $date=substr(str_replace('-','',$row['created']),0,8);
    $q=$db->prepare('UPDATE dune_ticket_identifiers SET ticket_id=?,workflow_type=?,original_created=?,internal_reference=? WHERE public_number=? AND (ticket_id IS NULL OR ticket_id=?)');
    $q->execute([(int)$id,$kind,$row['created'],$kind.'-'.$date.'-'.$number,$number,(int)$id]);
}
function dune_ticket_number_alias_ids($number){
    if(!is_string($number)||strlen($number)>64)return [];
    $q=dune_identifier_db()->prepare('SELECT ticket_id FROM dune_ticket_number_aliases WHERE alias=?');$q->execute([$number]);
    return array_map('intval',$q->fetchAll(PDO::FETCH_COLUMN));
}

