"""Isolated MySQL TEMPORARY proof. Does not initialize or alter live tables, grant ranks, or deliver items."""
from pathlib import Path
import sys,json,uuid,copy
from decimal import Decimal
sys.path.insert(0,str(Path(__file__).parent));sys.path.append('/opt/dunerealms/server-control')
import agent,kit_commerce as shop
real=agent.network_database();witness=agent.network_database();failure=None
class Cursor:
 def __enter__(self):self.cur=real.cursor();return self
 def __exit__(self,*args):self.cur.close()
 def execute(self,sql,args=()):
  if failure and failure(sql):raise RuntimeError('Intentional transactional failure')
  return self.cur.execute(sql,args)
 def __getattr__(self,name):return getattr(self.cur,name)
class Connection:
 def __enter__(self):return self
 def __exit__(self,*args):pass
 def cursor(self):return Cursor()
 def __getattr__(self,name):return getattr(real,name)
def connect():return Connection()
def live_counts():
 witness.commit()
 with witness.cursor() as cur:
  rows={}
  for table in ('dune_kit_sales','dune_kit_carts','dune_kit_arc_claims','dune_economy_balances','dune_player_banks','dune_player_bank_ledger','dune_faction_bank_wallet','dune_faction_bank_ledger','dune_staff_bank_wallet','dune_staff_bank_ledger'):
   cur.execute('SELECT COUNT(*) AS n FROM '+table);rows[table]=cur.fetchone()['n']
  return rows
