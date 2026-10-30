from pathlib import Path
import re,uuid,json,hashlib
import pymysql
suffix=uuid.uuid4().hex[:12];routine='qa_dune_case_'+suffix;prefix='qa_case_'+suffix+'_'
source=Path('/root/game-ticket-context-source.sql').read_text()
start=source.index('CREATE DEFINER=`website`@`%` PROCEDURE dune_case_action_lock(')
end=source.index('\nEND//',start)+len('\nEND')
body=source[start:end].replace('PROCEDURE dune_case_action_lock(','PROCEDURE website.'+routine+'(',1)
columns={
 'site_accounts':'id,role','site_game_links':'user_id,minecraft_uuid,linked_at','cp_account_ranks':'user_id,rank_key',
 'cp_ranks':'rank_key,permissions_json,version','cp_rank_game_permissions':'rank_key,permissions_json,version',
 'drsupport_staff':'staff_id,username,isactive,dept_id,assigned_only,onvacation','drsupport_ticket':'ticket_id,staff_id,dept_id,status_id,user_id,user_email_id',
 'drsupport_ticket_status':'id,state','drsupport_department':'id,name,flags','drsupport_staff_dept_access':'staff_id,dept_id',
 'drsupport_team_member':'staff_id,team_id','dune_game_ticket_context':'ticket_id,version,suspect_uuid',
 'drsupport_user_email':'id,user_id,address','site_game_identities':'minecraft_uuid,first_linked_at',
 'account_audit':'id,actor_id,action,created_at','dune_game_ticket_context_events':'id,ticket_id,actor_uuid,action,to_version'}
tables={name:prefix+name for name in columns}
for original,replacement in tables.items():body=body.replace('website.'+original,'website.'+replacement)
# All SQL table reads must be QA names. The body is READS SQL DATA and may not mutate any table.
without_comments=re.sub(r'--[^\n]*','',body)
assert not re.search(r'\b(?:INSERT|DELETE|ALTER|TRUNCATE|DROP)\b|\bREPLACE\s+INTO\b',without_comments,re.I)
assert not re.search(r'(?<!FOR )\bUPDATE\b',without_comments,re.I)
references=re.findall(r'\b(?:FROM|JOIN)\s+(website\.\w+)',body,re.I)
assert references and all(ref.split('.')[1] in tables.values() for ref in references),references
assert 'COMMIT' not in body and 'ROLLBACK' not in body
Path('/root/'+routine+'.sql').write_text(body)
kwargs=dict(read_default_file='/root/dune-dump.cnf',database='website',charset='utf8mb4',autocommit=False,cursorclass=pymysql.cursors.DictCursor)
def connect():return pymysql.connect(**kwargs)
conn=connect();witness=connect();checks=[]
def check(value,label):
 if not value:raise AssertionError(label)
 checks.append(label)
def snapshot():
 witness.commit();result={}
 with witness.cursor() as cur:
  for name,fields in columns.items():
   cur.execute('SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema=%s AND table_name=%s',('website',name))
   if not cur.fetchone()['n']:continue
   expressions=','.join('COALESCE(CAST(`'+field+'` AS CHAR),\'<NULL>\')' for field in fields.split(','))
   cur.execute('SELECT COUNT(*) AS n,COALESCE(SUM(CRC32(CONCAT_WS(\'|\','+expressions+'))),0) AS digest FROM website.`'+name+'`')
   row=cur.fetchone();result[name]=(int(row['n']),str(row['digest']))
 return result
