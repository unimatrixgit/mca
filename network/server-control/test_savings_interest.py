import unittest
from decimal import Decimal
from datetime import datetime,timezone
from savings_interest import accrued,boundaries
D=86400000
T=lambda y,m,d:int(datetime(y,m,d,tzinfo=timezone.utc).timestamp()*1000)
class InterestTests(unittest.TestCase):
 def test_full_month(self):
  a,b=boundaries(T(2026,11,1));self.assertEqual(accrued('5000',[],a,b),Decimal('52.50'))
 def test_partial_first_month(self):
  a=T(2026,11,16);self.assertEqual(accrued('5000',[],a,boundaries(a)[1]),Decimal('26.25'))
 def test_midmonth_deposit(self):
  a,b=boundaries(T(2026,11,1));self.assertEqual(accrued('5000',[{'created_at':a+15*D,'delta':'5000'}],a,b),Decimal('26.25'))
 def test_midmonth_withdrawal(self):
  a,b=boundaries(T(2026,11,1));self.assertEqual(accrued('0',[{'created_at':a+15*D,'delta':'-5000'}],a,b),Decimal('26.25'))
 def test_boundary_deposit_excluded(self):
  a,b=boundaries(T(2026,11,1));self.assertEqual(accrued('5000',[{'created_at':b,'delta':'5000'}],a,b),Decimal('0.00'))
 def test_calendar_edges(self):
  self.assertEqual(boundaries(T(2028,2,2))[1]-boundaries(T(2028,2,2))[0],29*D)
  self.assertEqual(boundaries(T(2026,12,30))[1],T(2027,1,1))
 def test_rounding_and_invalid(self):
  a,b=boundaries(T(2026,11,1));self.assertEqual(accrued('1',[],a,b),Decimal('0.01'))
  with self.assertRaises(ValueError):accrued('0',[{'created_at':a+1,'delta':'10'}],a,b)
  with self.assertRaises(ValueError):accrued('NaN',[],a,b)
if __name__=='__main__':unittest.main()
