#!/usr/bin/env python3
"""Real MySQL procedure checks in a new, disposable QA database only.

Usage: python3 quest-rewards-mysql-verify.py /root/private-admin.json
Optional MYSQL_QA_READER_CONFIG points to private JSON credentials for the actual
CP reader, testing EXECUTE-only access. No password, code or winner is printed.
The temporary schema is dropped in finally; production tables are never touched.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import json
import os
import secrets
import sys


def statements(script):
    delimiter = ';'
    buffer = []
    for line in script.splitlines():
        if not line.strip() or line.lstrip().startswith('--'):
            continue
        if line.strip().startswith('DELIMITER '):
            if buffer:
                raise ValueError('Unexpected SQL delimiter change')
            delimiter = line.strip().split(None, 1)[1]
            continue
        buffer.append(line)
        joined = '\n'.join(buffer).rstrip()
        if joined.endswith(delimiter):
            yield joined[:-len(delimiter)].strip()
            buffer = []
    if buffer:
        raise ValueError('Unterminated migration statement')


def main():
    import pymysql
    from pymysql.cursors import DictCursor
    admin_config = json.loads(Path(sys.argv[1]).read_text())
    allowed = {'host', 'port', 'user', 'password', 'unix_socket', 'charset', 'ssl'}
    admin_config = {key: value for key, value in admin_config.items() if key in allowed}
    admin_config.update(autocommit=True, cursorclass=DictCursor, connect_timeout=5, read_timeout=15, write_timeout=15)
    schema = 'dune_quest_qa_' + secrets.token_hex(8)
    assert schema.startswith('dune_quest_qa_') and len(schema) < 64
    admin = pymysql.connect(**admin_config)
    created = False
    checks = 0

    def connect(config=admin_config):
        return pymysql.connect(**{**config, 'database': schema})

    def call(name, args, config=admin_config):
        with connect(config) as connection, connection.cursor() as cursor:
            cursor.callproc(name, args)
            rows = cursor.fetchall()
            while cursor.nextset():
                pass
            return rows[0]

    def check(condition, reason):
        nonlocal checks
        if not condition:
            raise AssertionError(reason)
        checks += 1

    try:
        with admin.cursor() as cursor:
            cursor.execute('CREATE DATABASE `' + schema + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        created = True
        with connect() as connection, connection.cursor() as cursor:
            cursor.execute('CREATE TABLE dune_economy_balances (uuid VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,balance DECIMAL(20,2) NOT NULL DEFAULT 0) ENGINE=InnoDB')
            for statement in statements(Path(__file__).with_name('quest-rewards-mysql.sql').read_text()):
                cursor.execute(statement)
        account_a, account_b = '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222'
        uuid_a, uuid_b = 'aaaaaaaa-0000-4000-8000-000000000001', 'bbbbbbbb-0000-4000-8000-000000000002'
        attempts = [(account_a, uuid_a, 'a' * 64, 'b' * 64), (account_b, uuid_b, 'c' * 64, 'd' * 64)]
        with ThreadPoolExecutor(max_workers=2) as workers:
            futures = [workers.submit(call, 'dune_quest_register', args) for args in attempts]
            outcomes = [future.result() for future in futures]
        check(sorted(row['outcome'] for row in outcomes) == ['issued', 'taken'], 'Concurrent discoveries did not produce one global winner')
        index = next(i for i, row in enumerate(outcomes) if row['outcome'] == 'issued')
        account, uuid, code, proof = attempts[index]
        other_account, other_uuid, _, _ = attempts[1 - index]
        check(call('dune_quest_register', (account, uuid, code, proof))['outcome'] == 'issued', 'Same proof was not idempotent')
        check(call('dune_quest_claim', (other_account, uuid, code))['outcome'] == 'invalid', 'Another account could redeem')
        check(call('dune_quest_claim', (account, other_uuid, code))['outcome'] == 'invalid', 'Another linked UUID could redeem')
        new_code, new_proof = 'e' * 64, 'f' * 64
        check(call('dune_quest_register', (account, uuid, new_code, new_proof))['outcome'] == 'reissued', 'Lost-code reissue failed')
        check(call('dune_quest_claim', (account, uuid, code))['outcome'] == 'invalid', 'Revoked code was accepted')

        with connect() as connection, connection.cursor() as cursor:
            cursor.execute('SELECT COUNT(*) AS total FROM dune_economy_balances')
            check(cursor.fetchone()['total'] == 0, 'Discovery unexpectedly credited a wallet')
            cursor.execute('ALTER TABLE dune_economy_balances ADD CONSTRAINT qa_block_credit CHECK (balance < 1000)')
        failed = False
        try:
            call('dune_quest_claim', (account, uuid, new_code))
        except pymysql.MySQLError:
            failed = True
        check(failed, 'Injected wallet failure was not reported')
        with connect() as connection, connection.cursor() as cursor:
            cursor.execute('SELECT redeemed_at FROM dune_quest_first_finder')
            check(cursor.fetchone()['redeemed_at'] is None, 'A failed credit consumed the code')
            cursor.execute('SELECT COUNT(*) AS total FROM dune_economy_balances')
            check(cursor.fetchone()['total'] == 0, 'Failed credit left a wallet change')
            cursor.execute('ALTER TABLE dune_economy_balances DROP CHECK qa_block_credit')

        with ThreadPoolExecutor(max_workers=2) as workers:
            futures = [workers.submit(call, 'dune_quest_claim', (account, uuid, new_code)) for _ in range(2)]
            outcomes = [future.result() for future in futures]
        check(sorted(row['outcome'] for row in outcomes) == ['already_redeemed', 'credited'], 'Concurrent claims duplicated or lost credit')
        with connect() as connection, connection.cursor() as cursor:
            cursor.execute('SELECT balance FROM dune_economy_balances WHERE uuid=%s', (uuid,))
            check(cursor.fetchone()['balance'] == 1000, 'Wallet did not receive exactly 1000')
            cursor.execute('SELECT COUNT(*) AS total FROM dune_quest_first_finder')
            check(cursor.fetchone()['total'] == 1, 'More than one winner exists')
        check(call('dune_quest_register', (account, uuid, code, proof))['outcome'] == 'redeemed', 'Redeemed winner was issued another code')
        check(call('dune_quest_status', (account, uuid)) == {'available': 0, 'discovered': 1, 'redeemed': 1}, 'Winner status mismatch')
        check(call('dune_quest_status', (other_account, other_uuid)) == {'available': 0, 'discovered': 0, 'redeemed': 0}, 'Other account learned private winner state')

        reader_file = os.environ.get('MYSQL_QA_READER_CONFIG')
        if reader_file:
            reader_config = {key: value for key, value in json.loads(Path(reader_file).read_text()).items() if key in allowed}
            reader_config.update(autocommit=True, cursorclass=DictCursor, connect_timeout=5, read_timeout=15, write_timeout=15)
            with pymysql.connect(**reader_config) as connection, connection.cursor() as cursor:
                cursor.execute('SELECT CURRENT_USER() AS identity')
                principal = cursor.fetchone()['identity']
            reader_user, reader_host = principal.rsplit('@', 1)
            with admin.cursor() as cursor:
                for name in ['dune_quest_register', 'dune_quest_claim', 'dune_quest_status']:
                    cursor.execute('GRANT EXECUTE ON PROCEDURE `' + schema + '`.`' + name + '` TO ' + admin.escape(reader_user) + '@' + admin.escape(reader_host))
            check(call('dune_quest_status', (account, uuid), reader_config)['redeemed'] == 1, 'EXECUTE-only reader could not call status')
            denied = False
            try:
                with connect(reader_config) as connection, connection.cursor() as cursor:
                    cursor.execute('SELECT * FROM dune_quest_first_finder')
            except pymysql.MySQLError:
                denied = True
            check(denied, 'Reader has broader ledger access than intended')
        print(json.dumps({'ok': True, 'checks': checks, 'scope': 'isolated disposable QA database; no production quest or wallet changed'}))
    finally:
        if created:
            with admin.cursor() as cursor:
                cursor.execute('DROP DATABASE `' + schema + '`')
        admin.close()


if __name__ == '__main__':
    main()