before=snapshot();created=False
try:
 ddls={
 'site_accounts':'id CHAR(36) PRIMARY KEY,username VARCHAR(40),role VARCHAR(40)',
 'site_game_links':'user_id CHAR(36) PRIMARY KEY,minecraft_uuid CHAR(36) UNIQUE,linked_at BIGINT',
 'cp_account_ranks':'user_id CHAR(36),rank_key VARCHAR(40),PRIMARY KEY(user_id,rank_key)',
 'cp_ranks':'rank_key VARCHAR(40) PRIMARY KEY,permissions_json LONGTEXT,rank_level INT,kind VARCHAR(16),version INT',
 'cp_rank_game_permissions':'rank_key VARCHAR(40) PRIMARY KEY,permissions_json LONGTEXT,version INT',
 'drsupport_staff':'staff_id INT PRIMARY KEY,username VARCHAR(40) UNIQUE,isactive INT,dept_id INT,assigned_only INT,onvacation INT',
 'drsupport_ticket':'ticket_id BIGINT PRIMARY KEY,staff_id INT,team_id INT,dept_id INT,user_id INT,user_email_id INT,topic_id INT,status_id INT',
 'drsupport_ticket_status':'id INT PRIMARY KEY,state VARCHAR(16)',
 'drsupport_department':'id INT PRIMARY KEY,name VARCHAR(128),flags INT',
 'drsupport_staff_dept_access':'staff_id INT,dept_id INT,PRIMARY KEY(staff_id,dept_id)',
 'drsupport_team_member':'staff_id INT,team_id INT,PRIMARY KEY(staff_id,team_id)',
 'dune_game_ticket_context':'ticket_id BIGINT PRIMARY KEY,version BIGINT,suspect_uuid CHAR(36),last_action VARCHAR(24),last_actor_uuid CHAR(36),last_from_version BIGINT',
 'drsupport_user_email':'id INT PRIMARY KEY,user_id INT,address VARCHAR(200)',
 'site_game_identities':'minecraft_uuid CHAR(36) PRIMARY KEY,first_linked_at BIGINT'}
 with conn.cursor() as cur:
  for name,ddl in ddls.items():cur.execute('CREATE TEMPORARY TABLE website.`'+tables[name]+'`('+ddl+') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci')
  cur.execute(body);created=True
 actor='00000000-0000-0000-0009-01f12c345678';creator='10000000-0000-4000-8000-000000000002';suspect='20000000-0000-4000-8000-000000000003';newcreator='30000000-0000-4000-8000-000000000004'
 account='11111111-1111-4111-8111-111111111111';owner='22222222-2222-4222-8222-222222222222'
 def sql(query,args=()):
  for name in sorted(tables,key=len,reverse=True):query=re.sub(r'\b'+name+r'\b',tables[name],query)
  with conn.cursor() as cur:cur.execute(query,args);return cur.fetchall() if cur.description else cur.rowcount
 perms=json.dumps(['web.tickets.staff','web.tickets.manage','web.tickets.support'])
 sql('INSERT INTO site_accounts VALUES(%s,\'Actor\',\'test_support\'),(%s,\'Creator\',\'user\')',(account,owner))
 sql('INSERT INTO site_game_links VALUES(%s,%s,1),(%s,%s,1)',(account,actor,owner,creator))
 sql('INSERT INTO cp_ranks VALUES(\'test_support\',%s,50,\'access\',1),(\'user\',\'[]\',10,\'access\',1)',(perms,))
 sql('INSERT INTO cp_rank_game_permissions VALUES(\'test_support\',\'["dune.admin"]\',1)')
 sql('INSERT INTO drsupport_staff VALUES(7,%s,1,1,0,0)',(account.replace('-',''),))
 sql("INSERT INTO drsupport_department VALUES(1,'Support',4),(2,'Legal',4)")
 sql("INSERT INTO drsupport_ticket_status VALUES(1,'open'),(2,'closed'),(3,'deleted')")
 sql('INSERT INTO drsupport_ticket VALUES(42,0,0,1,9,9,1,1)')
 sql('INSERT INTO dune_game_ticket_context VALUES(42,1,NULL,NULL,NULL,NULL)')
 sql('INSERT INTO drsupport_user_email VALUES(9,9,%s)',(owner+'@accounts.dunerealms.invalid',))
 sql('INSERT INTO site_game_identities VALUES(%s,1)',(suspect,));conn.commit()
 def call(action='read',version=None,identity=actor):
  with conn.cursor() as cur:
   cur.execute('CALL website.'+routine+'(%s,42,%s,%s)',(identity,version,action));rows=cur.fetchall()
   while cur.nextset():pass
   assert len(rows)==1;return rows[0]
 def denied(label,action='read',version=None,identity=actor,code='case_denied'):
  try:call(action,version,identity);raise AssertionError('Unexpected authority: '+label)
  except pymysql.MySQLError as e:check(e.args[1]==code,label)
 row=call();check(row['creator_uuid']==creator and row['can_take']==1 and row['can_set_suspect']==0 and row['can_edit_inventory']==0,'visible unassigned case: creator link and Take only')
 denied('unassigned high-risk denied','teleport_creator',1);conn.rollback()
 sql('UPDATE drsupport_ticket SET staff_id=7 WHERE ticket_id=42');row=call();check(row['assigned_agent_uuid']==actor and row['can_set_suspect']==1,'current native assignment and actor identity')
 row=call('teleport_creator',1);check(row['target_uuid']==creator and row['can_teleport']==1,'creator teleport exact current UUID')
 denied('suspect inspection unavailable without verified suspect','inspect_inventory',1)
 sql('UPDATE dune_game_ticket_context SET suspect_uuid=%s WHERE ticket_id=42',(suspect,));row=call('inspect_inventory',1);check(row['target_uuid']==suspect and row['can_inspect_inventory']==1,'verified suspect exact inspection target')
 row=call('edit_inventory',1);check(row['can_edit_inventory']==1,'assigned admin inventory edit authority')
 row=call('adjust_economy',1);check(row['can_adjust_economy']==1,'case economy gate available; separate bank gate still required')
 row=call('edit_faction',1);check(row['can_edit_faction']==1 and row['target_uuid']==suspect,'assigned case faction edit exact suspect and existing admin authority')
 denied('stale version is conflict','inspect_inventory',2,code='case_conflict')
 denied('unknown action is invalid','anything',1,code='case_invalid')
 denied('missing native version is invalid','inspect_inventory',None,code='case_invalid')
 denied('unknown actor has no account authority',identity='40000000-0000-4000-8000-000000000005')
 sql('UPDATE cp_rank_game_permissions SET permissions_json=\'[]\'');row=call();check(row['can_set_suspect']==1 and row['can_edit_inventory']==0,'assignment does not invent game administrator authority');denied('game permission revoked immediately','edit_inventory',1)
 conn.rollback();row=call();check(row['assigned_staff_id']==0 and row['suspect_uuid'] is None,'procedure never commits caller changes; rollback preserves fixture')
 sql('UPDATE drsupport_ticket SET staff_id=7,dept_id=2');sql('INSERT INTO drsupport_staff_dept_access VALUES(7,2)');denied('native Legal membership cannot bypass current central department')
 conn.rollback();sql('UPDATE drsupport_staff SET assigned_only=1');denied('assigned-only agent cannot enumerate unassigned department')
 conn.rollback();sql('UPDATE drsupport_staff SET isactive=0');denied('inactive native agent denied')
 conn.rollback();sql('UPDATE cp_ranks SET permissions_json=\'[]\' WHERE rank_key=\'test_support\'');denied('central staff permission removal denied')
 conn.rollback();sql('UPDATE site_game_links SET minecraft_uuid=%s WHERE user_id=%s',(newcreator,owner));row=call();check(row['creator_uuid']==newcreator,'creator relink follows current verified UUID')
 conn.rollback();sql('UPDATE site_game_links SET minecraft_uuid=%s WHERE user_id=%s',(newcreator,account));denied('actor relink immediately revokes old UUID')
 conn.rollback();sql('UPDATE drsupport_ticket SET staff_id=7,status_id=2');row=call();check(not row['can_take'] and not row['can_set_suspect'] and not row['can_teleport'],'closed case readable but cannot mutate or act');denied('closed case high-risk denied','teleport_creator',1)
 conn.rollback();sql('UPDATE drsupport_staff SET onvacation=1');check(not call()['can_take'],'vacation agent cannot take a case')
 conn.rollback();sql('UPDATE drsupport_department SET flags=20 WHERE id=1');sql('UPDATE drsupport_staff SET dept_id=2');sql('INSERT INTO drsupport_staff_dept_access VALUES(7,1)');check(not call()['can_take'],'native primary-only assignment restriction preserved')
 conn.rollback();check(snapshot()==before,'readonly production witness counts/digests unchanged')
 print(json.dumps({'result':'PASS','checks':len(checks),'checkNames':checks,'procedure':routine,'productionWitnessUnchanged':True,'productionRoutineInvoked':False,'productionTriggerInstalled':False,'fixtureTables':'connection-private TEMPORARY QA names','triggerQa':'not installed; MySQL cannot attach triggers to TEMPORARY tables'}))
finally:
 conn.rollback()
 if created:
  with conn.cursor() as cur:cur.execute('DROP PROCEDURE IF EXISTS website.'+routine)
  conn.commit()
 conn.close();witness.close()
 # Keep only source evidence, never a stored routine; TEMP tables vanished with connection.


