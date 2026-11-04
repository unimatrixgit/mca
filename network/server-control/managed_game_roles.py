"""Scoped website-team -> LuckPerms writer. No manual nodes, OP or arbitrary console commands."""
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import uuid

CATALOG = json.loads(Path(__file__).with_name('managed-game-permissions.json').read_text(encoding='utf-8'))
NODES = frozenset(item['node'] for item in CATALOG['nodes'])
BANK_NODES = frozenset(CATALOG['bankLinkedNodes'])
ADMIN_ROLES = frozenset(CATALOG['networkAdministratorRoles'])
WILDCARDS = frozenset({'worldedit.*', 'worldguard.*'})
ROLE_PREFIX = 'wrole_'
# Fixed cosmetic metadata only. No caller-controlled formatting or gameplay nodes.
# Priorities stay below existing staff prefixes (50/80/100).
PATRON_PREFIXES = {
    'patron_caravan': 'prefix.12.&8[&6Caravan&8] &r',
    'patron_guild': 'prefix.15.&8[&7Guild Patron&8] &r',
    'vip': 'prefix.21.&8[&eVIP&8] &r',
    'patron_grand': 'prefix.25.&8[&bBenefactor&8] &r',
}
BANK_PREFIX = 'wbn_'
LOCK_NAME = 'dune:website:roles:v1'
GLOBAL = "BINARY server='global' AND BINARY world='global' AND expiry=0 AND BINARY contexts='{}'"

class Invalid(ValueError):
    pass
class Conflict(ValueError):
    pass

def strict_json(raw):
    def pairs(values):
        result = {}
        for key, value in values:
            if key in result:
                raise Invalid('Duplicate JSON key')
            result[key] = value
        return result
    try:
        return json.loads(raw, object_pairs_hook=pairs)
    except (ValueError, TypeError, UnicodeError) as error:
        raise Invalid('Invalid JSON') from error

def exact(value, keys):
    if not isinstance(value, dict) or set(value) != set(keys):
        raise Invalid('Unexpected request fields')

def player_uuid(value):
    # Floodgate identities with leading zero words are legitimate verified UUIDs.
    if not isinstance(value, str):
        raise Invalid('Canonical verified UUID required')
    try:
        parsed = uuid.UUID(value)
    except ValueError as error:
        raise Invalid('Canonical verified UUID required') from error
    if str(parsed) != value or parsed.int == 0:
        raise Invalid('Canonical verified UUID required')
    return value

def rank_id(value):
    if not isinstance(value, str) or not re.fullmatch(r'[a-z][a-z0-9_]{2,63}', value):
        raise Invalid('Canonical rank ID required')
    return value

def actor(value):
    if value != 'website-sync':
        raise Invalid('Fixed website-sync actor required')

def flags(value, allowed):
    if not isinstance(value, dict) or any(node not in allowed or type(grant) is not bool for node, grant in value.items()):
        raise Invalid('Unknown permission or non-boolean grant')
    return dict(sorted(value.items()))

def group_name(rank):
    return ROLE_PREFIX + (rank if len(rank) <= 30 else hashlib.sha256(rank.encode()).hexdigest()[:30])

def validate_bank(data):
    exact(data, ('uuid', 'actor', 'permissions'))
    actor(data['actor'])
    identity = player_uuid(data['uuid'])
    if not isinstance(data['permissions'], dict) or set(data['permissions']) != BANK_NODES:
        raise Invalid('All four bank flags are required')
    return {'uuid': identity, 'actor': 'website-sync', 'permissions': flags(data['permissions'], BANK_NODES)}

def validate_teams(data):
    exact(data, ('revision', 'actor', 'ranks', 'users'))
    actor(data['actor'])
    revision = player_uuid(data['revision'])
    if not isinstance(data['ranks'], list) or len(data['ranks']) > 128 or not isinstance(data['users'], list) or len(data['users']) > 2048:
        raise Invalid('Snapshot limits exceeded')
    ranks = {}
    for record in data['ranks']:
        exact(record, ('id', 'permissions'))
        key = rank_id(record['id'])
        if key in ('game_server_admin', 'game_server_mod'):
            raise Invalid('Server roles require the scoped endpoint')
        if key in ranks:
            raise Invalid('Duplicate rank')
        permissions = flags(record['permissions'], NODES)
        if key not in ADMIN_ROLES and any(permissions.get(node) for node in WILDCARDS):
            raise Invalid('World tools are restricted to network administrators')
        ranks[key] = permissions
    users = {}
    for record in data['users']:
        exact(record, ('uuid', 'ranks'))
        identity = player_uuid(record['uuid'])
        memberships = record['ranks']
        if identity in users or not isinstance(memberships, list) or len(memberships) > 128 or any(not isinstance(r, str) or r not in ranks for r in memberships) or len(set(memberships)) != len(memberships):
            raise Invalid('Duplicate identity or unknown/duplicate rank membership')
        users[identity] = sorted(memberships)
    return {'revision': revision, 'actor': 'website-sync', 'ranks': dict(sorted(ranks.items())), 'users': dict(sorted(users.items()))}

