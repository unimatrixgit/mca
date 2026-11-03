<?php
// CLI-only native-boundary doubles. SQL and advisory locks use isolated real MySQL tables.
if (PHP_SAPI !== 'cli') exit(1);
$request=json_decode(file_get_contents($argv[1]),true,512,JSON_THROW_ON_ERROR);
$prefix=getenv('DUNE_TEST_PREFIX');
if (!preg_match('/^dune_test344_[a-f0-9]{12}_$/D',$prefix)) throw new RuntimeException('Invalid fixture namespace');
$config=json_decode(file_get_contents(getenv('DUNE_TEST_DB_CONFIG')),true,8,JSON_THROW_ON_ERROR);
$fixtureConnection=new PDO('mysql:host='.$config['host'].';port='.($config['port']??3306).';dbname='.$config['database'].';charset=utf8mb4',$config['username'],$config['password'],[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION,PDO::ATTR_DEFAULT_FETCH_MODE=>PDO::FETCH_ASSOC]);
$fixtureTables=['dune_dev_tasks','dune_dev_task_events','drsupport_help_topic','drsupport_ticket','drsupport_ticket__cdata','drsupport_ticket_status'];
class FixtureStatement {
 function __construct(private $statement,private $lock=false){}
 function execute($args=[]){global $prefix;if($this->lock)$args[0]=$prefix.substr(hash('sha256',$args[0]),0,32);return $this->statement->execute($args);}
 function __call($name,$args){return $this->statement->$name(...$args);}
}
class FixturePDO {
 function __construct(...$unused){}
 function sql($sql){global $prefix,$fixtureTables;foreach($fixtureTables as $table)$sql=preg_replace('/\b'.preg_quote($table,'/').'\b/',$prefix.$table,$sql);preg_match_all('/\b(?:FROM|JOIN|UPDATE|INTO)\s+([a-zA-Z0-9_]+)/i',$sql,$matches);foreach($matches[1]as$table)if(!in_array($table,array_map(fn($t)=>$prefix.$t,$fixtureTables),true))throw new RuntimeException('Fixture SQL escaped namespace');return $sql;}
 function prepare($sql){global $fixtureConnection;return new FixtureStatement($fixtureConnection->prepare($this->sql($sql)),str_contains($sql,'GET_LOCK(')||str_contains($sql,'RELEASE_LOCK('));}
 function query($sql){global $fixtureConnection;return $fixtureConnection->query($this->sql($sql));}
}
function dune_ticket_identity(){global $request;return $request['identity'];}
class User {
 static function lookupByEmail($email){return new self;}
 static function fromVars($vars){return new self;}
 function getId(){global $request;return ['owner'=>1,'vip'=>2,'dev'=>3,'staff'=>4][$request['identity']['id']]??5;}
}
class Ticket {
 function __construct(private $id){}
 static function lookup($id){$db=new FixturePDO;$q=$db->prepare('SELECT ticket_id FROM drsupport_ticket WHERE ticket_id=?');$q->execute([$id]);return $q->fetch()?new self((int)$id):null;}
 static function create($vars,&$errors,...$unused){
  global $request,$fixtureConnection;
  if($request['nativeCreateFail']??false)return false;
  $db=new FixturePDO;$q=$db->prepare('INSERT INTO drsupport_ticket(user_id,dept_id,status_id,number) VALUES(?,?,1,?)');$q->execute([$vars['uid'],$vars['deptId'],'fixture']);$id=(int)$fixtureConnection->lastInsertId();$q=$db->prepare('INSERT INTO drsupport_ticket__cdata(ticket_id,subject) VALUES(?,?)');$q->execute([$id,$vars['subject']]);
  if($request['crashAfterNative']??false){echo 'fixture interrupted after native ticket';exit;}
  return new self($id);
 }
 function getId(){return $this->id;}
 function isClosed(){$db=new FixturePDO;$q=$db->prepare('SELECT status_id FROM drsupport_ticket WHERE ticket_id=?');$q->execute([$this->id]);return (int)$q->fetchColumn()===3;}
 function setStatus($status,$note,&$errors,...$unused){global $request;if($request['nativeCloseFail']??false)return false;$db=new FixturePDO;$q=$db->prepare('UPDATE drsupport_ticket SET status_id=? WHERE ticket_id=?');$q->execute([$status,$this->id]);return true;}
}
$_SERVER=['REQUEST_METHOD'=>$request['method']??'POST','HTTP_ORIGIN'=>$request['origin']??'https://support.dunerealms.com'];
$_POST=$request['post']??[];$_GET=$request['get']??['view'=>'new'];$_SESSION=$request['session']??['dune_dev_csrf'=>str_repeat('a',64),'dune_dev_submission'=>str_repeat('b',64)];
ob_start();register_shutdown_function(function(){global $errors,$values,$selected; $output=ob_get_clean();echo json_encode(['status'=>http_response_code()?:200,'errors'=>$errors??[],'values'=>$values??[],'selected'=>$selected??null,'session'=>$_SESSION,'body'=>$output],JSON_THROW_ON_ERROR);});
