import copy
import json
import re
import sqlite3
import unittest
import uuid
import managed_game_roles as roles

A='00000000-0000-0000-0009-01f12c345678'  # real-format Floodgate UUID; leading zero fields are valid
B='10000000-0000-0000-0000-000000000002'
REV='20000000-0000-4000-8000-000000000003'
NEXT='30000000-0000-4000-8000-000000000004'
class Cursor:
    def __init__(self, connection): self.connection=connection;self.raw=connection.raw.cursor()
    def __enter__(self): return self
    def __exit__(self,*args): self.raw.close()
    def execute(self,sql,args=()):
        self.connection.queries.append(sql)
        if self.connection.fail and self.connection.fail(sql): raise RuntimeError('Injected storage failure')
        sql=sql.replace('BINARY ','').replace('INSERT IGNORE','INSERT OR IGNORE').replace(' FOR UPDATE','')
        sql=re.sub(r'LEFT\((\w+),%s\)',r'substr(\1,1,%s)',sql).replace('%s','?')
        return self.raw.execute(sql,args)
    def fetchone(self):
        row=self.raw.fetchone();return dict(row) if row is not None else None
    def fetchall(self): return [dict(r) for r in self.raw.fetchall()]
class Database:
    def __init__(self):
        self.raw=sqlite3.connect(':memory:');self.raw.row_factory=sqlite3.Row
        self.raw.create_function('GET_LOCK',2,lambda *_:1);self.raw.create_function('RELEASE_LOCK',1,lambda *_:1)
        self.raw.executescript('''CREATE TABLE luckperms_groups(name VARCHAR(36) PRIMARY KEY);
CREATE TABLE luckperms_group_permissions(id INTEGER PRIMARY KEY AUTOINCREMENT,name VARCHAR(36),permission TEXT,value BOOLEAN,server TEXT,world TEXT,expiry INTEGER,contexts TEXT);
CREATE TABLE luckperms_user_permissions(id INTEGER PRIMARY KEY AUTOINCREMENT,uuid TEXT,permission TEXT,value BOOLEAN,server TEXT,world TEXT,expiry INTEGER,contexts TEXT);
CREATE TABLE luckperms_players(uuid TEXT PRIMARY KEY,username TEXT);
''');self.fail=None;self.queries=[];self.commits=0;self.rollbacks=0
    def __call__(self):return self
    def __enter__(self):return self
    def __exit__(self,*args):pass
    def cursor(self):return Cursor(self)
    def begin(self):self.raw.commit();self.raw.execute('BEGIN')
    def commit(self):self.raw.commit();self.commits+=1
    def rollback(self):self.raw.rollback();self.rollbacks+=1
    def rows(self,table):return [dict(r) for r in self.raw.execute('SELECT * FROM '+table)]
