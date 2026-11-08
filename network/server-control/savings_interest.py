"""Monthly savings interest; balances and ledger writes share one transaction.

Interest starts at enrollment, never retroactively. The monthly rate is 1.05%
of the time-weighted balance within each UTC calendar month. Credits earn interest only after actual posting.
"""
from decimal import Decimal, ROUND_HALF_UP
import time
import uuid
from datetime import datetime, timezone

def boundaries(at):
    date=datetime.fromtimestamp(at/1000,timezone.utc)
    start=datetime(date.year,date.month,1,tzinfo=timezone.utc)
    end=datetime(date.year+(date.month==12),1 if date.month==12 else date.month+1,1,tzinfo=timezone.utc)
    return int(start.timestamp()*1000),int(end.timestamp()*1000)
RATE = Decimal('0.0105')
LIMIT = Decimal('1000000000000.00')


def accrued(current, entries, start, end):
    if not isinstance(start,int) or end!=boundaries(start)[1]:
        raise ValueError('A calendar-month period is required')
    month_start,month_end=boundaries(start)
    current = Decimal(current)
    if not current.is_finite() or not 0 <= current <= LIMIT:
        raise ValueError('Invalid balance')
    changes = {}
    for row in entries:
        at, delta = row['created_at'], Decimal(row['delta'])
        if not isinstance(at, int) or at < start or not delta.is_finite():
            raise ValueError('Invalid ledger entry')
        changes[at] = changes.get(at, Decimal(0)) + delta
    balance = current - sum(changes.values(), Decimal(0))
    area, previous = Decimal(0), start
    for at, delta in sorted(changes.items()):
        if balance < 0:
            raise ValueError('Invalid historical balance')
        if at >= end:
            break
        area += balance * (at - previous)
        balance += delta
        previous = at
    if balance < 0:
        raise ValueError('Invalid closing balance')
    area += balance * (end - previous)
    return (area * RATE / (month_end-month_start)).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)


def initialize(connect):
    with connect() as con:
        with con.cursor() as c:
            c.execute('CREATE TABLE IF NOT EXISTS dune_savings_interest_state ('
                      'player_uuid VARCHAR(36) PRIMARY KEY,period_start BIGINT NOT NULL) ENGINE=InnoDB')
        con.commit()


def settle(connect, player, excluded, now=None):
    if str(uuid.UUID(player)) != player or player in excluded:
        raise ValueError('Ineligible savings account')
    now = int(time.time() * 1000) if now is None else now
    with connect() as con:
        try:
            con.begin()
            with con.cursor() as c:
                c.execute('SELECT balance FROM dune_player_banks WHERE player_uuid=%s FOR UPDATE', (player,))
                row = c.fetchone()
                if not row:
                    con.rollback()
                    return {'status': 'absent'}
                current = Decimal(row['balance'])
                c.execute('SELECT period_start FROM dune_savings_interest_state WHERE player_uuid=%s FOR UPDATE', (player,))
                state = c.fetchone()
                if not state:
                    c.execute('INSERT INTO dune_savings_interest_state VALUES(%s,%s)', (player, now))
                    con.commit()
                    return {'status': 'enrolled', 'nextAt': boundaries(now)[1]}
                start = state['period_start']
                end=boundaries(start)[1]
                if now < end:
                    con.rollback()
                    return {'status': 'not-due', 'nextAt': end}
                c.execute('SELECT delta,created_at FROM dune_player_bank_ledger WHERE player_uuid=%s AND created_at>=%s ORDER BY created_at', (player, start))
                amount = accrued(current, c.fetchall(), start, end)
                if current + amount > LIMIT:
                    raise ValueError('Savings balance limit exceeded')
                if amount:
                    c.execute('UPDATE dune_player_banks SET balance=%s,updated_at=%s WHERE player_uuid=%s', (current + amount, now, player))
                    c.execute('INSERT INTO dune_player_bank_ledger(id,player_uuid,action,delta,balance_after,created_at,reference) VALUES(%s,%s,%s,%s,%s,%s,%s)',
                              (str(uuid.uuid4()), player, 'monthly interest 1.05%', amount, current + amount, now, 'savings-month:' + player + ':' + str(start)))
                c.execute('UPDATE dune_savings_interest_state SET period_start=%s WHERE player_uuid=%s', (end, player))
                con.commit()
                return {'status': 'settled', 'interest': str(amount), 'nextAt': boundaries(end)[1]}
        except BaseException:
            con.rollback()
            raise
