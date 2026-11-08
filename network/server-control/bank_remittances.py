"""External personal-account payments: paired postings and monthly fee accrual."""
import json,time,uuid,hashlib
from decimal import Decimal,ROUND_HALF_UP
from bank_transfers import validate,EXCLUDED

def fee(value):return min(Decimal('100.00'),max(Decimal('1.00'),(value*Decimal('0.0012')).quantize(Decimal('.01'),rounding=ROUND_HALF_UP)))
def initialize(connect):
 with connect() as con:
  with con.cursor() as c:
   c.execute('CREATE TABLE IF NOT EXISTS dune_bank_remittances(request_id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36) NOT NULL,fingerprint CHAR(64) NOT NULL,result JSON NULL,created_at BIGINT NOT NULL) ENGINE=InnoDB')
   c.execute('CREATE TABLE IF NOT EXISTS dune_bank_fee_accruals(request_id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36) NOT NULL,source_type VARCHAR(16) NOT NULL,amount DECIMAL(20,2) NOT NULL,created_at BIGINT NOT NULL,settled_batch VARCHAR(64) NULL) ENGINE=InnoDB')
  con.commit()
def remit(connect,player,source,amount,request_id,memo,target,target_type,from_number,to_number):
 value=validate(player,source,amount,request_id,memo);validate(target,target_type,amount,request_id,memo)
 if target==player:raise ValueError('Use LOCAL for your own accounts')
 for n in [from_number,to_number]:
  if not isinstance(n,str) or len(n)>24:raise ValueError('Invalid account number')
 fingerprint=hashlib.sha256(json.dumps([player,source,str(value),memo,target,target_type,from_number,to_number],separators=(',',':')).encode()).hexdigest();now=int(time.time()*1000)
 with connect() as con:
  try:
   con.begin()
   with con.cursor() as c:
    for who in sorted([player,target]):
     c.execute('SELECT uuid FROM dune_players WHERE uuid=%s',(who,))
     if not c.fetchone():raise ValueError('Registered player required')
    c.execute('INSERT INTO dune_bank_remittances VALUES(%s,%s,%s,NULL,%s) ON DUPLICATE KEY UPDATE request_id=request_id',(request_id,player,fingerprint,now))
    c.execute('SELECT player_uuid,fingerprint,result FROM dune_bank_remittances WHERE request_id=%s FOR UPDATE',(request_id,));receipt=c.fetchone()
    if receipt['player_uuid']!=player or receipt['fingerprint']!=fingerprint:raise ValueError('Request changed')
    if receipt['result']:con.rollback();return json.loads(receipt['result'])
    accounts=[(player,source),(target,target_type)];balances={}
    # All savings locks precede all wallet locks, matching native bank order.
    for who,kind in sorted(accounts,key=lambda a:(a[1]=='current',a[0])):
     if kind=='savings':
      c.execute('INSERT INTO dune_player_banks VALUES(%s,0,%s) ON DUPLICATE KEY UPDATE player_uuid=player_uuid',(who,now))
      c.execute('SELECT balance FROM dune_player_banks WHERE player_uuid=%s FOR UPDATE',(who,))
     else:c.execute('SELECT balance FROM dune_economy_balances WHERE uuid=%s FOR UPDATE',(who,))
     row=c.fetchone()
     if not row:raise ValueError('Account unavailable')
     balances[(who,kind)]=Decimal(row['balance'])
    if balances[(player,source)]<value:raise ValueError('Insufficient funds')
    if balances[(target,target_type)]+value>Decimal('1000000000000'):raise ValueError('Recipient balance limit')
    for who,kind,delta,suffix in [(player,source,-value,'debit'),(target,target_type,value,'credit')]:
     balance=balances[(who,kind)]+delta
     if kind=='savings':c.execute('UPDATE dune_player_banks SET balance=%s,updated_at=%s WHERE player_uuid=%s',(balance,now,who))
     else:c.execute('UPDATE dune_economy_balances SET balance=%s WHERE uuid=%s',(balance,who))
     table='dune_player_bank_ledger' if kind=='savings' else 'dune_current_bank_ledger'
     c.execute('INSERT INTO '+table+'(id,player_uuid,action,delta,balance_after,created_at,reference) VALUES(%s,%s,%s,%s,%s,%s,%s)',(str(uuid.uuid4()),who,'Imperial Remittance',delta,balance,now,'web-ext:'+request_id+':'+suffix))
    charge=fee(value);c.execute('INSERT INTO dune_bank_fee_accruals VALUES(%s,%s,%s,%s,%s,NULL)',(request_id,player,source,charge,now))
    result={'reference':request_id,'amountDunemarks':str(value),'feeDunemarks':str(charge),'feeStatus':'accrued-monthly','fromAccount':from_number,'toAccount':to_number,'memo':memo,'createdAt':now}
    c.execute('UPDATE dune_bank_remittances SET result=%s WHERE request_id=%s',(json.dumps(result),request_id));con.commit();return result
  except BaseException:con.rollback();raise