class ManagedRoleTests(unittest.TestCase):
    def setUp(self):self.db=Database();self.notifications=[]
    def notify(self):self.notifications.append(self.db.commits)
    def bank(self,**overrides):
        flags={node:False for node in roles.BANK_NODES};flags['dunebank.use']=True;flags.update(overrides)
        return {'uuid':A,'actor':'website-sync','permissions':flags}
    def teams(self,revision=REV):return {'revision':revision,'actor':'website-sync','ranks':[{'id':'web_support','permissions':{'dune.tickets.staff':True,'dunebank.staff.view':True,'dune.ban.local':False}}], 'users':[{'uuid':A,'ranks':['web_support']}]}
    def apply(self,data,teams=False):return roles.dispatch(self.db,data,teams,self.notify)
    def test_compat_four_flags_create_owned_36_character_group(self):
        status,body=self.apply(self.bank());self.assertEqual((status,body),(200,{'ok':True,'uuid':A}));name='wbn_'+uuid.UUID(A).hex
        self.assertEqual(len(name),36);self.assertEqual(self.db.rows('luckperms_groups'),[{'name':name}]);self.assertEqual([r['permission'] for r in self.db.rows('luckperms_group_permissions')],['dunebank.use']);self.assertEqual(self.notifications,[1])
    def test_compat_false_omits_grant_not_deny_and_is_idempotent(self):
        body=self.bank(**{'dunebank.staff.view':True});self.apply(body);self.apply(body);self.assertEqual(len(self.db.rows('luckperms_user_permissions')),1);self.assertEqual(len(self.db.rows('luckperms_group_permissions')),2)
        self.apply(self.bank());self.assertEqual([r['permission'] for r in self.db.rows('luckperms_group_permissions')],['dunebank.use']);self.assertTrue(all(r['value'] for r in self.db.rows('luckperms_group_permissions')))
    def test_compat_requires_all_four_boolean_flags(self):
        for change in ('missing','extra','integer','actor','uuid'):
            with self.subTest(change=change):
                data=self.bank()
                if change=='missing':data['permissions'].pop('dunebank.use')
                if change=='extra':data['permissions']['op']=True
                if change=='integer':data['permissions']['dunebank.use']=1
                if change=='actor':data['actor']='x\nlp user admin'
                if change=='uuid':data['uuid']=A.upper()
                # This Floodgate example contains hex letters, so uppercase is noncanonical.
                self.assertEqual(self.apply(data)[0],400)
    def seed_manual(self):
        self.db.raw.execute("INSERT INTO luckperms_groups(name) VALUES('manual')")
        self.db.raw.execute("INSERT INTO luckperms_groups(name) VALUES('wroleXmanual')")
        for node in ('group.dr_house_aerdrecht','manual.node','group.manual'):
            self.db.raw.execute("INSERT INTO luckperms_user_permissions(uuid,permission,value,server,world,expiry,contexts) VALUES(?,?,1,'global','global',0,'{}')",(A,node))
        self.db.raw.execute("INSERT INTO luckperms_user_permissions(uuid,permission,value,server,world,expiry,contexts) VALUES(?,?,1,'arrakis','global',0,'{}')",(A,'group.wrole_context'))
        self.db.raw.execute("INSERT INTO luckperms_group_permissions(name,permission,value,server,world,expiry,contexts) VALUES('wrole_web_support','context.manual',1,'global','global',0,'{\"server\":\"arrakis\"}')")
        self.db.raw.execute("INSERT INTO luckperms_group_permissions(name,permission,value,server,world,expiry,contexts) VALUES('wroleXmanual','untouched',1,'global','global',0,'{}')")
        self.db.raw.execute("INSERT INTO luckperms_players(uuid,username) VALUES(?,?)",(A,'VerifiedBedrock'));self.db.raw.commit()
    def test_full_snapshot_preserves_manual_contexts_factions_and_player_records(self):
        self.seed_manual();status,body=self.apply(self.teams(),True);self.assertEqual((status,body),(200,{'ok':True,'revision':REV}))
        nodes={r['permission'] for r in self.db.rows('luckperms_user_permissions')};self.assertTrue({'group.dr_house_aerdrecht','manual.node','group.manual','group.wrole_context','group.wrole_web_support'}<=nodes)
        self.assertEqual(len(self.db.rows('luckperms_players')),1);self.assertIn('context.manual',{r['permission'] for r in self.db.rows('luckperms_group_permissions')});self.assertIn('untouched',{r['permission'] for r in self.db.rows('luckperms_group_permissions')})
    def test_full_snapshot_replaces_owned_scope_and_cleans_old_verified_identity(self):
        self.apply(self.teams(),True);changed=self.teams(NEXT);changed['ranks'][0]['permissions']={'dune.tickets.view':True};changed['users']=[{'uuid':B,'ranks':['web_support']}];self.apply(changed,True)
        rows=self.db.rows('luckperms_user_permissions');self.assertEqual([r['uuid'] for r in rows],[B]);self.assertEqual([r['permission'] for r in self.db.rows('luckperms_group_permissions')],['dune.tickets.view'])
    def test_full_snapshot_removes_compat_and_prevents_future_compat_posts(self):
        self.apply(self.bank());self.apply(self.teams(),True);self.assertFalse(any(r['name'].startswith('wbn_') for r in self.db.rows('luckperms_groups')));self.assertFalse(any(r['permission'].startswith('group.wbn_') for r in self.db.rows('luckperms_user_permissions')));self.assertEqual(self.apply(self.bank())[0],409)
    def test_replay_rebroadcasts_without_duplicates_and_stale_revision_is_rejected(self):
        body=self.teams();self.apply(body,True);self.apply(body,True);self.assertEqual(len(self.db.rows('luckperms_user_permissions')),1);self.assertEqual(len(self.notifications),2)
        self.apply(self.teams(NEXT),True);self.assertEqual(self.apply(body,True)[0],409)
    def test_altered_revision_is_conflict(self):
        self.apply(self.teams(),True);changed=self.teams();changed['users']=[];self.assertEqual(self.apply(changed,True)[0],409)
    def test_notify_failure_commits_and_retry_rebroadcasts_same_snapshot(self):
        def offline():raise RuntimeError('Lobby offline')
        body=self.teams();self.assertEqual(roles.dispatch(self.db,body,True,offline)[0],503);self.assertEqual(len(self.db.rows('luckperms_user_permissions')),1);self.assertEqual(self.apply(body,True)[0],200);self.assertEqual(len(self.db.rows('luckperms_user_permissions')),1)
    def test_transaction_rolls_back_all_managed_changes_on_failure(self):
        self.apply(self.teams(),True);before=self.db.rows('luckperms_group_permissions');before_users=self.db.rows('luckperms_user_permissions');self.db.fail=lambda sql:sql.startswith('INSERT INTO luckperms_user_permissions')
        self.assertEqual(self.apply(self.teams(NEXT),True)[0],503);self.assertEqual(self.db.rows('luckperms_group_permissions'),before);self.assertEqual(self.db.rows('luckperms_user_permissions'),before_users)
    def test_rejects_unknown_nodes_wildcards_and_undeclared_memberships(self):
        variants=[]
        for node in ('*','luckperms.*','op','invented.node','worldedit.*'):
            body=self.teams();body['ranks'][0]['permissions']={node:True};variants.append(body)
        body=self.teams();body['users'][0]['ranks']=['owners'];variants.append(body)
        for body in variants:self.assertEqual(self.apply(body,True)[0],400)
    def test_only_network_administrators_may_receive_approved_plugin_wildcards(self):
        body=self.teams();body['ranks'][0]={'id':'web_net_admin','permissions':{'worldedit.*':True,'worldguard.*':True}};body['users'][0]['ranks']=['web_net_admin'];self.assertEqual(self.apply(body,True)[0],200)
    def test_duplicate_rank_user_or_membership_rejected(self):
        for kind in ('rank','user','membership'):
            body=self.teams()
            if kind=='rank':body['ranks'].append(copy.deepcopy(body['ranks'][0]))
            if kind=='user':body['users'].append(copy.deepcopy(body['users'][0]))
            if kind=='membership':body['users'][0]['ranks']*=2
            self.assertEqual(self.apply(body,True)[0],400)
    def test_uuid_strict_but_leading_zero_floodgate_allowed(self):
        self.assertEqual(roles.player_uuid(A),A)
        for value in (A.upper(),A.replace('-',''),'{'+A+'}','00000000-0000-0000-0000-000000000000','not-uuid',123):
            self.assertRaises(roles.Invalid,roles.player_uuid,value)
    def test_long_canonical_rank_group_mapping_stays_within_luckperms_width(self):
        key='custom_'+('a'*50);body=self.teams();body['ranks'][0]['id']=key;body['users'][0]['ranks']=[key];self.assertEqual(self.apply(body,True)[0],200);self.assertEqual(len(roles.group_name(key)),36);self.assertEqual(self.db.rows('dune_web_role_names')[0]['rank_id'],key)
    def test_payload_limits_unknown_fields_and_noncanonical_rank(self):
        for kind in ('ranks','users','extra','rank'):
            body=self.teams()
            if kind=='ranks':body['ranks']*=129
            if kind=='users':body['users']*=2049
            if kind=='extra':body['command']='op me'
            if kind=='rank':body['ranks'][0]['id']='bad-rank'
            self.assertEqual(self.apply(body,True)[0],400)
    def test_duplicate_json_keys_rejected(self):
        self.assertRaises(roles.Invalid,roles.strict_json,'{"actor":"website-sync","actor":"bad"}')
    def test_catalog_support_and_appeal_defaults_do_not_share_staff_support_gui(self):
        defaults={r['id']:r['permissions'] for r in roles.CATALOG['suggestedRoles']};self.assertTrue(defaults['web_support'].get('dune.tickets.staff'));self.assertTrue(defaults['game_support'].get('dune.tickets.staff'))
        for key in ('web_appeal','game_appeal','game_mod','web_legal','web_sales','web_forum','web_news'):
            self.assertNotIn('dune.tickets.staff',defaults[key])
    def test_group_mapping_collision_rolls_back_before_reassigning_other_rank(self):
        from unittest.mock import patch
        self.apply(self.teams(),True);before=self.db.rows('luckperms_group_permissions');body=self.teams(NEXT);body['ranks'][0]['id']='custom_role';body['users'][0]['ranks']=['custom_role']
        with patch('managed_game_roles.group_name',return_value='wrole_web_support'):
            self.assertEqual(self.apply(body,True)[0],409)
        self.assertEqual(self.db.rows('luckperms_group_permissions'),before)
    def test_busy_database_lock_rejects_without_managed_mutation(self):
        self.db.raw.create_function('GET_LOCK',2,lambda *_:0)
        self.assertEqual(self.apply(self.bank())[0],409);self.assertEqual(self.db.rows('luckperms_user_permissions'),[])
    def test_sql_scope_is_fixed_and_no_direct_manual_player_node_writes(self):
        self.seed_manual();self.apply(self.teams(),True);self.assertFalse(any('LIKE' in q for q in self.db.queries));self.assertTrue(all(r['permission'].startswith('group.') or r['permission']=='manual.node' for r in self.db.rows('luckperms_user_permissions')))
if __name__=='__main__':unittest.main()
