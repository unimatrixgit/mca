import json,threading,unittest
from http.server import ThreadingHTTPServer
from urllib.request import Request,urlopen
from urllib.error import HTTPError
from unittest.mock import patch
import agent
class KitRoutesTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  agent.SECRET='c'*64;cls.http=ThreadingHTTPServer(('127.0.0.1',0),agent.Handler);threading.Thread(target=cls.http.serve_forever,daemon=True).start();cls.url='http://127.0.0.1:'+str(cls.http.server_port)
 @classmethod
 def tearDownClass(cls):cls.http.shutdown();cls.http.server_close();agent.configure('legacy')
 def request(self,path,data=None,token='c'*64):
  try:
   body=data.encode() if isinstance(data,str) else json.dumps(data).encode() if data is not None else None
   with urlopen(Request(self.url+path,data=body,headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'})) as response:return response.status,json.load(response)
  except HTTPError as response:return response.code,json.load(response)
 def test_gateway_auth_and_method_guards(self):
  agent.configure('gateway');self.assertEqual(self.request('/kits/catalogue',token='bad')[0],401)
  agent.configure('planets');self.assertEqual(self.request('/kits/catalogue')[0],404)
  agent.configure('gateway');self.assertEqual(self.request('/kits/cart/checkout')[0],405);self.assertEqual(self.request('/kits/catalogue',{})[0],405)
 def test_catalogue_get_and_exact_query(self):
  agent.configure('gateway')
  with patch('kit_commerce.catalogue',return_value={'listings':[]}) as read:
   self.assertEqual(self.request('/kits/catalogue'),(200,{'listings':[]}));read.assert_called_once()
   self.assertEqual(self.request('/kits/catalogue?uuid=x')[0],400)
 def test_cart_dispatch_and_duplicate_fields_rejected(self):
  agent.configure('gateway')
  with patch('agent.Path.read_bytes',return_value=b'k'*32),patch('kit_commerce.checkout_cart',return_value={'ok':True}) as pay:
   body={'buyerUuid':'00000000-0000-0000-0009-000000000001','cartId':'00000000-0000-0000-0009-000000000002','lines':[]}
   self.assertEqual(self.request('/kits/cart/checkout',body)[0],200);self.assertEqual(pay.call_args.args[2],body)
   pay.reset_mock();self.assertEqual(self.request('/kits/cart/checkout','{"lines":[],"lines":[]}')[0],400);pay.assert_not_called()
 def test_no_ambiguous_identity_query(self):
  agent.configure('gateway')
  for path in ('/kits/arc','/kits/arc?uuid=a&uuid=b','/kits/orders?uuid=bad','/kits/arc?uuid=x&amount=1000'):
   with self.subTest(path=path):self.assertEqual(self.request(path)[0],400)
if __name__=='__main__':unittest.main()
