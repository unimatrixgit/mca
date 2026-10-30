import json
import unittest
from decimal import Decimal
from unittest.mock import MagicMock, patch
import kit_commerce as shop

BUYER='10000000-0000-0000-0000-000000000001'
SELLER='20000000-0000-0000-0000-000000000002'
LISTING='30000000-0000-0000-0000-000000000003'
DESIGN='40000000-0000-0000-0000-000000000004'
ORDER='50000000-0000-0000-0000-000000000005'

class KitAuthorityTests(unittest.TestCase):
    def row(self,kind='personal_sale'):
        return dict(listing_id=LISTING,listing_version=1,blueprint_id=DESIGN,blueprint_version=1,
            seller_uuid=SELLER,buyer_uuid=BUYER,commerce_kind=kind,clan_id=None,
            shop_type='player' if kind=='personal_sale' else kind.removesuffix('_sale'),price_cents=10000,kit_name='Desert Herald',kit_color='#113355',
            kit_lore_json='["An original design."]',quality='common')
    def payload(self):
        return dict(buyerUuid=BUYER,listingId=LISTING,orderLineId=ORDER,listingVersion=1,blueprintVersion=1,priceCents=10000)
    def setup_checkout(self,rows):
        con=MagicMock();cur=con.cursor.return_value.__enter__.return_value
        cur.fetchone.side_effect=rows;cur.fetchall.return_value=[];cur.nextset.return_value=False
        connect=MagicMock();connect.return_value.__enter__.return_value=con
        return connect,con,cur
    def test_personal_sale_commits_new_rate_and_wallet_destination(self):
        connect,con,cur=self.setup_checkout([None,{'balance':Decimal('100')},{'balance':Decimal('0')},{'balance':Decimal('0')},{'now':1000}])
        result=shop.checkout(connect,b'x'*32,self.payload(),context=lambda *args:self.row())
        self.assertFalse(result['alreadyPaid']);con.commit.assert_called_once()
        receipt=next(c.args[1] for c in cur.execute.call_args_list if 'INSERT INTO dune_kit_sales(' in c.args[0])
        self.assertEqual(receipt[9:14],('personal_sale',10000,2500,0,7500))
        updates=[c.args[1] for c in cur.execute.call_args_list if c.args[0].startswith('UPDATE dune_economy_balances')]
        self.assertEqual(updates,[(Decimal('0'),BUYER),(Decimal('75'),SELLER)])
    def test_official_discount_requires_exact_live_office_and_term(self):
        row={**self.row('official_sale'),'office_key':'emperor','office_term':'emperor:1:'+SELLER}
        item=shop.listing_snapshot(row)
        with patch('player_kits.authority',return_value={'emperor':item['officeTerm']}) as authority:
            shop.current_listing_authority(MagicMock(),item)
            authority.assert_called_once();self.assertTrue(authority.call_args.kwargs['locking'])
        for offices in ({},{'emperor':'emperor:2:'+SELLER}):
            with patch('player_kits.authority',return_value=offices),self.assertRaises(PermissionError):
                shop.current_listing_authority(MagicMock(),item)
    def test_official_sale_credits_eighty_without_personal_tier_gate(self):
        row={**self.row('official_sale'),'office_key':'emperor','office_term':'emperor:1:'+SELLER,'quality':'legendary'}
        connect,con,cur=self.setup_checkout([None,{'balance':Decimal('100')},{'balance':Decimal('0')},{'balance':Decimal('0')},{'now':1000}])
        with patch('player_kits.authority',return_value={'emperor':row['office_term']}),patch.object(shop,'claimed',side_effect=AssertionError('Official tier must use current office authority')):
            shop.checkout(connect,b'x'*32,self.payload(),context=lambda *args:row)
        receipt=next(c.args[1] for c in cur.execute.call_args_list if 'INSERT INTO dune_kit_sales(' in c.args[0])
        self.assertEqual(receipt[9:14],('official_sale',10000,2000,0,8000))
        con.commit.assert_called_once()
    def test_faction_revenue_reaches_real_designer_bank_and_owning_faction(self):
        row={**self.row('faction_sale'),'faction_id':'atreides','office_key':'house-leader:Atreides','office_term':'house-leader:Atreides:1:'+SELLER}
        connect,con,cur=self.setup_checkout([None,{'balance':Decimal('100')},{'balance':Decimal('0')},{'now':1000},{'balance':Decimal('0')},{'balance':Decimal('0')}])
        with patch('player_kits.authority',return_value={'house-leader:Atreides':row['office_term']}):
            shop.checkout(connect,b'x'*32,self.payload(),context=lambda *args:row)
        receipt=next(c.args[1] for c in cur.execute.call_args_list if 'INSERT INTO dune_kit_sales(' in c.args[0])
        self.assertEqual(receipt[9:14],('faction_sale',10000,1500,2500,6000))
        personal=next(c.args[1] for c in cur.execute.call_args_list if c.args[0].startswith('UPDATE dune_player_banks'))
        faction=next(c.args[1] for c in cur.execute.call_args_list if c.args[0].startswith('UPDATE dune_faction_bank_wallet'))
        self.assertEqual(personal,(Decimal('60'),1000,SELLER));self.assertEqual(faction,(Decimal('25'),1000,'atreides'))
        wallet_updates=[c.args[1] for c in cur.execute.call_args_list if c.args[0].startswith('UPDATE dune_economy_balances')]
        self.assertEqual(wallet_updates,[(Decimal('0'),BUYER)]);con.commit.assert_called_once()
    def test_server_sale_requires_sql_protected_publisher_flag_and_pays_no_seller(self):
        for invalid in (None,False,True,0,'1'):
            with self.assertRaises(PermissionError):shop.listing_snapshot({**self.row('server_sale'),'server_authorized':invalid})
        row={**self.row('server_sale'),'server_authorized':1,'quality':'legendary'}
        connect,con,cur=self.setup_checkout([None,{'balance':Decimal('100')},{'balance':Decimal('0')},{'now':1000}])
        shop.checkout(connect,b'x'*32,self.payload(),context=lambda *args:row)
        receipt=next(c.args[1] for c in cur.execute.call_args_list if 'INSERT INTO dune_kit_sales(' in c.args[0])
        self.assertEqual(receipt[9:14],('server_sale',10000,10000,0,0))
        self.assertFalse(any(c.args[0].startswith('UPDATE dune_player_banks') for c in cur.execute.call_args_list))
    def test_authoritative_tier_override_does_not_create_claims_or_rewards(self):
        row={**self.row(),'quality':'legendary','price_cents':160000,'seller_tier_override':1}
        connect,con,cur=self.setup_checkout([None,{'balance':Decimal('1600')},{'balance':Decimal('0')},{'balance':Decimal('0')},{'now':1000}])
        with patch.object(shop,'claimed',side_effect=AssertionError('Override should not invent/read claimed sequence')):
            shop.checkout(connect,b'x'*32,{**self.payload(),'priceCents':160000},context=lambda *args:row)
        self.assertFalse(any('INSERT INTO dune_kit_arc_claims' in c.args[0] for c in cur.execute.call_args_list))
        for invalid in (True,'1',2):
            with self.assertRaises(ValueError):shop.listing_snapshot({**row,'seller_tier_override':invalid})
    def test_deadlock_after_receipt_insert_rolls_back_and_preserves_retry_code(self):
        connect,con,cur=self.setup_checkout([None,{'balance':Decimal('100')},{'balance':Decimal('0')},{'balance':Decimal('0')},{'now':1000}])
        def execute(sql,args=()):
            if sql.startswith('UPDATE dune_economy_balances'):raise RuntimeError('1213 deadlock fixture')
        cur.execute.side_effect=execute
        with self.assertRaises(RuntimeError):shop.checkout(connect,b'x'*32,self.payload(),context=lambda *args:self.row())
        con.rollback.assert_called_once();con.commit.assert_not_called()
        code=shop.receipt_code(b'x'*32,ORDER)
        self.assertEqual(code,shop.receipt_code(b'x'*32,ORDER))
    def test_caller_cannot_select_rate_kind_or_unlock_override(self):
        for field,value in (('commerceKind','official_sale'),('server_authorized',1),('seller_tier_override',1)):
            with self.assertRaises(ValueError):shop.checkout(MagicMock(),b'x'*32,{**self.payload(),field:value})
    def test_zero_designer_and_mismatched_faction_authority_fail_closed(self):
        with self.assertRaises(ValueError):shop.listing_snapshot({**self.row(),'seller_uuid':'00000000-0000-0000-0000-000000000000'})
        with self.assertRaises(ValueError):shop.listing_snapshot({**self.row('faction_sale'),'faction_id':'atreides','office_key':'house-leader:Corrino','office_term':'house-leader:Corrino:1:'+SELLER})
    def test_catalogue_checks_current_office_and_authoritative_override_without_quest_mutations(self):
        row={**self.row(),'quality':'legendary','price_cents':160000,'seller_tier_override':1,'seller_name':'Real designer'}
        connect,con,cur=self.setup_checkout([]);cur.fetchall.side_effect=[[row],[],[]]
        result=shop.catalogue(connect)
        self.assertEqual(result['listings'][0]['sellerTierOverride'],1)
        self.assertFalse(any('INSERT' in c.args[0] or 'UPDATE' in c.args[0] for c in cur.execute.call_args_list))
        row={**self.row('official_sale'),'office_key':'emperor','office_term':'emperor:1:'+SELLER,'seller_name':'Real designer'}
        connect,con,cur=self.setup_checkout([]);cur.fetchall.side_effect=[[row],[]]
        with patch('player_kits.authority',return_value={}):
            self.assertEqual(shop.catalogue(connect)['listings'],[])
    def test_personal_arc_query_explicitly_excludes_all_other_sale_kinds(self):
        cur=MagicMock();cur.fetchone.return_value={'n':0}
        self.assertEqual(shop.progress(cur,SELLER,0),0)
        self.assertIn("commerce_kind='personal_sale'",cur.execute.call_args.args[0])
    def test_listings_cannot_mix_authoritative_kind_and_category(self):
        for kind in shop.KINDS:
            for category in ('player','clan','official','faction','server'):
                if category in ({'player','clan'} if kind=='personal_sale' else {kind.removesuffix('_sale')}):
                    continue
                with self.subTest(kind=kind,category=category),self.assertRaises(ValueError):
                    shop.listing_snapshot({**self.row(kind),'shop_type':category})

if __name__=='__main__':unittest.main()
