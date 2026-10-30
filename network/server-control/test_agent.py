import json
import threading
import unittest
from unittest.mock import patch, MagicMock
from contextlib import ExitStack, contextmanager
from types import SimpleNamespace
import stat
from pathlib import Path
from tempfile import TemporaryDirectory
from http.server import ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import agent

class AgentTests(unittest.TestCase):
    @contextmanager
    def mode(self, mode):
        agent.configure(mode)
        agent.JOBS.clear();agent.LAST.clear()
        try:
            yield
        finally:
            agent.configure('legacy')
            agent.JOBS.clear();agent.LAST.clear()
    @classmethod
    def setUpClass(cls):
        agent.SECRET = 't' * 64
        cls.http = ThreadingHTTPServer(('127.0.0.1', 0), agent.Handler)
        threading.Thread(target=cls.http.serve_forever, daemon=True).start()
        cls.url = 'http://127.0.0.1:' + str(cls.http.server_port)

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()

    def request(self, path, body=None, token='t' * 64):
        data = None if body is None else json.dumps(body).encode()
        request = Request(self.url + path, data=data, headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
        try:
            with urlopen(request) as response:
                return response.status, json.load(response)
        except HTTPError as response:
            return response.code, json.load(response)

    def test_authentication_and_fixed_log_services(self):
        self.assertEqual(self.request('/servers', token='wrong')[0], 401)
        self.assertEqual(self.request('/logs?server=ssh')[0], 404)
        self.assertEqual(self.request('/logs?server=arrakis&server=proxy')[0], 404)

    def test_invalid_actions_never_start_worker(self):
        with patch.object(agent, 'perform') as perform:
            for data in [dict(server='ssh', action='restart', actor='owner'), dict(server='arrakis', action='shell', actor='owner'), dict(server='arrakis', action='command', command='list\nstop', actor='owner'), dict(server='proxy', action='command', command='list', actor='owner')]:
                self.assertEqual(self.request('/action', data)[0], 400)
            perform.assert_not_called()

    def test_accepted_action_and_duplicate_block(self):
        agent.JOBS.clear()
        agent.LAST.clear()
        with patch.object(agent, 'perform') as perform:
            data = dict(server='arrakis', action='restart', actor='owner')
            self.assertEqual(self.request('/action', data)[0], 202)
            self.assertEqual(self.request('/action', data)[0], 409)
            perform.assert_called_once_with('arrakis', 'restart', '', 'owner')

    def test_redacts_credentials(self):
        self.assertNotIn('hunter2', agent.scrub('rcon.password=hunter2'))
        self.assertNotIn('private-session', agent.scrub('https://luckperms.net/editor/private-session'))
        self.assertNotIn('private-code', agent.scrub('issued server command: /duneess:linkwebsite private-code'))
        self.assertNotIn('reward-code', agent.scrub('/communitycode reward-code'))
        self.assertNotIn('view-token', agent.scrub('/dunemenus action view-token 2 1'))

    def test_luckperms_routes_authenticate_and_reject_injected_commands(self):
        self.assertEqual(self.request('/luckperms/groups', token='wrong')[0], 401)
        with patch.object(agent, 'permission_command') as command:
            for text in ['op attacker', 'lp applyedits abc\nstop', 'lp trusteditor a b']:
                self.assertEqual(self.request('/luckperms/apply', {'actor':'owner','command':text})[0],400)
            command.assert_not_called()

    def test_editor_accepts_only_fixed_operation_and_attributes_actor(self):
        with patch.object(agent, 'permission_command', return_value=(200,{'url':'https://luckperms.net/editor/test-session'})) as command:
            self.assertEqual(self.request('/luckperms/editor', {'actor':'owner','command':'op attacker'})[0],200)
            command.assert_called_once_with('lp editor groups', True)

    def ban_request(self, **changes):
        return dict(key='arrakis:00000000-0000-4000-8000-000000000001', action='remove',
                    created='2026-10-29 00:00:00 +0000', actor='moderator', reason='Appeal approved', **changes)

    def test_ban_action_authentication_and_injection_rejected(self):
        data=self.ban_request()
        self.assertEqual(self.request('/bans/action',data,token='wrong')[0],401)
        with patch.object(agent,'local_ban_entries') as entries, patch.object(agent.os,'open') as console:
            for changes in [dict(key='global:'+data['key'].split(':')[1]),dict(key=data['key']+'\nstop'),
                            dict(created=''),dict(action='edit'),dict(actor='mod\nstop'),dict(reason='ok\nstop'),
                            dict(key='arrakis:'+'-'*36)]:
                invalid={**data,**changes}
                self.assertEqual(self.request('/bans/action',invalid)[0],400)
            entries.assert_not_called();console.assert_not_called()

    def test_ban_action_stale_snapshot_and_unavailable_source_fail_closed(self):
        data=self.ban_request();entry={'uuid':data['key'].split(':')[1],'created':'newer'}
        with patch.object(agent,'local_ban_entries',return_value=[entry]),patch.object(agent.os,'open') as console:
            self.assertEqual(agent.remove_ban(data)[0],409);console.assert_not_called()
        with patch.object(agent,'local_ban_entries',side_effect=FileNotFoundError()),patch.object(agent.os,'open') as console:
            self.assertEqual(agent.remove_ban(data)[0],503);console.assert_not_called()

    def test_ban_action_already_absent_is_confirmed_only_after_readable_source(self):
        with patch.object(agent,'local_ban_entries',return_value=[]),patch.object(agent.os,'open') as console:
            status,body=agent.remove_ban(self.ban_request())
            self.assertEqual(status,200);self.assertTrue(body['confirmed']);self.assertTrue(body['absent']);console.assert_not_called()

    def ban_console(self, reads, clock):
        stack=ExitStack();stack.enter_context(patch.object(agent,'local_ban_entries',side_effect=reads))
        stack.enter_context(patch.object(agent,'run',return_value=SimpleNamespace(returncode=0,stdout='active\n')))
        stack.enter_context(patch.object(agent.time,'monotonic',side_effect=clock))
        stack.enter_context(patch.object(agent.os,'open',return_value=42))
        stack.enter_context(patch.object(agent.os,'O_NOFOLLOW',0,create=True))
        stack.enter_context(patch.object(agent.os,'O_NONBLOCK',0,create=True))
        stack.enter_context(patch.object(agent.os,'fstat',return_value=SimpleNamespace(st_mode=stat.S_IFIFO)))
        stack.enter_context(patch.object(agent.os,'close'))
        write=stack.enter_context(patch.object(agent.os,'write',side_effect=lambda fd,data:len(data)))
        return stack,write

    def test_ban_action_sends_stored_name_pardon_and_confirms_disappearance(self):
        agent.JOBS.clear();data=self.ban_request();player=data['key'].split(':')[1];entry={'uuid':player,'created':data['created'],'name':'Demo'}
        stack,write=self.ban_console([[entry],[entry],[]],[0,1])
        with stack:
            status,body=agent.remove_ban(data)
            self.assertEqual(status,200);self.assertTrue(body['confirmed']);write.assert_called_once_with(42,b'minecraft:pardon Demo\n')

    def test_stored_ban_name_cannot_inject_a_console_command(self):
        agent.JOBS.clear();data=self.ban_request()
        entry={'uuid':data['key'].split(':')[1],'created':data['created'],'name':'Demo\nstop'}
        stack,write=self.ban_console([[entry],[entry]],[0])
        with stack:
            self.assertEqual(agent.remove_ban(data)[0],409)
            write.assert_not_called()

    def test_ban_action_unconfirmed_removal_never_returns_success(self):
        agent.JOBS.clear();data=self.ban_request();entry={'uuid':data['key'].split(':')[1],'created':data['created'],'name':'Demo'}
        stack,write=self.ban_console([[entry],[entry]],[0,13])
        with stack:
            status,body=agent.remove_ban(data)
            self.assertEqual(status,504);self.assertNotIn('confirmed',body);write.assert_called_once()

    def test_server_restart_is_blocked_while_ban_action_running(self):
        agent.BAN_LOCK.acquire()
        try:
            with patch.object(agent,'perform') as perform:
                self.assertEqual(self.request('/action',dict(server='arrakis',action='restart',actor='owner'))[0],409)
                perform.assert_not_called()
        finally:
            agent.BAN_LOCK.release()

    def test_unreadable_or_malformed_local_file_is_not_absence(self):
        with TemporaryDirectory() as folder,patch.dict(agent.BAN_FOLDERS,{'arrakis':folder}):
            file=Path(folder)/'banned-players.json'
            with self.assertRaises(RuntimeError):agent.local_ban_entries('arrakis')
            for text in ['{}','[null]','[{"uuid":"bad","created":"today"}]','[{"uuid":"00000000-0000-4000-8000-000000000001"}]']:
                file.write_text(text,encoding='utf-8')
                with self.assertRaises((RuntimeError,ValueError)):agent.local_ban_entries('arrakis')
            file.write_text('[]',encoding='utf-8');self.assertEqual(agent.local_ban_entries('arrakis'),[])

    def test_newer_ban_during_confirmation_remains_unconfirmed(self):
        agent.JOBS.clear();data=self.ban_request();entry={'uuid':data['key'].split(':')[1],'created':data['created'],'name':'Demo'}
        stack,write=self.ban_console([[entry],[entry],[{**entry,'created':'newer'}]],[0,1])
        with stack:
            status,body=agent.remove_ban(data)
            self.assertEqual(status,409);self.assertNotIn('confirmed',body);write.assert_called_once()

    def test_inactive_server_never_receives_pardon(self):
        data=self.ban_request();entry={'uuid':data['key'].split(':')[1],'created':data['created'],'name':'Demo'}
        with patch.object(agent,'local_ban_entries',return_value=[entry]),patch.object(agent,'run',return_value=SimpleNamespace(returncode=3,stdout='inactive')),patch.object(agent.os,'open') as console:
            self.assertEqual(agent.remove_ban(data)[0],503);console.assert_not_called()

    def test_modes_choose_only_fixed_local_and_remote_units(self):
        with self.mode('gateway'):
            self.assertEqual(set(agent.SERVERS),{'lobby','proxy'})
            self.assertEqual(set(agent.REMOTE_SERVERS),{'arrakis','kaitain','giedi_prime'})
            self.assertEqual(set(agent.BAN_FOLDERS),{'lobby'})
        with self.mode('planets'):
            self.assertEqual(set(agent.SERVERS),{'arrakis','kaitain','giedi_prime'})
            self.assertEqual(agent.REMOTE_SERVERS,{})
            self.assertEqual(set(agent.BAN_FOLDERS),set(agent.PLANET_SERVERS))
            self.assertEqual(self.request('/logs?server=lobby')[0],404)
        with self.assertRaises(ValueError):agent.configure('https://attacker.test')

    def test_gateway_routes_fixed_remote_logs_actions_and_scrubs_output(self):
        with self.mode('gateway'),patch.object(agent,'perform') as local,patch.object(agent,'relay',return_value=(202,{'ok':True})) as relay:
            data={'server':'kaitain','action':'command','command':'list','actor':'owner','host':'https://attacker.test','unit':'ssh'}
            self.assertEqual(self.request('/action',data)[0],202)
            relay.assert_called_once_with('/action',{'server':'kaitain','action':'command','command':'list','actor':'owner'})
            local.assert_not_called()
            relay.return_value=(200,{'log':'token=privatevalue\nhello'})
            status,body=self.request('/logs?server=giedi_prime')
            self.assertEqual(status,200);self.assertNotIn('privatevalue',body['log'])
            self.assertEqual(relay.call_args.args,('/logs?server=giedi_prime',))
            self.assertEqual(self.request('/logs?server=http://attacker.test')[0],404)
            relay.return_value=(200,{'ok':True})
            self.assertEqual(self.request('/action',data)[0],503,'ordinary200 is not an accepted queued action')

    def test_planet_action_replays_and_injected_targets_do_not_start_twice(self):
        with self.mode('planets'),patch.object(agent,'perform') as perform:
            data={'server':'giedi_prime','action':'restart','actor':'owner'}
            self.assertEqual(self.request('/action',data)[0],202)
            self.assertEqual(self.request('/action',data)[0],409)
            for server in ('lobby','proxy','ssh','giedi_prime;stop'):
                self.assertEqual(self.request('/action',{**data,'server':server})[0],400)
            perform.assert_called_once_with('giedi_prime','restart','','owner')

    def test_gateway_remote_ban_confirmation_requires_exact_key_and_flags(self):
        data=self.ban_request()
        with self.mode('gateway'),patch.object(agent,'relay') as relay,patch.object(agent,'local_ban_entries') as local,patch.object(agent.os,'open') as console:
            for result in ({'ok':True,'confirmed':True,'absent':True,'key':data['key']+'x'},
                           {'ok':True,'confirmed':False,'absent':True,'key':data['key']},
                           {'ok':True,'confirmed':True,'absent':False,'key':data['key']},
                           {'confirmed':True,'absent':True,'key':data['key']}):
                relay.return_value=(200,result)
                status,body=agent.remove_ban(data);self.assertEqual(status,503);self.assertNotIn('confirmed',body)
            relay.return_value=(200,{'ok':True,'confirmed':True,'absent':True,'key':data['key']})
            self.assertEqual(agent.remove_ban(data)[0],200)
            relay.assert_called_with('/bans/action',data,18)
            relay.return_value=(409,{'message':'newer ban'})
            self.assertEqual(agent.remove_ban(data)[0],409)
            local.assert_not_called();console.assert_not_called()

    def test_new_planet_ban_uses_local_stale_guard_and_fixed_name_pardon(self):
        data={**self.ban_request(),'key':'kaitain:00000000-0000-4000-8000-000000000001'}
        entry={'uuid':data['key'].split(':')[1],'created':data['created'],'name':'Demo'}
        with self.mode('planets'):
            with patch.object(agent,'local_ban_entries',return_value=[{**entry,'created':'newer'}]),patch.object(agent.os,'open') as console:
                self.assertEqual(agent.remove_ban(data)[0],409);console.assert_not_called()
            stack,write=self.ban_console([[entry],[entry],[]],[0,1])
            with stack:
                self.assertEqual(agent.remove_ban(data)[0],200)
                write.assert_called_once_with(42,b'minecraft:pardon Demo\n')

    def test_gateway_ban_listing_does_not_hide_failed_remote_sources(self):
        player='00000000-0000-4000-8000-000000000001'
        row={'scope':'giedi_prime','key':'giedi_prime:'+player,'uuid':player,'name':'Demo','operator':'mod','reason':'Test ban','created':'today','expires':'forever'}
        with self.mode('gateway'),patch.object(agent,'local_ban_entries',return_value=[]),patch.object(agent,'network_bans',return_value=[]),patch.object(agent,'remote_request') as remote:
            remote.return_value=(200,{'bans':[row],'sources':['arrakis','kaitain','giedi_prime']})
            result=agent.bans();self.assertEqual(result['bans'],[row]);self.assertEqual(set(result['sources']),{'lobby','arrakis','kaitain','giedi_prime','network'});self.assertTrue(result['globalConnected'])
            for result in ((503,{'bans':[]}), (200,{'bans':[],'sources':['arrakis']}),
                           (200,{'bans':[{**row,'scope':'lobby'}],'sources':['arrakis','kaitain','giedi_prime']})):
                remote.return_value=result
                self.assertEqual(self.request('/bans')[0],503)

    def test_gateway_status_keeps_local_services_and_marks_remote_failure(self):
        remote_rows=[{'id':key,'state':'active','detail':'running','memory':'123','job':None} for key in agent.PLANET_SERVERS]
        with self.mode('gateway'),patch.object(agent,'run',return_value=SimpleNamespace(stdout='ActiveState=active\nSubState=running\n',returncode=0)),patch.object(agent,'urlopen',side_effect=TimeoutError()),patch.object(agent,'remote_request',return_value=(200,{'servers':remote_rows})) as remote:
            result=agent.state();self.assertEqual(len(result['servers']),5);self.assertTrue(all(row['state']=='active' for row in result['servers']))
            remote.side_effect=TimeoutError()
            result=agent.state();self.assertEqual(sum(row['state']=='unavailable' for row in result['servers']),3)
            self.assertTrue(all(row['state']=='active' for row in result['servers'] if row['id'] in ('lobby','proxy')))

    def test_luckperms_gateway_relays_console_but_groups_stay_local(self):
        with self.mode('gateway'),patch.object(agent,'permission_command') as local,patch.object(agent,'relay',return_value=(200,{'url':'https://luckperms.net/editor/session123'})) as remote:
            self.assertEqual(self.request('/luckperms/editor',{'actor':'owner','command':'op attacker'})[0],200)
            remote.assert_called_once_with('/luckperms/editor',{'actor':'owner','command':'lp editor groups'},22);local.assert_not_called()
            remote.return_value=(200,{'url':'https://attacker.test/secret'})
            self.assertEqual(self.request('/luckperms/editor',{'actor':'owner'})[0],503)
            with patch.object(agent,'run',return_value=SimpleNamespace(stdout='[]',returncode=0)) as pg:
                self.assertEqual(self.request('/luckperms/groups')[0],200)
                self.assertIn('dunerealms_permissions',pg.call_args.args[0])
        with self.mode('planets'),patch.object(agent,'run') as pg:
            self.assertEqual(self.request('/luckperms/groups')[0],503);pg.assert_not_called()

    def test_remote_transport_uses_fixed_origin_no_redirects_and_response_cap(self):
        import io
        class Response(io.BytesIO):
            code=200
        with self.mode('gateway'),patch.object(agent,'build_opener') as opener:
            opener.return_value.open.return_value=Response(b'{"servers":[]}')
            self.assertEqual(agent.remote_request('/servers'),(200,{'servers':[]}))
            request=opener.return_value.open.call_args.args[0]
            self.assertEqual(request.full_url,'http://10.77.0.2:25573/servers')
            self.assertEqual(request.get_header('Authorization'),'Bearer '+'t'*64)
            self.assertIsNone(agent.NoRedirect().redirect_request(None,None,302,'',{},'https://attacker.test'))
            for target in ('https://attacker.test','/logs?server=lobby','/servers?url=https://attacker.test'):
                with self.assertRaises(ValueError):agent.remote_request(target)
            opener.return_value.open.return_value=Response(b'x'*262145)
            self.assertEqual(agent.relay('/servers')[0],503)

    def test_remote_authentication_and_bad_json_never_reach_relay(self):
        with self.mode('gateway'),patch.object(agent,'relay') as relay:
            self.assertEqual(self.request('/action',{'server':'arrakis','action':'restart','actor':'owner'},token='wrong')[0],401)
            for data in ([],['owner'],{'actor':['owner']},{'actor':'owner','command':'lp applyedits abc\nstop'}):
                self.assertEqual(self.request('/luckperms/apply',data)[0],400)
            relay.assert_not_called()

    def network_fixture(self, reads):
        connection=MagicMock();connection.__enter__.return_value=connection
        cursor=MagicMock();cursor.__enter__.return_value=cursor;connection.cursor.return_value=cursor
        cursor.fetchone.side_effect=reads;cursor.rowcount=1
        return connection,cursor

    def test_network_ban_removal_is_atomic_parameterized_and_confirms_exact_snapshot(self):
        data={**self.ban_request(),'key':'network:00000000-0000-4000-8000-000000000001','reason':"Reviewed reason with ' quote"}
        connection,cursor=self.network_fixture([{'player_uuid':data['key'].split(':')[1]},{'created_at':1793232000000},None])
        with self.mode('gateway'),patch.object(agent,'network_database',return_value=connection),patch.object(agent.os,'open') as console:
            status,body=agent.remove_ban(data);self.assertEqual(status,200);self.assertTrue(body['confirmed']);self.assertEqual(body['key'],data['key'])
            connection.commit.assert_called_once();console.assert_not_called()
            update=[c for c in cursor.execute.call_args_list if c.args[0].startswith('UPDATE')][0]
            self.assertNotIn(data['reason'],update.args[0]);self.assertEqual(update.args[1],('moderator',data['reason'],data['key'].split(':')[1],1793232000000))

    def test_network_stale_changed_absent_or_unavailable_records_never_invent_success(self):
        data={**self.ban_request(),'key':'network:00000000-0000-4000-8000-000000000001'}
        with self.mode('gateway'):
            for reads,status in [([{'player_uuid':'ok'},{'created_at':1793232000001}],409),
                                 ([{'player_uuid':'ok'},{'created_at':1793232000000},{'created_at':1793232000001}],409),
                                 ([{'player_uuid':'ok'},None],200)]:
                connection,cursor=self.network_fixture(reads)
                with patch.object(agent,'network_database',return_value=connection):
                    actual,body=agent.remove_ban(data);self.assertEqual(actual,status)
                    if status!=200:self.assertNotIn('confirmed',body)
                    if len(reads)==2:connection.commit.assert_not_called()
            with patch.object(agent,'network_database',side_effect=TimeoutError()):
                status,body=agent.remove_ban(data);self.assertEqual(status,503);self.assertNotIn('confirmed',body)
        with self.mode('planets'),patch.object(agent,'network_database') as connection:
            self.assertEqual(agent.remove_ban(data)[0],400);connection.assert_not_called()

    def test_network_feed_uses_same_uuid_and_millisecond_snapshot_as_removal(self):
        player='00000000-0000-4000-8000-000000000001'
        connection,cursor=self.network_fixture([])
        cursor.fetchall.return_value=[dict(id='00000000-0000-4000-8000-000000000009',player_uuid=player,player_name='Demo',actor_name='Moderator',reason='Recorded reason',created_at=1793232000123,expires_at=None)]
        with patch.object(agent,'network_database',return_value=connection):
            rows=agent.network_bans();self.assertEqual(rows[0]['key'],'network:'+player);self.assertEqual(rows[0]['created'],'2026-10-29T00:00:00.123Z');self.assertEqual(agent.network_created(rows[0]['created']),1793232000123)
        for created in ('today','2026-10-29T00:00:00.000001Z','2026-10-29T00:00:00'):
            with self.assertRaises(ValueError):agent.network_created(created)

    def test_luckperms_mysql_read_switch_is_explicit_and_never_silently_falls_back(self):
        connection,cursor=self.network_fixture([])
        cursor.fetchall.return_value=[{'name':'moderator','nodes':12}]
        with self.mode('gateway'),patch.dict(agent.os.environ,{'DUNE_LUCKPERMS_MYSQL':'1'}),patch.object(agent,'permissions_database',return_value=connection) as mysql,patch.object(agent,'run') as old:
            self.assertEqual(agent.permission_groups()['groups'],[{'name':'moderator','nodes':12}]);old.assert_not_called()
            mysql.side_effect=TimeoutError()
            self.assertEqual(self.request('/luckperms/groups')[0],503);old.assert_not_called()
        with self.mode('gateway'),patch.dict(agent.os.environ,{'DUNE_LUCKPERMS_MYSQL':'0'}),patch.object(agent,'permissions_database') as mysql,patch.object(agent,'run',return_value=SimpleNamespace(stdout='[]',returncode=0)) as old:
            self.assertEqual(agent.permission_groups()['groups'],[]);mysql.assert_not_called();old.assert_called_once()

    def test_quest_routes_keep_private_auth_and_gateway_mode(self):
        import quest_engine
        with self.mode('gateway'),patch.object(quest_engine,'dispatch',return_value=(200,{'events':[]})) as dispatch:
            self.assertEqual(self.request('/quests/player?uuid=00000000-0000-4000-8000-000000000001',token='wrong')[0],401)
            dispatch.assert_not_called()
            self.assertEqual(self.request('/quests/player?uuid=00000000-0000-4000-8000-000000000001')[0],200)
            self.assertEqual(dispatch.call_args.args[1:4],('GET','/quests/player',{'uuid':['00000000-0000-4000-8000-000000000001']}))
        with self.mode('planets'),patch.object(quest_engine,'dispatch') as dispatch:
            self.assertEqual(self.request('/quests/definitions')[0],404);dispatch.assert_not_called()

    def test_quest_database_failure_reports_unavailable_without_fabricating_state(self):
        import quest_engine
        with self.mode('gateway'),patch.object(quest_engine,'dispatch',side_effect=TimeoutError('private password')):
            status,body=self.request('/quests/definitions')
            self.assertEqual(status,503);self.assertNotIn('private password',str(body));self.assertNotIn('definitions',body)

if __name__ == '__main__':
    unittest.main()

