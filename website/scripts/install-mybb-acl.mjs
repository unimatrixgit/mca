import {readFileSync,writeFileSync,copyFileSync,mkdirSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {basename} from 'node:path';
const root=process.argv[2]||'/home/dunerealms/apps/integrations/mybb';
const helper=process.argv[3]||new URL('../integrations/mybb/forum-access.php',import.meta.url);
const seals=process.argv[4]||'/home/dunerealms/apps/dunerealms/current/node/native-seals.mjs';
const backup='/root/dune-forum-acl-'+new Date().toISOString().replace(/[:.]/g,'-');
const legacy="in_array('web.ranks.manage',$identity['permissions'],true)?4:(in_array('web.forum.moderate',$identity['permissions'],true)?6:2)";
const oldGuard="!in_array('web.ranks.manage',$identity['permissions'],true)||!in_array('web.forum.moderate',$identity['permissions'],true)";
const edits=[];
for(const [path,include]of [[root+'/dune-sso.php',"require_once __DIR__.'/forum-access.php';"],[root+'/inc/plugins/dune_accounts.php',"require_once MYBB_ROOT.'forum-access.php';"]]){
 const before=readFileSync(path,'utf8');let after=before;
 if(!after.includes(include)){
  if(path.endsWith('dune_accounts.php')){
   const guard="if (!defined('IN_MYBB')) die('Direct access denied');";
   if(!after.includes(guard))throw Error('Native direct-access guard missing');
   after=after.replace(guard,guard+'\n'+include);
  }else after=after.replace('<?php','<?php\n'+include);
 }
 if(after.includes(legacy))after=after.replace(legacy,'dune_forum_group_for($identity)');
 else if(!after.includes('dune_forum_group_for($identity)'))throw Error('Unexpected native group mapper: '+path);
 if(path.endsWith('dune_accounts.php')){
  if(after.includes(oldGuard))after=after.replace(oldGuard,'!dune_forum_can_admin($identity)');
  else if(!after.includes('!dune_forum_can_admin($identity)'))throw Error('Unexpected native admin guard');
 }
 edits.push({path,before,after,kind:'php'});
}
const before=readFileSync(seals,'utf8');let after=before.replace("forumGroup=access.nodes.includes('web.ranks.manage')?4:","forumGroup=access.nodes.includes('web.forum.admin')?4:");
if(!after.includes("forumGroup=access.nodes.includes('web.forum.admin')?4:"))throw Error('Unexpected native forum projection');
edits.push({path:seals,before,after,kind:'node'});
mkdirSync(backup,{recursive:true,mode:0o700});
const helperSource=readFileSync(helper,'utf8');
writeFileSync(backup+'/forum-access.php',helperSource,{mode:0o600});
const lint=spawnSync('/usr/bin/php',['-l',backup+'/forum-access.php'],{encoding:'utf8'});if(lint.status!==0)throw Error('Forum helper failed lint');
for(const [index,edit]of edits.entries()){
 const candidate=backup+'/'+index+'-'+basename(edit.path);writeFileSync(candidate,edit.after,{mode:0o600});
 const result=spawnSync(edit.kind==='php'?'/usr/bin/php':process.execPath,edit.kind==='php'?['-l',candidate]:['--check',candidate],{encoding:'utf8'});
 if(result.status!==0)throw Error('Candidate syntax failed: '+edit.path);
}
copyFileSync(helper,root+'/forum-access.php');
for(const [index,edit]of edits.entries())if(edit.before!==edit.after){writeFileSync(backup+'/'+index+'-original-'+basename(edit.path),edit.before,{mode:0o600});writeFileSync(edit.path,edit.after);}
console.log('Explicit web.forum.admin mapping installed; no ranks or user memberships changed. Backup: '+backup);
