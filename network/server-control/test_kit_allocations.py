import unittest
from unittest.mock import MagicMock
import kit_commerce as shop

class KitAllocationTests(unittest.TestCase):
    def test_four_explicit_allocations_conserve_every_cent(self):
        for amount in (1,2,3,4,19,99,10000,10001,100000000):
            for kind in shop.KINDS:
                faction,server,seller=shop.allocation_cents(amount,kind)
                self.assertEqual(faction+server+seller,amount)
                self.assertGreaterEqual(min(faction,server,seller),0)
                self.assertEqual(faction,amount*25//100 if kind=='faction_sale' else 0)
                self.assertEqual(server,amount if kind=='server_sale' else amount*(15 if kind=='faction_sale' else 20 if kind=='official_sale' else 25)//100)
        self.assertEqual(shop.allocation_cents(10000,'personal_sale'),(0,2500,7500))
        self.assertEqual(shop.allocation_cents(10000,'official_sale'),(0,2000,8000))
        self.assertEqual(shop.allocation_cents(10000,'faction_sale'),(2500,1500,6000))
        self.assertEqual(shop.allocation_cents(10000,'server_sale'),(0,10000,0))

    def test_unknown_kind_or_two_way_faction_call_fails_closed(self):
        for kind in ('clan_craft','admin_override','',None):
            with self.assertRaises(ValueError):shop.allocation_cents(10000,kind)
        for cents in (1,10000):
            with self.assertRaises(ValueError):shop.split_cents(cents,'faction_sale')

    def test_schema_migration_changes_only_exact_legacy_constraints_in_one_ddl(self):
        cur=MagicMock();cur.fetchone.return_value={'n':0};cur.fetchall.return_value=[
            {'name':'dune_kit_sales_chk_1','clause':'(`quantity` = 1)'},
            {'name':'dune_kit_sales_chk_5','clause':'(`faction_tax_cents` = 0)'},
            {'name':'dune_kit_sales_chk_7','clause':'(`gross_cents` = (`server_tax_cents` + `seller_net_cents`))'}]
        query=shop.sales_constraint_migration(cur)
        self.assertTrue(query.startswith('ALTER TABLE dune_kit_sales '))
        self.assertIn('DROP CHECK `dune_kit_sales_chk_5`',query)
        self.assertIn('DROP CHECK `dune_kit_sales_chk_7`',query)
        self.assertNotIn('dune_kit_sales_chk_1',query)
        self.assertIn('faction_tax_cents>=0',query)
        self.assertIn('gross_cents=server_tax_cents+faction_tax_cents+seller_net_cents',query)
        self.assertIn("ADD COLUMN commerce_kind VARCHAR(16) NOT NULL DEFAULT 'personal_sale'",query)
        self.assertEqual(cur.execute.call_count,2) # Metadata only: no DDL or historical-row UPDATE executed.

    def test_schema_migration_is_idempotent_and_rejects_conflicting_name(self):
        cur=MagicMock();cur.fetchone.return_value={'n':1};cur.fetchall.return_value=[
            {'name':'dune_kit_sales_faction_nonnegative','clause':'(`faction_tax_cents` >= 0)'},
            {'name':'dune_kit_sales_conservation','clause':'(`gross_cents` = ((`server_tax_cents` + `faction_tax_cents`) + `seller_net_cents`))'},
            {'name':'dune_kit_sales_kind','clause':"(`commerce_kind` IN (_utf8mb4'personal_sale',_utf8mb4'official_sale',_utf8mb4'faction_sale',_utf8mb4'server_sale'))"}]
        self.assertIsNone(shop.sales_constraint_migration(cur))
        cur.fetchall.return_value=[{'name':'dune_kit_sales_conservation','clause':'gross_cents=1'}]
        with self.assertRaises(RuntimeError):shop.sales_constraint_migration(cur)

    def test_constraint_metadata_cannot_inject_arbitrary_sql(self):
        cur=MagicMock();cur.fetchone.return_value={'n':1};cur.fetchall.return_value=[{'name':'x`; DROP TABLE users; --','clause':'faction_tax_cents=0'}]
        with self.assertRaises(RuntimeError):shop.sales_constraint_migration(cur)

    def test_actual_mysql_escaped_literal_metadata_is_already_migrated(self):
        cur=MagicMock();cur.fetchone.return_value={'n':1};cur.fetchall.return_value=[
            {'name':'dune_kit_sales_faction_nonnegative','clause':'(`faction_tax_cents` >= 0)'},
            {'name':'dune_kit_sales_conservation','clause':'(`gross_cents` = ((`server_tax_cents` + `faction_tax_cents`) + `seller_net_cents`))'},
            {'name':'dune_kit_sales_kind','clause':r"(`commerce_kind` in (_utf8mb4\'personal_sale\',_utf8mb4\'official_sale\',_utf8mb4\'faction_sale\',_utf8mb4\'server_sale\'))"}]
        self.assertIsNone(shop.sales_constraint_migration(cur))
        self.assertEqual(cur.execute.call_count,2)

if __name__=='__main__':unittest.main()