def broadcast():
    """Single fixed operation: publish the new SQL permissions through native LP messaging."""
    fifo = '/opt/dunerealms/lobby/console.fifo'
    if not stat.S_ISFIFO(os.stat(fifo, follow_symlinks=False).st_mode):
        raise RuntimeError('Lobby console unavailable')
    descriptor = os.open(fifo, os.O_WRONLY | os.O_NONBLOCK | os.O_NOFOLLOW)
    try:
        if not stat.S_ISFIFO(os.fstat(descriptor).st_mode) or os.write(descriptor, b'lp sync\n') != len(b'lp sync\n'):
            raise RuntimeError('Lobby synchronization unavailable')
    finally:
        os.close(descriptor)

def schema(cursor):
    # MySQL DDL commits implicitly: run before beginning the data transaction.
    cursor.execute('CREATE TABLE IF NOT EXISTS dune_web_role_sync (id INTEGER PRIMARY KEY, revision VARCHAR(36) NOT NULL, digest VARCHAR(64) NOT NULL, teams_active BOOLEAN NOT NULL DEFAULT FALSE)')
    cursor.execute('CREATE TABLE IF NOT EXISTS dune_web_role_history (revision VARCHAR(36) PRIMARY KEY, digest VARCHAR(64) NOT NULL)')
    cursor.execute('CREATE TABLE IF NOT EXISTS dune_web_role_names (rank_id VARCHAR(64) PRIMARY KEY, group_name VARCHAR(36) NOT NULL UNIQUE)')
    cursor.execute("INSERT IGNORE INTO dune_web_role_sync(id,revision,digest,teams_active) VALUES(1,'','',FALSE)")

def clear_prefix(cursor, table, column, prefix, identity=None):
    # LEFT exact-prefix comparison; underscore never acts as a LIKE wildcard.
    where = GLOBAL + ' AND BINARY LEFT(' + column + ',%s)=BINARY %s'
    parameters = [len(prefix), prefix]
    if identity is not None:
        where += ' AND uuid=%s'
        parameters.append(identity)
    cursor.execute('DELETE FROM ' + table + ' WHERE ' + where, tuple(parameters))

def clean_empty_groups(cursor, prefix):
    # Preserve contextual/manual references and nodes, even within the reserved namespace.
    cursor.execute('SELECT name FROM luckperms_groups WHERE BINARY LEFT(name,%s)=BINARY %s', (len(prefix), prefix))
    for row in cursor.fetchall():
        name = row['name']
        cursor.execute('SELECT id FROM luckperms_group_permissions WHERE name=%s OR permission=%s LIMIT 1', (name, 'group.' + name))
        if cursor.fetchone():
            continue
        cursor.execute('SELECT id FROM luckperms_user_permissions WHERE permission=%s LIMIT 1', ('group.' + name,))
        if not cursor.fetchone():
            cursor.execute('DELETE FROM luckperms_groups WHERE name=%s', (name,))

def write_group(cursor, name, permissions):
    cursor.execute('SELECT name FROM luckperms_groups WHERE name=%s', (name,))
    existing = cursor.fetchone()
    if existing and existing['name'] != name:
        raise Conflict('Case-conflicting manual group identity')
    cursor.execute('INSERT IGNORE INTO luckperms_groups(name) VALUES(%s)', (name,))
    for node, grant in sorted(permissions.items()):
        # False means no managed grant, not an inherited deny against another team.
        if grant:
            cursor.execute("INSERT INTO luckperms_group_permissions(name,permission,value,server,world,expiry,contexts) VALUES(%s,%s,TRUE,'global','global',0,'{}')", (name, node))

def write_member(cursor, identity, group):
    cursor.execute("INSERT INTO luckperms_user_permissions(uuid,permission,value,server,world,expiry,contexts) VALUES(%s,%s,TRUE,'global','global',0,'{}')", (identity, 'group.' + group))

