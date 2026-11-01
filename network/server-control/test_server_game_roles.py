import copy
import unittest
import server_game_roles as scoped
import managed_game_roles as global_roles
from test_managed_game_roles import Database, A, B, REV, NEXT

class ServerTeams(unittest.TestCase):
    def setUp(self):
        self.db = Database()
        self.body = {'revision': REV, 'actor': 'website-sync',
                     'ranks': [{'id': 'game_server_admin', 'permissions': {'dune.ban.local': True, 'worldedit.*': True, 'velocity.command.send': True}}],
                     'users': [{'uuid': A, 'memberships': [{'server': 'arrakis', 'rank': 'game_server_admin'}, {'server': 'proxy', 'rank': 'game_server_admin'}]}]}
    def run_snapshot(self, body=None, notify=lambda: None):
        return scoped.dispatch(self.db, body or self.body, notify)[0]
    def test_grants_and_memberships_are_both_contextual_and_proxy_filtered(self):
        self.assertEqual(self.run_snapshot(), 200)
        grants = self.db.rows('luckperms_group_permissions')
        self.assertFalse(any(r['server'] == 'global' for r in grants))
        self.assertEqual({r['permission'] for r in grants if r['server'] == 'proxy'}, {'velocity.command.send'})
        self.assertFalse(any(r['permission'].startswith('velocity.') for r in grants if r['server'] != 'proxy'))
        self.assertEqual({r['server'] for r in self.db.rows('luckperms_user_permissions')}, {'arrakis', 'proxy'})
    def test_rank_change_and_relink_remove_old_membership(self):
        self.run_snapshot()
        changed = copy.deepcopy(self.body); changed['revision'] = NEXT; changed['users'] = [{'uuid': B, 'memberships': [{'server': 'kaitain', 'rank': 'game_server_admin'}]}]
        self.assertEqual(self.run_snapshot(changed), 200)
        self.assertEqual([(r['uuid'], r['server']) for r in self.db.rows('luckperms_user_permissions')], [(B, 'kaitain')])
    def test_replay_stale_and_altered_revisions(self):
        self.assertEqual(self.run_snapshot(), 200); self.assertEqual(self.run_snapshot(), 200)
        self.assertEqual(len(self.db.rows('luckperms_user_permissions')), 2)
        changed = copy.deepcopy(self.body); changed['users'] = []; self.assertEqual(self.run_snapshot(changed), 409)
        changed['revision'] = NEXT; self.assertEqual(self.run_snapshot(changed), 200)
        self.assertEqual(self.run_snapshot(), 409)
    def test_no_network_economy_or_permission_administration_escalation(self):
        for node in ('*', 'luckperms.*', 'dune.admin', 'dune.ban.global', 'dune.unban.global', 'dunebank.staff.manage', 'duneitems.admin', 'dune.ban.protected'):
            body = copy.deepcopy(self.body); body['ranks'][0]['permissions'] = {node: True}
            self.assertEqual(self.run_snapshot(body), 400, node)
    def test_mod_cannot_acquire_admin_world_tools(self):
        body = copy.deepcopy(self.body); body['ranks'][0]['id'] = 'game_server_mod'
        self.assertEqual(self.run_snapshot(body), 400)
    def test_local_role_cannot_be_exported_globally(self):
        body = {'revision': REV, 'actor': 'website-sync', 'ranks': self.body['ranks'], 'users': []}
        self.assertEqual(global_roles.dispatch(self.db, body, True, lambda: None)[0], 400)
    def test_preserves_manual_world_context_and_global_roles(self):
        name = scoped.group_name('arrakis', 'game_server_admin')
        for group, server, world in ((name, 'arrakis', 'world_nether'), (name, 'global', 'global'), ('wrole_owners', 'global', 'global')):
            self.db.raw.execute("INSERT INTO luckperms_user_permissions(uuid,permission,value,server,world,expiry,contexts) VALUES(?,?,1,?,?,0,'{}')", (B, 'group.' + group, server, world))
        self.db.raw.commit(); self.assertEqual(self.run_snapshot(), 200)
        self.assertEqual(len([r for r in self.db.rows('luckperms_user_permissions') if r['uuid'] == B]), 3)
    def test_partial_failure_rolls_back_snapshot(self):
        self.run_snapshot(); before = self.db.rows('luckperms_user_permissions')
        changed = copy.deepcopy(self.body); changed['revision'] = NEXT
        self.db.fail = lambda sql: sql.startswith('INSERT INTO luckperms_user_permissions')
        self.assertEqual(self.run_snapshot(changed), 503)
        self.assertEqual(self.db.rows('luckperms_user_permissions'), before)
    def test_failed_broadcast_can_retry_without_duplicate_grants(self):
        def fail(): raise RuntimeError('offline')
        self.assertEqual(self.run_snapshot(notify=fail), 503)
        self.assertEqual(self.run_snapshot(), 200)
        self.assertEqual(len(self.db.rows('luckperms_user_permissions')), 2)
    def test_unknown_server_and_duplicate_membership_fail_closed(self):
        for server in ('global', 'arrakis;op', 'ARRAKIS'):
            body = copy.deepcopy(self.body); body['users'][0]['memberships'][0]['server'] = server
            self.assertEqual(self.run_snapshot(body), 400)
        body = copy.deepcopy(self.body); body['users'][0]['memberships'] *= 2
        self.assertEqual(self.run_snapshot(body), 400)

if __name__ == '__main__': unittest.main()