before=live_counts();suffix=uuid.uuid4().hex[:12]
try:
 with real.cursor() as cur:
  class TemporaryDDL:
   def execute(self,sql):
    sql=sql.replace('CREATE TABLE IF NOT EXISTS','CREATE TEMPORARY TABLE')
    for name in ('dune_kit_sales_faction_nonnegative','dune_kit_sales_conservation','dune_kit_sales_kind'):sql=sql.replace(name,'qa_'+suffix+'_'+name[-12:])
    cur.execute(sql)
  shop.initialize(TemporaryDDL())
  for ddl in (
   'dune_economy_balances(uuid VARCHAR(36) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL)',
   'dune_staff_bank_wallet(account_key VARCHAR(16) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL,updated_at BIGINT NOT NULL)',
   'dune_staff_bank_ledger(id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36),action VARCHAR(32),delta DECIMAL(20,2),balance_after DECIMAL(20,2),created_at BIGINT,reference VARCHAR(100) UNIQUE)',
   'dune_player_banks(player_uuid VARCHAR(36) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL,updated_at BIGINT NOT NULL)',
   'dune_player_bank_ledger(id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36),action VARCHAR(32),delta DECIMAL(20,2),balance_after DECIMAL(20,2),created_at BIGINT,reference VARCHAR(100) UNIQUE)',
   'dune_faction_bank_wallet(faction_id VARCHAR(32) PRIMARY KEY,balance DECIMAL(20,2) NOT NULL,updated_at BIGINT NOT NULL)',
   'dune_faction_bank_ledger(id VARCHAR(36) PRIMARY KEY,faction_id VARCHAR(32),player_uuid VARCHAR(36),action VARCHAR(32),delta DECIMAL(20,2),balance_after DECIMAL(20,2),created_at BIGINT,reference VARCHAR(100) UNIQUE)',
   'dune_politics(id INT PRIMARY KEY,yaml TEXT NOT NULL)',
   'dune_players(uuid VARCHAR(36) PRIMARY KEY,faction VARCHAR(32))'):
   cur.execute('CREATE TEMPORARY TABLE '+ddl+' ENGINE=InnoDB')
  buyer,seller=[str(uuid.uuid4()) for _ in range(2)]
  cur.executemany('INSERT INTO dune_economy_balances VALUES(%s,%s)',[(buyer,'1000'),(seller,'0')])
  cur.execute("INSERT INTO dune_staff_bank_wallet VALUES('network',0,0)")
  cur.executemany('INSERT INTO dune_players VALUES(%s,%s)',[(buyer,'Fremen'),(seller,'Atreides')])
  cur.execute('SELECT FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP)/604800) AS week');week=int(cur.fetchone()['week'])
  politics={'votes':{'emperor':{str(week):{'tally':{seller:1}}},'house':{str(week):{'Atreides':{'tally':{seller:1}}}}}}
  cur.execute('INSERT INTO dune_politics VALUES(1,%s)',(json.dumps(politics),))
 real.commit()
 listings={}
 for kind in shop.KINDS:
  listing,blueprint=[str(uuid.uuid4()) for _ in range(2)]
  row=dict(listing_id=listing,listing_version=1,blueprint_id=blueprint,blueprint_version=1,
   seller_uuid=seller,buyer_uuid=buyer,commerce_kind=kind,clan_id=None,shop_type='player' if kind=='personal_sale' else kind.removesuffix('_sale'),price_cents=10000,
   kit_name='Temporary allocation fixture',kit_color='#123456',kit_lore_json='[]',quality='common',seller_tier_override=0)
  if kind=='official_sale':row.update(office_key='emperor',office_term=f'emperor:{week}:{seller}')
  if kind=='faction_sale':row.update(faction_id='atreides',office_key='house-leader:Atreides',office_term=f'house-leader:Atreides:{week}:{seller}')
  if kind=='server_sale':row.update(server_authorized=1,quality='legendary')
  listings[listing]=row
 def context(cur,owner,listing):
  assert owner==buyer
  return listings[listing]
 lines=[dict(listingId=key,orderLineId=str(uuid.uuid4()),listingVersion=1,blueprintVersion=1,priceCents=10000) for key in listings]
 payload=dict(buyerUuid=buyer,cartId=str(uuid.uuid4()),lines=lines)
 def balances():
  with real.cursor() as cur:
   cur.execute('SELECT uuid,balance FROM dune_economy_balances');wallets={r['uuid']:r['balance'] for r in cur.fetchall()}
   result={'buyer':wallets[buyer],'sellerWallet':wallets[seller]}
   for label,sql,args in [('server',"SELECT balance FROM dune_staff_bank_wallet WHERE account_key='network'",()),('designerBank','SELECT balance FROM dune_player_banks WHERE player_uuid=%s',(seller,)),('faction','SELECT balance FROM dune_faction_bank_wallet WHERE faction_id=%s',('atreides',))]:
    cur.execute(sql,args);row=cur.fetchone();result[label]=row['balance'] if row else Decimal(0)
   return result
 paid=shop.checkout_cart(connect,b'a'*32,payload,context)
 expected={'buyer':Decimal('600'),'sellerWallet':Decimal('155'),'server':Decimal('160'),'designerBank':Decimal('60'),'faction':Decimal('25')}
 assert balances()==expected and sum(expected.values())==Decimal('1000')
 assert shop.checkout_cart(connect,b'a'*32,payload,context)['alreadyPaid'] and balances()==expected
 try:shop.checkout_cart(connect,b'a'*32,{**payload,'lines':[{**lines[0],'priceCents':10001}]+lines[1:]},context)
 except shop.Conflict:pass
 else:raise AssertionError('Changed replay accepted')
 with real.cursor() as cur:
  cur.execute('SELECT commerce_kind,gross_cents,server_tax_cents,faction_tax_cents,seller_net_cents FROM dune_kit_sales')
  rows=cur.fetchall();assert len(rows)==4
  for row in rows:assert tuple(row[k] for k in ('faction_tax_cents','server_tax_cents','seller_net_cents'))==shop.allocation_cents(row['gross_cents'],row['commerce_kind'])
  stamp=shop.now(cur);cur.execute('UPDATE dune_kit_sales SET delivered_at=%s',(stamp,))
 real.commit()
 with real.cursor() as cur:assert shop.progress(cur,seller,0)==1
 # A rolled-back mixed cart must restore all wallet and bank ledgers, not just the last line.
 counter=[0]
 def inject(sql):
  if 'INSERT INTO dune_kit_sales(' in sql:counter[0]+=1
  return counter[0]==2
 failure=inject
 retry={**payload,'cartId':str(uuid.uuid4()),'lines':[{**line,'orderLineId':str(uuid.uuid4())} for line in lines]}
 try:shop.checkout_cart(connect,b'a'*32,retry,context)
 except RuntimeError:pass
 else:raise AssertionError('Mixed-cart partial failure accepted')
 failure=None;assert balances()==expected
 # Expired live native office authority rejects NEW purchases before any money moves.
 with real.cursor() as cur:cur.execute("UPDATE dune_politics SET yaml='{}' WHERE id=1")
 real.commit()
 official=next(line for line in lines if listings[line['listingId']]['commerce_kind']=='official_sale')
 try:shop.checkout(connect,b'a'*32,{**official,'orderLineId':str(uuid.uuid4()),'buyerUuid':buyer},context)
 except PermissionError:pass
 else:raise AssertionError('Expired office accepted')
 # Already-paid historical orders remain recoverable after the authority expires.
 historical=shop.checkout(connect,b'a'*32,{**official,'buyerUuid':buyer},context)
 assert historical['alreadyPaid'] and balances()==expected
 # A current DB tier override unlocks quality, and creates no quest claims.
 personal=next(row for row in listings.values() if row['commerce_kind']=='personal_sale')
 personal.update(quality='legendary',price_cents=160000,seller_tier_override=1)
 with real.cursor() as cur:cur.execute('UPDATE dune_economy_balances SET balance=2000 WHERE uuid=%s',(buyer,))
 real.commit()
 override_payload=dict(buyerUuid=buyer,listingId=personal['listing_id'],orderLineId=str(uuid.uuid4()),listingVersion=1,blueprintVersion=1,priceCents=160000)
 shop.checkout(connect,b'a'*32,override_payload,context)
 with real.cursor() as cur:
  cur.execute('SELECT COUNT(*) AS n FROM dune_kit_arc_claims');assert cur.fetchone()['n']==0
 assert live_counts()==before
 print(json.dumps({'mixedKitAllocationMysqlProbe':'PASS','personal':'75wallet/25server','official':'80wallet/20server','faction':'60designerbank/25faction/15server','server':'100server','currentNativeOfficeAuthority':'PASS','wholeCartRollbackReplay':'PASS','nonPersonalArcExclusion':'PASS','overrideNoFakeClaims':'PASS','liveCountsUnchanged':True,'realFundsOrItemsChanged':False}))
finally:real.close();witness.close()
