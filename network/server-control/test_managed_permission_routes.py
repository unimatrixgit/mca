import io
import json
import threading
import unittest
from http.server import ThreadingHTTPServer
from urllib.request import Request,urlopen
from urllib.error import HTTPError
from unittest.mock import patch
import agent
import managed_game_roles as roles

class ManagedRouteTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  agent.SECRET='r'*64;cls.server=ThreadingHTTPServer(('127.0.0.1',0),agent.Handler);threading.Thread(target=cls.server.serve_forever,daemon=True).start();cls.url='http://127.0.0.1:'+str(cls.server.server_port)
 @classmethod
 def tearDownClass(cls):cls.server.shutdown();cls.server.server_close();agent.configure('legacy')
 def setUp(self):agent.configure('gateway')
 def tearDown(self):agent.configure('legacy')
 def request(self,path,body=None,raw=None,token='r'*64):
  encoded=raw if raw is not None else json.dumps(body or {}).encode();req=Request(self.url+path,data=encoded,headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})
  try:
   with urlopen(req) as result:return result.status,json.load(result)
  except HTTPError as result:return result.code,json.load(result)
 def test_private_authorization_is_checked_before_permission_module(self):
  with patch('managed_game_roles.dispatch') as dispatch:self.assertEqual(self.request('/permissions/teams',token='bad')[0],401);dispatch.assert_not_called()
 def test_compat_and_full_routes_dispatch_current_fixed_database(self):
  for path,teams in [('/bank/permissions',False),('/permissions/teams',True)]:
   with self.subTest(path=path),patch('managed_game_roles.dispatch',return_value=(200,{'ok':True})) as dispatch:
    self.assertEqual(self.request(path,{'actor':'website-sync'})[0],200);dispatch.assert_called_once_with(agent.permissions_database,{'actor':'website-sync'},teams=teams)
 def test_legacy_and_planet_modes_are_not_permission_writers(self):
  for mode in ('legacy','planets'):
   agent.configure(mode)
   with patch('managed_game_roles.dispatch') as dispatch:self.assertEqual(self.request('/permissions/teams')[0],404);dispatch.assert_not_called()
 def test_query_duplicate_keys_and_oversized_bodies_are_rejected(self):
  with patch('managed_game_roles.dispatch') as dispatch:
   self.assertEqual(self.request('/permissions/teams?server=arrakis')[0],400)
   self.assertEqual(self.request('/bank/permissions',raw=b'{"actor":"website-sync","actor":"bad"}')[0],400)
   self.assertEqual(self.request('/permissions/teams',raw=b'x'*262145)[0],400)
   dispatch.assert_not_called()
 def test_backend_failure_does_not_report_success(self):
  with patch('managed_game_roles.dispatch',return_value=(503,{'message':'Unavailable'})):self.assertEqual(self.request('/permissions/teams')[0],503)
 def test_scoped_route_uses_only_scoped_writer(self):
  with patch('server_game_roles.dispatch',return_value=(200,{'ok':True})) as scoped,patch('managed_game_roles.dispatch') as global_writer:
   self.assertEqual(self.request('/permissions/server-teams',{'actor':'website-sync'})[0],200)
   scoped.assert_called_once_with(agent.permissions_database,{'actor':'website-sync'});global_writer.assert_not_called()
 def test_scoped_route_checks_authentication_and_gateway_mode(self):
  with patch('server_game_roles.dispatch') as scoped:
   self.assertEqual(self.request('/permissions/server-teams',token='bad')[0],401)
   for mode in ('legacy','planets'):
    agent.configure(mode);self.assertEqual(self.request('/permissions/server-teams')[0],404)
   scoped.assert_not_called()
 def test_scoped_route_rejects_query_duplicate_keys_and_oversized_payload(self):
  with patch('server_game_roles.dispatch') as scoped:
   self.assertEqual(self.request('/permissions/server-teams?server=global')[0],400)
   self.assertEqual(self.request('/permissions/server-teams',raw=b'{"actor":"website-sync","actor":"bad"}')[0],400)
   self.assertEqual(self.request('/permissions/server-teams',raw=b'x'*262145)[0],400)
   scoped.assert_not_called()
 def test_fixed_fifo_broadcast_rejects_nonfifo(self):
  from types import SimpleNamespace
  with patch('managed_game_roles.os.stat',return_value=SimpleNamespace(st_mode=0)),patch('managed_game_roles.os.open') as opened:
   self.assertRaises(RuntimeError,roles.broadcast);opened.assert_not_called()
 def test_broadcast_is_only_the_fixed_native_sync_command(self):
  import stat
  from types import SimpleNamespace
  with patch.object(roles.os,'O_NONBLOCK',2048,create=True),patch.object(roles.os,'O_NOFOLLOW',131072,create=True),patch('managed_game_roles.os.stat',return_value=SimpleNamespace(st_mode=stat.S_IFIFO)),patch('managed_game_roles.os.open',return_value=12),patch('managed_game_roles.os.fstat',return_value=SimpleNamespace(st_mode=stat.S_IFIFO)),patch('managed_game_roles.os.write',return_value=len(b'lp sync\n')) as write,patch('managed_game_roles.os.close') as close:
   roles.broadcast();write.assert_called_once_with(12,b'lp sync\n');close.assert_called_once_with(12)
if __name__=='__main__':unittest.main()

