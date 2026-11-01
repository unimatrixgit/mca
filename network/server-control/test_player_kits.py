import unittest
from unittest.mock import MagicMock, patch
import player_kits as kits

OWNER='00000000-0000-0000-0009-01f09764757f'
OTHER='49d2fe76-7977-4d49-bd39-e48fc8c648b8'

class OfficeBlueprintTests(unittest.TestCase):
    def state(self):
        return {'votes': {'emperor': {'12': {'tally': {OWNER: 5}}}, 'house': {'12': {'Atreides': {'tally': {OWNER: 3}}}}}, 'appointments': {'water': {'week': 12, 'emperor': OWNER, 'house': 'Atreides'}}}

    def data(self):
        return {'uuid': OWNER, 'office': 'emperor', 'term': 'emperor:12:'+OWNER, 'version': 0, 'name': 'Imperial Regalia', 'color': '#123abc', 'lore': ['Water is the gift of life.']}

    def test_emperor_floodgate_and_matching_house_appointment(self):
        offices=kits.offices(self.state(),12,OWNER,'Atreides')
        self.assertEqual(offices['emperor'],'emperor:12:'+OWNER)
        self.assertEqual(offices['water-steward:Atreides'],'water-steward:Atreides:12:'+OWNER+':'+OWNER)
        self.assertEqual(len(offices),3)

    def test_expired_term_and_non_elected_member_have_no_rights(self):
        self.assertEqual(kits.offices(self.state(),13,OWNER,'Atreides'),{})
        self.assertEqual(kits.offices(self.state(),12,OTHER,'Atreides'),{})
        self.assertEqual(set(kits.offices(self.state(),12,OWNER,'Ordos')),{'emperor'})

    def test_stale_emperor_invalidates_appointment(self):
        state=self.state();state['appointments']['water']['emperor']=OTHER
        self.assertNotIn('water-steward:Atreides',kits.offices(state,12,OWNER,'Atreides'))

    def test_tied_votes_match_lexical_game_rule(self):
        self.assertEqual(kits.winner({'tally':{OTHER:5,OWNER:5}}),OWNER)

    def test_cosmetics_only_reject_privilege_nbt_and_malformed_fields(self):
        data=self.data();self.assertEqual(kits.validate(data)['color'],'#123ABC')
        for mutation in ({'commands':['op x']},{'quality':'legendary'},{'name':'<script>'},{'name':'§kHidden'},{'lore':['x']*7},{'version':True},{'color':'red'},{'office':'owner'},{'uuid':'unimatrix999'}):
            with self.subTest(mutation=mutation),self.assertRaises(ValueError):kits.validate({**data,**mutation})

    def connection(self,row):
        connection=MagicMock();cursor=connection.cursor.return_value.__enter__.return_value
        cursor.fetchone.return_value=row;cursor.rowcount=1
        connect=MagicMock();connect.return_value.__enter__.return_value=connection
        return connect,connection,cursor

    def test_revoked_office_rolls_back_without_blueprint_write(self):
        connect,connection,cursor=self.connection(None)
        with patch.object(kits,'authority',return_value={}):
            with self.assertRaises(PermissionError):kits.save(connect,self.data())
        connection.rollback.assert_called_once();connection.commit.assert_not_called();cursor.execute.assert_not_called()

    def test_stale_revision_rolls_back(self):
        connect,connection,cursor=self.connection({'version':3})
        data=self.data()
        with patch.object(kits,'authority',return_value={'emperor':data['term']}):
            with self.assertRaises(kits.Conflict):kits.save(connect,data)
        connection.rollback.assert_called_once();connection.commit.assert_not_called()
        self.assertEqual(cursor.execute.call_count,1)

    def test_first_definition_commits_and_never_resets_claim_receipt(self):
        connect,connection,cursor=self.connection(None);data=self.data()
        with patch.object(kits,'authority',return_value={'emperor':data['term']}) as authority:
            self.assertEqual(kits.save(connect,data)['version'],1)
        authority.assert_called_once_with(cursor,OWNER,True)
        connection.commit.assert_called_once()
        sql=' '.join(call.args[0] for call in cursor.execute.call_args_list)
        self.assertNotIn('dune_office_kit_claims',sql)
        self.assertNotIn('ON DUPLICATE',sql)

if __name__=='__main__':unittest.main()
