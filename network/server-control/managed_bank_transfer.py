"""Atomic LOCAL transfer between explicitly managed official 979 accounts."""
import json,uuid,time,hashlib,re
from decimal import Decimal
KEYS={'mcserver','staff_unimatrix999','staff_grmacken','staff_mr_blueray','staff_richwjr'}
def transfer(connect,actor,source,target,amount,request_id,memo,from_number,to_number):
 if not isinstance(amount,str) or not re.fullmatch(r'(?:0|[1-9][0-9]{0,6})(?:\.[0-9]{1,2})?',amount):raise ValueError('Invalid amount')
 value=Decimal(amount).quantize(Decimal('.01'))
 if not 0<value<=1000000 or str(uuid.UUID(request_id))!=request_id or not isinstance(memo,str) or len(memo)>140 or any(ord(c)<32 for c in memo):raise ValueError('Invalid payment')
 if not isinstance(actor,str) or not 1<=len(actor)<=36 or source not in KEYS or target not in KEYS or source==target:raise ValueError('Invalid managed transfer')
 fingerprint=hashlib.sha256(json.dumps([actor,source,target,str(value),memo,from_number,to_number]).encode()).hexdigest();now=int(time.time()*1000)
 with connect() as con:
  try:
   con.begin()
   with con.cursor() as c:
    c.execute('INSERT INTO dune_bank_remittances VALUES(%s,%s,%s,NULL,%s) ON DUPLICATE KEY UPDATE request_id=request_id',(request_id,actor,fingerprint,now))
    c.execute('SELECT player_uuid,fingerprint,result FROM dune_bank_remittances WHERE request_id=%s FOR UPDATE',(request_id,));r=c.fetchone()
    if r['player_uuid']!=actor or r['fingerprint']!=fingerprint:raise ValueError('Transfer request changed')
    if r['result']:con.rollback();return json.loads(r['result'])
    balances={};names={}
    for key in sorted([source,target]):
     c.execute('SELECT account_number,display_name,balance FROM dune_official_bank_wallets WHERE account_key=%s FOR UPDATE',(key,));r=c.fetchone()
     if not r or r['account_number']!=(from_number if key==source else to_number):raise ValueError('Account changed')
     balances[key]=Decimal(r['balance']);names[key]=r['display_name']
    if balances[source]<value or balances[target]+value>Decimal('1000000000000'):raise ValueError('Insufficient funds or account limit')
    for key,delta,suffix in [(source,-value,'debit'),(target,value,'credit')]:
     after=balances[key]+delta;c.execute('UPDATE dune_official_bank_wallets SET balance=%s,updated_at=%s WHERE account_key=%s',(after,now,key))
     c.execute('INSERT INTO dune_official_bank_ledger VALUES(%s,%s,%s,%s,%s,%s,%s)',(str(uuid.uuid4()),key,'Vault Passage',delta,after,now,'managed:'+request_id+':'+suffix))
    result={'reference':request_id,'amountDunemarks':str(value),'feeDunemarks':'0.00','fromAccount':from_number,'toAccount':to_number,'fromName':names[source],'toName':names[target],'memo':memo,'createdAt':now}
    c.execute('UPDATE dune_bank_remittances SET result=%s WHERE request_id=%s',(json.dumps(result),request_id));con.commit();return result
  except BaseException:con.rollback();raise
