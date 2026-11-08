"""Read-only official MC server treasury."""
from player_bank import money

def read_mcserver(connect):
 with connect() as con:
  with con.cursor() as c:
   c.execute("SELECT account_number,balance FROM dune_official_bank_wallets WHERE account_key='mcserver'");row=c.fetchone()
   if not row:raise ValueError('Account unavailable')
   c.execute("SELECT id,action,delta,balance_after,created_at FROM dune_official_bank_ledger WHERE account_key='mcserver' ORDER BY created_at DESC,id DESC LIMIT 50")
   ledger=[{'id':r['id'],'action':r['action'],'amountDunemarks':money(r['delta'],True),'balanceDunemarks':money(r['balance_after']),'createdAt':r['created_at']} for r in c.fetchall()]
 return {'accountNumber':row['account_number'],'balanceDunemarks':money(row['balance']),'ledger':ledger}
