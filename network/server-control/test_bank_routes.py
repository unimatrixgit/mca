import json,threading,unittest
from http.server import ThreadingHTTPServer
from urllib.request import Request,urlopen
from urllib.error import HTTPError
from unittest.mock import patch
import agent
class BankRoutesTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  agent.SECRET='b'*64;cls.http=ThreadingHTTPServer(('127.0.0.1',0),agent.Handler);threading.Thread(target=cls.http.serve_forever,daemon=True).start();cls.url='http://127.0.0.1:'+str(cls.http.server_port)
 @classmethod
 def tearDownClass(cls):cls.http.shutdown();cls.http.server_close();agent.configure('legacy')
 def request(self,path,token='b'*64):
  try:
   with urlopen(Request(self.url+path,headers={'Authorization':'Bearer '+token})) as r:return r.status,json.load(r)
  except HTTPError as r:return r.code,json.load(r)
 def test_auth_and_gateway_only(self):
  agent.configure('gateway');self.assertEqual(self.request('/bank/staff',token='bad')[0],401);agent.configure('planets');self.assertEqual(self.request('/bank/staff')[0],404)
 def test_exact_routes_and_no_query_injection(self):
  agent.configure('gateway')
  with patch('player_bank.read_player',return_value={'currency':'dunemarks'}) as player,patch('player_bank.read_staff',return_value={'currency':'dunemarks'}) as staff:
   self.assertEqual(self.request('/bank/player?uuid=00000000-0000-0000-0009-000000000001')[0],200);player.assert_called_once();self.assertEqual(self.request('/bank/staff')[0],200);staff.assert_called_once()
   for path in ['/bank/player','/bank/player?uuid=a&uuid=b','/bank/player?uuid=x&balance=10','/bank/staff?uuid=x','/bank/faction?uuid=x&faction=atreides'] :self.assertEqual(self.request(path)[0],400)
