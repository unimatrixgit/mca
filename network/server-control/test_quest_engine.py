import unittest
from unittest.mock import MagicMock
import quest_engine as q

class QuestTests(unittest.TestCase):
    def config(self,**changes):
        return dict(key='buried-thunder',title='Buried Thunder',description='Survive the actual sandworm encounter.',enabled=True,hidden=True,intervalSeconds=3600,surviveSeconds=300,rewardDunemarks=0,version=1,actor='Moderator',**changes)
    def database(self,reads=(),rows=()):
        connection=MagicMock();connection.__enter__.return_value=connection
        cursor=MagicMock();cursor.__enter__.return_value=cursor;connection.cursor.return_value=cursor
        cursor.fetchone.side_effect=reads;cursor.fetchall.return_value=list(rows);cursor.rowcount=1
        return MagicMock(return_value=connection),connection,cursor
    def test_bounds_fixed_rewards_and_unknown_fields_are_rejected_before_any_database_access(self):
        connect=MagicMock()
        for changes in ({'intervalSeconds':3599},{'intervalSeconds':604801},{'surviveSeconds':59},{'surviveSeconds':1801},
                        {'rewardDunemarks':1000},{'cap':10},{'engine':'fake-payout'},{'version':-1},{'enabled':1},
                        {'key':'quests;drop'},{'actor':'staff\nstop'},{'title':'x'}):
            status,_=q.dispatch(connect,'POST','/quests/definitions',body={**self.config(),**changes})
            self.assertEqual(status,400)
        connect.assert_not_called()
    def test_player_request_is_exact_uuid_bound_and_never_reads_unassigned_hidden_definitions(self):
        player='00000000-0000-4000-8000-000000000001'
        connect,connection,cursor=self.database(rows=[{'id':'event','title':'Discovered title','status':'assigned'}])
        status,body=q.dispatch(connect,'GET','/quests/player',{'uuid':[player]})
        self.assertEqual(status,200);self.assertEqual(body['events'][0]['status'],'assigned')
        sql,params=cursor.execute.call_args.args;self.assertEqual(params,(player,));self.assertIn('WHERE player_uuid=%s',sql)
        self.assertIn("status IN ('assigned','completed','failed','discovered')",sql);self.assertIn('deadline_at IS NOT NULL',sql);self.assertNotIn('dune_quest_definitions',sql);self.assertNotIn('worm_uuid',sql);self.assertNotIn('reason',sql)
        for args in ({'uuid':['bad']},{'uuid':[player,player]},{'uuid':[player],'url':['https://attacker.test']}):
            self.assertIn(q.dispatch(connect,'GET','/quests/player',args)[0],(400,404))
    def test_stale_version_cannot_edit_hidden_definition_or_engine_schedule(self):
        connect,connection,cursor=self.database(reads=[{'version':2,'enabled':True,'interval_seconds':3600,'next_run_at':1234},{'now':9999}])
        status,_=q.dispatch(connect,'POST','/quests/definitions',body=self.config())
        self.assertEqual(status,409);connection.rollback.assert_called_once();connection.commit.assert_not_called()
        self.assertFalse(any(c.args[0].startswith('UPDATE') for c in cursor.execute.call_args_list))
    def test_valid_configuration_update_keeps_current_schedule_and_binds_staff_text(self):
        connect,connection,cursor=self.database(reads=[{'version':1,'enabled':True,'interval_seconds':3600,'next_run_at':1234},{'now':9999}])
        data={**self.config(),'description':"A real player's discovery, with ' quotes."}
        status,body=q.dispatch(connect,'POST','/quests/definitions',body=data)
        self.assertEqual(status,200);self.assertEqual(body['version'],2);self.assertEqual(body['cap'],2);self.assertEqual(body['rewardDunemarks'],0)
        statement=[c for c in cursor.execute.call_args_list if c.args[0].startswith('UPDATE')][0]
        self.assertNotIn(data['description'],statement.args[0]);self.assertIn(data['description'],statement.args[1]);self.assertEqual(statement.args[1][6],1234)
        connection.commit.assert_called_once()
    def test_reenabled_or_retimed_quest_gets_a_fresh_full_interval(self):
        connect,connection,cursor=self.database(reads=[{'version':1,'enabled':False,'interval_seconds':3600,'next_run_at':0},{'now':1000}])
        self.assertEqual(q.dispatch(connect,'POST','/quests/definitions',body=self.config())[0],200)
        update=[c for c in cursor.execute.call_args_list if c.args[0].startswith('UPDATE')][0]
        self.assertEqual(update.args[1][6],3601000)
    def test_new_definition_requires_version_zero_and_records_supported_engine_only(self):
        connect,connection,cursor=self.database(reads=[None,{'now':1000},{'total':1}])
        status,body=q.dispatch(connect,'POST','/quests/definitions',body={**self.config(),'version':0})
        self.assertEqual(status,200);self.assertEqual(body['version'],1)
        insert=[c for c in cursor.execute.call_args_list if c.args[0].startswith('INSERT')][0]
        self.assertIn("'worm-survival'",insert.args[0]);connection.commit.assert_called_once()
    def test_admin_definitions_are_explicitly_separate_from_player_history(self):
        connect,connection,cursor=self.database(rows=[{'key':'buried-thunder','enabled':1,'hidden':1}])
        status,body=q.dispatch(connect,'GET','/quests/definitions')
        self.assertEqual(status,200);self.assertTrue(body['definitions'][0]['hidden']);self.assertEqual(body['cap'],2)
        self.assertEqual(q.dispatch(connect,'POST','/quests/events',body={'status':'completed'})[0],404)
        self.assertEqual(q.dispatch(connect,'GET','/quests/definitions',{'uuid':['someone']})[0],404)

if __name__=='__main__':unittest.main()
