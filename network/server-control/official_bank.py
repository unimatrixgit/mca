"""Read-only official MC server treasury."""
from player_bank import money

def read_official(connect,key='mcserver'):
 if key not in {'mcserver','staff_unimatrix999','staff_grmacken','staff_mr_blueray','staff_richwjr'}:raise ValueError('Invalid official account')
 with connect() as con:
  with con.cursor() as c:
   c.execute("SELECT account_number,display_name,balance FROM dune_official_bank_wallets WHERE account_key=%s",(key,));row=c.fetchone()
   if not row:raise ValueError('Account unavailable')
   c.execute("SELECT id,action,delta,balance_after,created_at FROM dune_official_bank_ledger WHERE account_key=%s ORDER BY created_at DESC,id DESC LIMIT 50",(key,))
   ledger=[{'id':r['id'],'action':r['action'],'amountDunemarks':money(r['delta'],True),'balanceDunemarks':money(r['balance_after']),'createdAt':r['created_at']} for r in c.fetchall()]
 return {'name':row['display_name'],'accountNumber':row['account_number'],'balanceDunemarks':money(row['balance']),'ledger':ledger}

def read_mcserver(connect):return read_official(connect)
