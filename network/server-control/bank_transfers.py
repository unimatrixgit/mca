"""Atomic own-account transfers. No shared-account authority is inferred here."""
import hashlib
import json
import re
import time
import uuid
from decimal import Decimal

EXCLUDED={'00000000-0000-4000-8000-00000000de01','69a05131-0bcd-41be-85a9-7a7f77f6fd40'}

def validate(player, source, amount, request_id, memo):
    if str(uuid.UUID(player)) != player or player in EXCLUDED: raise ValueError('Ineligible account')
    if source not in ('current','savings'): raise ValueError('Invalid source')
    if not isinstance(amount,str) or not re.fullmatch(r'(?:0|[1-9][0-9]{0,6})(?:\.[0-9]{1,2})?',amount): raise ValueError('Use a positive amount with at most two decimals')
    value=Decimal(amount).quantize(Decimal('.01'))
    if not 0 < value <= 1000000: raise ValueError('Amount must be between 0.01 and 1000000 DM')
    if str(uuid.UUID(request_id)) != request_id: raise ValueError('Invalid request identifier')
    if not isinstance(memo,str) or len(memo)>140 or any(ord(c)<32 for c in memo): raise ValueError('Invalid payment reference')
    return value

def initialize(connect):
    with connect() as con:
        with con.cursor() as c:
            c.execute('CREATE TABLE IF NOT EXISTS dune_bank_web_transfers(request_id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36) NOT NULL,fingerprint CHAR(64) NOT NULL,result JSON NULL,created_at BIGINT NOT NULL) ENGINE=InnoDB')
            c.execute('CREATE TABLE IF NOT EXISTS dune_current_bank_ledger(id VARCHAR(36) PRIMARY KEY,player_uuid VARCHAR(36) NOT NULL,action VARCHAR(32) NOT NULL,delta DECIMAL(20,2) NOT NULL,balance_after DECIMAL(20,2) NOT NULL,created_at BIGINT NOT NULL,reference VARCHAR(100) NOT NULL UNIQUE,INDEX player_time(player_uuid,created_at)) ENGINE=InnoDB')
        con.commit()

def transfer(connect, player, source, amount, request_id, memo=''):
    value=validate(player,source,amount,request_id,memo)
    fingerprint=hashlib.sha256(json.dumps([player,source,str(value),memo],separators=(',',':')).encode()).hexdigest()
    now=int(time.time()*1000)
    with connect() as con:
        try:
            con.begin()
            with con.cursor() as c:
                c.execute('SELECT uuid FROM dune_players WHERE uuid=%s',(player,))
                if not c.fetchone(): raise ValueError('Registered player required')
                c.execute('INSERT INTO dune_bank_web_transfers VALUES(%s,%s,%s,NULL,%s) ON DUPLICATE KEY UPDATE request_id=request_id',(request_id,player,fingerprint,now))
                c.execute('SELECT player_uuid,fingerprint,result FROM dune_bank_web_transfers WHERE request_id=%s FOR UPDATE',(request_id,))
                receipt=c.fetchone()
                if receipt['player_uuid']!=player or receipt['fingerprint']!=fingerprint: raise ValueError('This request identifier belongs to another transfer')
                if receipt['result']:
                    result=json.loads(receipt['result']);con.rollback();return result
                # Same bank -> current lock order as native BankStore.transfer.
                c.execute('INSERT INTO dune_player_banks VALUES(%s,0,%s) ON DUPLICATE KEY UPDATE player_uuid=player_uuid',(player,now))
                c.execute('SELECT balance FROM dune_player_banks WHERE player_uuid=%s FOR UPDATE',(player,));savings=Decimal(c.fetchone()['balance'])
                c.execute('SELECT balance FROM dune_economy_balances WHERE uuid=%s FOR UPDATE',(player,));row=c.fetchone()
                if not row: raise ValueError('Current account unavailable')
                current=Decimal(row['balance'])
                if (current if source=='current' else savings)<value: raise ValueError('Insufficient funds')
                new_savings=savings+(value if source=='current' else -value);new_current=current+(-value if source=='current' else value)
                if new_savings<0 or max(new_savings,new_current)>Decimal('1000000000000'):raise ValueError('Balance limit exceeded')
                c.execute('UPDATE dune_player_banks SET balance=%s,updated_at=%s WHERE player_uuid=%s',(new_savings,now,player))
                c.execute('UPDATE dune_economy_balances SET balance=%s WHERE uuid=%s',(new_current,player))
                for table,delta,balance,suffix in [('dune_player_bank_ledger',new_savings-savings,new_savings,'savings'),('dune_current_bank_ledger',new_current-current,new_current,'current')]:
                    c.execute('INSERT INTO '+table+'(id,player_uuid,action,delta,balance_after,created_at,reference) VALUES(%s,%s,%s,%s,%s,%s,%s)',(str(uuid.uuid4()),player,'internal transfer',delta,balance,now,'web-own:'+request_id+':'+suffix))
                result={'reference':request_id,'source':source,'destination':'savings' if source=='current' else 'current','amountDunemarks':str(value),'feeDunemarks':'0.00','savingsDunemarks':str(new_savings),'currentDunemarks':str(new_current),'memo':memo,'createdAt':now}
                c.execute('UPDATE dune_bank_web_transfers SET result=%s WHERE request_id=%s',(json.dumps(result),request_id))
                con.commit();return result
        except BaseException:
            con.rollback();raise
