"""Term-bound office blueprints. Private bridge only; authority comes from game SQL."""
import json
import re
import uuid

HOUSES = ('Fremen', 'Harkonnen', 'Corrino', 'Atreides', 'Ordos')

class Conflict(Exception):
    pass

def identity(value):
    if not isinstance(value, str) or not re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', value):
        raise ValueError('Verified Minecraft UUID required')
    return str(uuid.UUID(value))

def winner(election):
    tally = election.get('tally', {}) if isinstance(election, dict) else {}
    valid = []
    for key, count in tally.items():
        try:
            player = identity(key)
        except (ValueError, TypeError):
            continue
        if type(count) is int and count > 0:
            valid.append((player, count))
    return sorted(valid, key=lambda row: (-row[1], row[0]))[0][0] if valid else ''

def at(record, *keys):
    for key in keys:
        if not isinstance(record, dict):
            return {}
        record = record.get(key, record.get(str(key), {}))
    return record

def offices(record, week, player, faction):
    result = {}
    emperor = winner(at(record, 'votes', 'emperor', week))
    if player == emperor:
        result['emperor'] = f'emperor:{week}:{player}'
    if faction in HOUSES and player == winner(at(record, 'votes', 'house', week, faction)):
        result['house-leader:' + faction] = f'house-leader:{faction}:{week}:{player}'
        for appointment, office in (('water', 'water-steward'), ('steward', 'spice-steward')):
            entry = at(record, 'appointments', appointment)
            if emperor and at(entry, 'week') == week and at(entry, 'emperor') == emperor and at(entry, 'house') == faction:
                result[office + ':' + faction] = f'{office}:{faction}:{week}:{player}:{emperor}'
    return result

def initialize(cursor):
    cursor.execute('''CREATE TABLE IF NOT EXISTS dune_office_kit_definitions (
        owner_uuid VARCHAR(36) NOT NULL,office_key VARCHAR(96) NOT NULL,
        term_id VARCHAR(190) NOT NULL,version INTEGER NOT NULL,
        kit_name VARCHAR(64) NOT NULL,color VARCHAR(7) NOT NULL,lore_json TEXT NOT NULL,
        updated_at BIGINT NOT NULL,PRIMARY KEY(owner_uuid,office_key,term_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4''')

def authority(cursor, player, locking=False):
    import yaml
    suffix = ' FOR SHARE' if locking else ''
    cursor.execute('SELECT yaml FROM dune_politics WHERE id=1' + suffix)
    row = cursor.fetchone()
    if not row or not isinstance(row['yaml'], str) or len(row['yaml']) > 2097152:
        raise RuntimeError('Political authority unavailable')
    record = yaml.safe_load(row['yaml']) or {}
    cursor.execute('SELECT faction FROM dune_players WHERE uuid=%s' + suffix, (player,))
    row = cursor.fetchone()
    faction = row['faction'] if row else ''
    cursor.execute('SELECT FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP)/604800) AS week')
    week = int(cursor.fetchone()['week'])
    return offices(record, week, player, faction)

def validate(data):
    if not isinstance(data, dict) or set(data) != {'uuid', 'office', 'term', 'version', 'name', 'color', 'lore'}:
        raise ValueError('Invalid blueprint fields')
    player = identity(data['uuid'])
    if data['office'] != 'emperor' and not (isinstance(data['office'], str) and re.fullmatch(r'(house-leader|water-steward|spice-steward):(Fremen|Harkonnen|Corrino|Atreides|Ordos)', data['office'])):
        raise ValueError('Invalid office')
    if not isinstance(data['term'], str) or not 1 <= len(data['term']) <= 190:
        raise ValueError('Invalid elected term')
    if type(data['version']) is not int or not 0 <= data['version'] < 2147483647:
        raise ValueError('Invalid blueprint version')
    def text(value, limit):
        if not isinstance(value, str) or not 1 <= len(value.strip()) <= limit or re.search(r'[\x00-\x1f\x7f<>§]', value):
            raise ValueError('Use plain text for kit names and lore')
        return value.strip()
    name = text(data['name'], 64)
    if not isinstance(data['color'], str) or not re.fullmatch(r'#[0-9A-Fa-f]{6}', data['color']):
        raise ValueError('Use a six-digit colour')
    if not isinstance(data['lore'], list) or len(data['lore']) > 6:
        raise ValueError('Use at most six lore lines')
    return {**data, 'uuid': player, 'name': name, 'color': data['color'].upper(), 'lore': [text(line, 120) for line in data['lore']]}

def read(connect, player):
    player = identity(player)
    with connect() as con:
        with con.cursor() as cursor:
            current = authority(cursor, player)
            cursor.execute('SELECT office_key,term_id,version,kit_name,color,lore_json FROM dune_office_kit_definitions WHERE owner_uuid=%s', (player,))
            definitions = {row['office_key']: row for row in cursor.fetchall() if current.get(row['office_key']) == row['term_id']}
            rows = []
            for office, term in current.items():
                row = definitions.get(office)
                rows.append({'office': office, 'term': term, 'version': row['version'] if row else 0, 'name': row['kit_name'] if row else 'Imperial Stillsuit', 'color': row['color'] if row else '#571F82', 'lore': json.loads(row['lore_json']) if row else [], 'quality': 'uncommon'})
    return {'playerUuid': player, 'canDesign': bool(rows), 'offices': rows}

def save(connect, payload):
    data = validate(payload)
    with connect() as con:
        try:
            con.begin()
            with con.cursor() as cursor:
                if authority(cursor, data['uuid'], True).get(data['office']) != data['term']:
                    raise PermissionError('Current elected office required')
                args = (data['uuid'], data['office'], data['term'])
                cursor.execute('SELECT version FROM dune_office_kit_definitions WHERE owner_uuid=%s AND office_key=%s AND term_id=%s FOR UPDATE', args)
                row = cursor.fetchone()
                if (row['version'] if row else 0) != data['version']:
                    raise Conflict('Blueprint changed; reload before saving')
                if row:
                    cursor.execute('''UPDATE dune_office_kit_definitions SET version=%s,kit_name=%s,color=%s,lore_json=%s,
                        updated_at=CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED)
                        WHERE owner_uuid=%s AND office_key=%s AND term_id=%s AND version=%s''',
                        (data['version'] + 1, data['name'], data['color'], json.dumps(data['lore'])) + args + (data['version'],))
                    if cursor.rowcount != 1:
                        raise Conflict('Blueprint changed; reload before saving')
                else:
                    cursor.execute('''INSERT INTO dune_office_kit_definitions(owner_uuid,office_key,term_id,version,kit_name,color,lore_json,updated_at)
                        VALUES(%s,%s,%s,%s,%s,%s,%s,CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED))''',
                        args + (1, data['name'], data['color'], json.dumps(data['lore'])))
            con.commit()
        except Exception:
            con.rollback()
            raise
    return {'ok': True, 'uuid': data['uuid'], 'office': data['office'], 'term': data['term'], 'version': data['version'] + 1}
