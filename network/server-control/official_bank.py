"""Read-only official MC server treasury."""
from player_bank import money
import json,re

def read_official(connect,key='mcserver'):
 if key not in {'mcserver','staff_unimatrix999','staff_grmacken','staff_mr_blueray','staff_richwjr'}:raise ValueError('Invalid official account')
 with connect() as con:
  with con.cursor() as c:
   c.execute("SELECT account_number,display_name,balance FROM dune_official_bank_wallets WHERE account_key=%s",(key,));row=c.fetchone()
   if not row:raise ValueError('Account unavailable')
   c.execute("SELECT id,action,delta,balance_after,created_at,reference FROM dune_official_bank_ledger WHERE account_key=%s ORDER BY created_at DESC,id DESC LIMIT 50",(key,))
   ledger=[]
   for r in c.fetchall():
    entry={'id':r['id'],'action':r['action'],'amountDunemarks':money(r['delta'],True),'balanceDunemarks':money(r['balance_after']),'createdAt':r['created_at']}
    if r['delta']>=0:entry.update(toAccount=row['account_number'],toName=row['display_name'])
    else:entry.update(fromAccount=row['account_number'],fromName=row['display_name'])
    ref=r.get('reference','')
    if re.fullmatch(r'managed:[a-f0-9-]{36}:(debit|credit)',ref):
     c.execute('SELECT result FROM dune_bank_remittances WHERE request_id=%s',(ref.split(':')[1],));receipt=c.fetchone()
     if receipt and receipt['result']:
      data=json.loads(receipt['result']);entry.update({k:data.get(k) for k in ['fromAccount','toAccount','fromName','toName','memo']})
    ledger.append(entry)

 return {'name':row['display_name'],'accountNumber':row['account_number'],'balanceDunemarks':money(row['balance']),'ledger':ledger}

def read_mcserver(connect):return read_official(connect)
