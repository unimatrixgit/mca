import unittest
from unittest.mock import MagicMock
from decimal import Decimal
import clan_bank
class ClanBankTest(unittest.TestCase):
 def fixture(self,balance,ledger):
  connect=MagicMock();cur=connect.return_value.__enter__.return_value.cursor.return_value.__enter__.return_value
  cur.fetchone.return_value={'balance':balance};cur.fetchall.return_value=ledger
  return connect,cur
 def test_fixed_projection_preserves_exact_cents_and_bound_clan(self):
  uid='00000000-0000-4000-8000-000000000001';con,cur=self.fixture(Decimal('42.50'),[{'id':'x','action':'withdraw','delta':Decimal('-10.05'),'balance_after':Decimal('42.50'),'created_at':1}])
  r=clan_bank.read_bank(con,uid);self.assertEqual(r['currency'],'dunemarks');self.assertEqual(r['balanceDunemarks'],'42.50');self.assertEqual(r['ledger'][0]['amountDunemarks'],'-10.05');self.assertEqual(cur.execute.call_args_list[0].args[1],(uid,));self.assertTrue(all(c.args[0].startswith('SELECT ') for c in cur.execute.call_args_list))
 def test_invalid_clan_never_connects(self):
  for value in ['x',None,"' OR 1=1"]:
   con=MagicMock()
   with self.assertRaises(ValueError):clan_bank.read_bank(con,value)
   con.assert_not_called()
 def test_negative_nonfinite_or_fractional_cents_fail_closed(self):
  for value in ['-1','NaN','Infinity','1.001']:
   with self.assertRaises(RuntimeError):clan_bank.money(value)
