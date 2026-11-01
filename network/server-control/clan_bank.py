"""Read-only Dunemarks treasury projection; all transfers are native, fenced transactions."""
import re
import uuid
from decimal import Decimal

def money(value, signed=False):
    value=Decimal(value)
    if not value.is_finite() or abs(value)>Decimal('1000000000000') or (not signed and value<0) or value!=value.quantize(Decimal('0.01')):
        raise RuntimeError('Invalid Dunemarks amount')
    return format(value,'.2f')

def read_bank(connect, clan_id):
    if not isinstance(clan_id,str) or not re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}',clan_id) or str(uuid.UUID(clan_id))!=clan_id:
        raise ValueError('Invalid clan identifier')
    with connect() as con:
        with con.cursor() as cursor:
            cursor.execute('SELECT balance FROM dune_clan_wallets WHERE clan_id=%s',(clan_id,))
            row=cursor.fetchone();balance=money(row['balance'] if row else 0)
            cursor.execute('SELECT id,action,delta,balance_after,created_at FROM dune_clan_wallet_ledger WHERE clan_id=%s ORDER BY created_at DESC,id DESC LIMIT 50',(clan_id,))
            ledger=[]
            for row in cursor.fetchall():
                if type(row['created_at']) is not int: raise RuntimeError('Invalid ledger time')
                ledger.append({'id':row['id'],'action':row['action'],'amountDunemarks':money(row['delta'],True),'balanceDunemarks':money(row['balance_after']),'createdAt':row['created_at']})
    return {'clanId':clan_id,'currency':'dunemarks','balanceDunemarks':balance,'ledger':ledger}
