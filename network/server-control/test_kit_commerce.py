import unittest
from decimal import Decimal
from unittest.mock import MagicMock,patch
import kit_commerce as shop

P='00000000-0000-0000-0009-000000000001'
S='00000000-0000-0000-0009-000000000002'
L='00000000-0000-0000-0009-000000000003'
B='00000000-0000-0000-0009-000000000004'
O='00000000-0000-0000-0009-000000000005'

class KitCommerceTests(unittest.TestCase):
    def context(self):
        return dict(listing_id=L,listing_version=1,blueprint_id=B,blueprint_version=1,seller_uuid=S,
            buyer_uuid=P,commerce_kind='personal_sale',clan_id=None,shop_type='player',price_cents=10000,
            kit_name='Desert Herald',kit_color='#113355',kit_lore_json='["An original design."]',quality='common')
    def payload(self):
        return dict(buyerUuid=P,listingId=L,orderLineId=O,listingVersion=1,blueprintVersion=1,priceCents=10000)
    def connection(self,rows):
        con=MagicMock();cur=con.cursor.return_value.__enter__.return_value;cur.fetchone.side_effect=rows
        connect=MagicMock();connect.return_value.__enter__.return_value=con
        return connect,con,cur
    def test_taxes_are_exact_cents_and_conserve_gross(self):
        for cents in (1,19,10000,10001,100000000):
            tax,net=shop.split_cents(cents);self.assertEqual(tax+net,cents);self.assertEqual(tax,cents*25//100)
        for value in (True,0,-1,100000001,Decimal('100')):
            with self.assertRaises(ValueError):shop.split_cents(value)
    def test_code_is_secret_derived_stable_and_line_bound(self):
        code=shop.receipt_code(b'a'*32,O)
        self.assertEqual(len(code),36);self.assertEqual(code,shop.receipt_code(b'a'*32,O))
        self.assertNotEqual(code,shop.receipt_code(b'b'*32,O))
        self.assertNotEqual(code,shop.receipt_code(b'a'*32,L))
        self.assertEqual(len(shop.code_hash(code)),64)
        with self.assertRaises(ValueError):shop.code_hash(code.lower())
    def test_snapshot_rejects_underpriced_high_tier_and_nbt_text(self):
        self.assertEqual(shop.listing_snapshot(self.context())['quality'],'common')
        for change in ({'quality':'legendary'},{'kit_name':'<script>'},{'kit_color':'red'},{'kit_lore_json':'[{}]'},{'kit_lore_json':'["§k"]'},{'price_cents':True}):
            with self.subTest(change=change),self.assertRaises(ValueError):shop.listing_snapshot({**self.context(),**change})
    def test_stale_listing_rolls_back_before_wallet_debit(self):
        connect,con,cur=self.connection([None]);context={**self.context(),'listing_version':2}
        with self.assertRaises(shop.Conflict):shop.checkout(connect,b'a'*32,self.payload(),context=lambda *args:context)
        con.rollback.assert_called_once();con.commit.assert_not_called()
        self.assertEqual(cur.execute.call_count,1)
    def test_wrong_verified_buyer_fails_before_money(self):
        connect,con,cur=self.connection([None]);context={**self.context(),'buyer_uuid':S}
        with self.assertRaises(PermissionError):shop.checkout(connect,b'a'*32,self.payload(),context=lambda *args:context)
        con.commit.assert_not_called();self.assertEqual(cur.execute.call_count,1)
    def test_insufficient_wallet_rolls_back_without_paid_order(self):
        connect,con,cur=self.connection([None,{'balance':Decimal('99.99')},{'balance':Decimal('0')}]);cur.fetchall.return_value=[]
        with self.assertRaises(shop.InsufficientFunds):shop.checkout(connect,b'a'*32,self.payload(),context=lambda *args:self.context())
        con.rollback.assert_called_once();con.commit.assert_not_called()
        self.assertFalse(any('INSERT INTO dune_kit_sales' in call.args[0] for call in cur.execute.call_args_list))
    def test_same_order_replay_returns_same_code_without_wallet_mutation(self):
        code=shop.receipt_code(b'a'*32,O)
        row=dict(buyer_uuid=P,listing_id=L,listing_version=1,kit_version=1,gross_cents=10000,code_hash=shop.code_hash(code))
        connect,con,cur=self.connection([row])
        answer=shop.checkout(connect,b'a'*32,self.payload(),context=lambda *args:self.fail('Replay consulted current listing'))
        self.assertTrue(answer['alreadyPaid']);self.assertEqual(answer['code'],code)
        self.assertEqual(cur.execute.call_count,1);con.commit.assert_not_called()
    def test_reusing_order_for_different_buyer_rejected(self):
        code=shop.receipt_code(b'a'*32,O)
        row=dict(buyer_uuid=S,listing_id=L,listing_version=1,kit_version=1,gross_cents=10000,code_hash=shop.code_hash(code))
        connect,con,cur=self.connection([row])
        with self.assertRaises(shop.Conflict):shop.checkout(connect,b'a'*32,self.payload())
        con.commit.assert_not_called()
    def test_foreign_code_cannot_be_queued(self):
        connect,con,cur=self.connection([dict(id=O,buyer_uuid=S,delivered_at=None,redeem_requested_at=None)])
        with self.assertRaises(PermissionError):shop.queue_redemption(connect,P,shop.receipt_code(b'a'*32,O))
        con.rollback.assert_called_once();con.commit.assert_not_called()
    def test_unfunded_reward_stays_retryable_without_claim_receipt(self):
        connect,con,cur=self.connection([{'balance':Decimal('0')}]);cur.fetchall.return_value=[]
        with patch.object(shop,'progress',return_value=3):answer=shop.claim_arc(connect,P,0)
        self.assertTrue(answer['pending']);con.commit.assert_not_called()
        self.assertFalse(any('INSERT' in call.args[0] for call in cur.execute.call_args_list))
    def test_cart_identity_is_stable_under_reordering_but_not_price_changes(self):
        first={k:v for k,v in self.payload().items() if k!='buyerUuid'}
        second={**first,'orderLineId':B}
        data={'buyerUuid':P,'cartId':L,'lines':[first,second]}
        self.assertEqual(shop.cart_input(data)[3],shop.cart_input({**data,'lines':[second,first]})[3])
        self.assertNotEqual(shop.cart_input(data)[3],shop.cart_input({**data,'lines':[first,{**second,'priceCents':10001}]})[3])
        with self.assertRaises(ValueError):shop.cart_input({**data,'lines':[first,first]})
        with self.assertRaises(ValueError):shop.cart_input({**data,'lines':[first]*21})

if __name__=='__main__':unittest.main()
