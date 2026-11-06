<?php
// Test only: invoked immediately after MyBB DB connection, before cache/session init.
if(PHP_SAPI!=='cli'||($config['cache_store']??'db')!=='db')throw new Exception('DB-cache CLI isolation required');
$stateFile='/tmp/forum372-native-qa/state.json';$saved=is_file($stateFile)?json_decode(file_get_contents($stateFile),true):[];
$seed=['users','settings','usergroups','forums','forumpermissions','threadprefixes','moderators','datacache','settinggroups'];
$result=$db->query("SHOW TABLES LIKE '".$db->escape_string(TABLE_PREFIX)."%'");$names=[];while($row=$db->fetch_array($result))$names[]=array_values($row)[0];
foreach($names as$name){
 if(!preg_match('/^[A-Za-z0-9_]+$/',$name)||strpos($name,TABLE_PREFIX)!==0)throw new Exception('Unexpected table name');
 $short=substr($name,strlen(TABLE_PREFIX));$rows=[];
 if(in_array($short,$seed,true)){$q=$db->query('SELECT * FROM `'.$name.'`'.($short==='users'?' WHERE uid=2':''));while($r=$db->fetch_array($q))$rows[]=$r;}
 if(isset($saved[$short]))$rows=$saved[$short];
 $binary=[];$q=$db->query('SHOW COLUMNS FROM `'.$name.'`');while($column=$db->fetch_array($q))$binary[$column['Field']]=(bool)preg_match('/binary|blob/i',$column['Type']);
 $ddl=$db->fetch_array($db->query('SHOW CREATE TABLE `'.$name.'`'));
 $create=preg_replace('/^CREATE TABLE /','CREATE TEMPORARY TABLE ',array_values($ddl)[1],1,$count);if($count!==1)throw new Exception('Unexpected table DDL');$db->write_query($create);
 foreach($rows as$row){$columns=[];$values=[];foreach($row as$k=>$v){if(!preg_match('/^[A-Za-z0-9_]+$/',$k))throw new Exception('Unexpected column');$columns[]='`'.$k.'`';$values[]=$v===null?'NULL':($binary[$k]?"X'".bin2hex((string)$v)."'":"'".$db->escape_string($v)."'");}$db->write_query('INSERT INTO `'.$name.'` ('.implode(',',$columns).') VALUES ('.implode(',',$values).')');}
}
// New adapter table is also temporary, including on the first migration run.
if(!in_array(TABLE_PREFIX.'dune_news_revision',$names,true))$db->write_query('CREATE TEMPORARY TABLE '.TABLE_PREFIX.'dune_news_revision(post_id CHAR(36) PRIMARY KEY,revision VARCHAR(64) NOT NULL,state VARCHAR(16) NOT NULL,updated_at BIGINT NOT NULL) ENGINE=InnoDB');
if(isset($saved['dune_news_revision'])&&!in_array(TABLE_PREFIX.'dune_news_revision',$names,true))foreach($saved['dune_news_revision']as$row)$db->insert_query('dune_news_revision',array_map(fn($v)=>$db->escape_string($v),$row));
if(getenv('ADD_REPLY')==='1'){
 $thread=$db->fetch_array($db->simple_select('threads','*'));
 $db->insert_query('posts',['pid'=>100,'tid'=>(int)$thread['tid'],'fid'=>14,'subject'=>'Re: QA announcement','uid'=>2,'username'=>'dunerealms','dateline'=>(int)$thread['dateline']+1,'message'=>'REPLY SENTINEL KEEP','ipaddress'=>'','visible'=>1]);
 $db->update_query('threads',['replies'=>1,'lastpost'=>(int)$thread['dateline']+1,'lastposter'=>'dunerealms','lastposteruid'=>2],"tid='".(int)$thread['tid']."'");
}
register_shutdown_function(function()use($db,$stateFile){
 $state=[];foreach(['threads','posts','dune_news_delivery','dune_news_revision']as$table){$q=$db->simple_select($table,'*');$state[$table]=[];while($row=$db->fetch_array($q))$state[$table][]=$row;}
 file_put_contents($stateFile,json_encode($state,JSON_THROW_ON_ERROR));
});