def mapping(cursor, rank):
    name = group_name(rank)
    cursor.execute('SELECT rank_id,group_name FROM dune_web_role_names WHERE rank_id=%s OR group_name=%s', (rank, name))
    for previous in cursor.fetchall():
        if previous['rank_id'] != rank or previous['group_name'] != name:
            raise Conflict('Managed group identity collision')
    cursor.execute('INSERT IGNORE INTO dune_web_role_names(rank_id,group_name) VALUES(%s,%s)', (rank, name))
    return name

def apply(connector, data, teams, notify=broadcast):
    payload = validate_teams(data) if teams else validate_bank(data)
    digest = hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    with connector() as connection:
        locked = False
        try:
            with connection.cursor() as cursor:
                cursor.execute('SELECT GET_LOCK(%s,0) AS acquired', (LOCK_NAME,))
                if (cursor.fetchone() or {}).get('acquired') != 1:
                    raise Conflict('Another team synchronization is running')
                locked = True
                schema(cursor)
            connection.begin()
            with connection.cursor() as cursor:
                cursor.execute('SELECT revision,digest,teams_active FROM dune_web_role_sync WHERE id=1 FOR UPDATE')
                current = cursor.fetchone()
                if teams:
                    cursor.execute('SELECT digest FROM dune_web_role_history WHERE revision=%s', (payload['revision'],))
                    previous = cursor.fetchone()
                    replay = previous is not None
                    if replay and (previous['digest'] != digest or current['revision'] != payload['revision']):
                        raise Conflict('Stale or altered snapshot revision')
                    if not replay:
                        clear_prefix(cursor, 'luckperms_group_permissions', 'name', ROLE_PREFIX)
                        clear_prefix(cursor, 'luckperms_user_permissions', 'permission', 'group.' + ROLE_PREFIX)
                        clear_prefix(cursor, 'luckperms_group_permissions', 'name', BANK_PREFIX)
                        clear_prefix(cursor, 'luckperms_user_permissions', 'permission', 'group.' + BANK_PREFIX)
                        for rank, permissions in payload['ranks'].items():
                            name = mapping(cursor, rank)
                            write_group(cursor, name, permissions)
                            prefix = PATRON_PREFIXES.get(rank) or {'web_net_owner': 'prefix.90.&8[&4Network Staff&8] &r', 'crysknife': 'prefix.20.&8[&eHoly Order&8] &r'}.get(rank)
                            if prefix:
                                write_group(cursor, name, {prefix: True})
                        for identity, ranks in payload['users'].items():
                            for rank in ranks:
                                write_member(cursor, identity, group_name(rank))
                        clean_empty_groups(cursor, ROLE_PREFIX)
                        clean_empty_groups(cursor, BANK_PREFIX)
                        cursor.execute('INSERT INTO dune_web_role_history(revision,digest) VALUES(%s,%s)', (payload['revision'], digest))
                        cursor.execute('UPDATE dune_web_role_sync SET revision=%s,digest=%s,teams_active=TRUE WHERE id=1', (payload['revision'], digest))
                else:
                    if current['teams_active']:
                        raise Conflict('Full team synchronization is active; legacy bank updates are disabled')
                    name = BANK_PREFIX + uuid.UUID(payload['uuid']).hex
                    cursor.execute('DELETE FROM luckperms_group_permissions WHERE name=%s AND ' + GLOBAL, (name,))
                    clear_prefix(cursor, 'luckperms_user_permissions', 'permission', 'group.' + BANK_PREFIX, payload['uuid'])
                    write_group(cursor, name, payload['permissions'])
                    write_member(cursor, payload['uuid'], name)
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            if locked:
                with connection.cursor() as cursor:
                    cursor.execute('SELECT RELEASE_LOCK(%s)', (LOCK_NAME,))
    # Commit first. A failed broadcast returns503; replay rebroadcasts without reapplying an old revision.
    notify()
    return {'ok': True, 'revision': payload['revision']} if teams else {'ok': True, 'uuid': payload['uuid']}

def dispatch(connector, data, teams=False, notify=broadcast):
    try:
        return 200, apply(connector, data, teams, notify)
    except Invalid:
        return 400, {'message': 'Invalid managed game-permission snapshot.'}
    except Conflict:
        return 409, {'message': 'Team permissions changed or another synchronization is active. Refresh the full snapshot.'}
    except Exception:
        return 503, {'message': 'Native game permissions could not be confirmed. Retry the current snapshot.'}
