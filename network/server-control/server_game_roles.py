"""Website-managed local teams. Both grants and memberships carry the server context."""
import hashlib
import json
import managed_game_roles as shared

SERVERS = ('lobby', 'arrakis', 'kaitain', 'giedi_prime', 'proxy')
RANKS = ('game_server_admin', 'game_server_mod')
PREFIX = 'wsrv_'
MOD = frozenset({'dune.ban.local', 'dune.unban.local', 'dune.moderation.ban',
                 'dune.moderation.unban', 'coreprotect.inspect', 'coreprotect.lookup',
                 'duneprotect.inspect', 'duneprotect.lookup'})
ADMIN = MOD | frozenset({'coreprotect.rollback', 'coreprotect.restore',
                        'duneprotect.rollback', 'duneprotect.restore',
                        'duneprotect.regions', 'duneprotect.worldedit', 'worldedit.*', 'worldguard.*'})
PROXY_MOD = frozenset({'velocity.command.glist', 'velocity.command.server', 'velocity.command.version'})
PROXY_ADMIN = PROXY_MOD | frozenset({'velocity.command.send'})

def allowed(server, rank):
    return (PROXY_ADMIN if rank == RANKS[0] else PROXY_MOD) if server == 'proxy' else (ADMIN if rank == RANKS[0] else MOD)

def group_name(server, rank):
    return PREFIX + hashlib.sha256((server + ':' + rank).encode()).hexdigest()[:30]

def validate(data):
    shared.exact(data, ('revision', 'actor', 'ranks', 'users'))
    shared.actor(data['actor'])
    revision = shared.player_uuid(data['revision'])
    if not isinstance(data['ranks'], list) or len(data['ranks']) > 2 or not isinstance(data['users'], list) or len(data['users']) > 2048:
        raise shared.Invalid('Snapshot limits exceeded')
    ranks = {}
    for row in data['ranks']:
        shared.exact(row, ('id', 'permissions'))
        rank = row['id']
        if not isinstance(rank, str) or rank not in RANKS or rank in ranks:
            raise shared.Invalid('Unknown or duplicate local role')
        ranks[rank] = shared.flags(row['permissions'], (ADMIN | PROXY_ADMIN) if rank == RANKS[0] else (MOD | PROXY_MOD))
    users = {}
    for row in data['users']:
        shared.exact(row, ('uuid', 'memberships'))
        identity = shared.player_uuid(row['uuid'])
        if identity in users or not isinstance(row['memberships'], list) or len(row['memberships']) > 10:
            raise shared.Invalid('Duplicate identity or invalid membership list')
        memberships = set()
        for member in row['memberships']:
            shared.exact(member, ('server', 'rank'))
            server, rank = member['server'], member['rank']
            if not isinstance(server, str) or server not in SERVERS or not isinstance(rank, str) or rank not in ranks or (server, rank) in memberships:
                raise shared.Invalid('Unknown or duplicate server membership')
            memberships.add((server, rank))
        users[identity] = sorted(memberships)
    return {'revision': revision, 'actor': 'website-sync', 'ranks': dict(sorted(ranks.items())), 'users': dict(sorted(users.items()))}

def apply(connector, data, notify=shared.broadcast):
    payload = validate(data)
    digest = hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    with connector() as connection:
        locked = False
        try:
            with connection.cursor() as cursor:
                cursor.execute('SELECT GET_LOCK(%s,0) AS acquired', (shared.LOCK_NAME,))
                if (cursor.fetchone() or {}).get('acquired') != 1:
                    raise shared.Conflict('Another permissions synchronization is running')
                locked = True
                cursor.execute('CREATE TABLE IF NOT EXISTS dune_web_server_role_sync (id INTEGER PRIMARY KEY, revision VARCHAR(36) NOT NULL, digest VARCHAR(64) NOT NULL)')
                cursor.execute('CREATE TABLE IF NOT EXISTS dune_web_server_role_history (revision VARCHAR(36) PRIMARY KEY, digest VARCHAR(64) NOT NULL)')
                cursor.execute("INSERT IGNORE INTO dune_web_server_role_sync(id,revision,digest) VALUES(1,'','')")
            connection.begin()
            with connection.cursor() as cursor:
                cursor.execute('SELECT revision,digest FROM dune_web_server_role_sync WHERE id=1 FOR UPDATE')
                current = cursor.fetchone()
                cursor.execute('SELECT digest FROM dune_web_server_role_history WHERE revision=%s', (payload['revision'],))
                previous = cursor.fetchone()
                if previous and (previous['digest'] != digest or current['revision'] != payload['revision']):
                    raise shared.Conflict('Stale or altered scoped snapshot')
                if not previous:
                    # Delete only our exact hashed identities in their own native server contexts.
                    for server in SERVERS:
                        for rank in RANKS:
                            name = group_name(server, rank)
                            context = " AND BINARY server=%s AND BINARY world='global' AND expiry=0 AND BINARY contexts='{}'"
                            cursor.execute('DELETE FROM luckperms_group_permissions WHERE BINARY name=%s' + context, (name, server))
                            cursor.execute('DELETE FROM luckperms_user_permissions WHERE BINARY permission=%s' + context, ('group.' + name, server))
                            if rank in payload['ranks']:
                                cursor.execute('SELECT name FROM luckperms_groups WHERE name=%s', (name,))
                                existing = cursor.fetchone()
                                if existing and existing['name'] != name:
                                    raise shared.Conflict('Case-conflicting group identity')
                                cursor.execute('INSERT IGNORE INTO luckperms_groups(name) VALUES(%s)', (name,))
                                for node, grant in payload['ranks'][rank].items():
                                    if grant and node in allowed(server, rank):
                                        cursor.execute("INSERT INTO luckperms_group_permissions(name,permission,value,server,world,expiry,contexts) VALUES(%s,%s,TRUE,%s,'global',0,'{}')", (name, node, server))
                    for identity, memberships in payload['users'].items():
                        for server, rank in memberships:
                            cursor.execute("INSERT INTO luckperms_user_permissions(uuid,permission,value,server,world,expiry,contexts) VALUES(%s,%s,TRUE,%s,'global',0,'{}')", (identity, 'group.' + group_name(server, rank), server))
                    shared.clean_empty_groups(cursor, PREFIX)
                    cursor.execute('INSERT INTO dune_web_server_role_history(revision,digest) VALUES(%s,%s)', (payload['revision'], digest))
                    cursor.execute('UPDATE dune_web_server_role_sync SET revision=%s,digest=%s WHERE id=1', (payload['revision'], digest))
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            if locked:
                with connection.cursor() as cursor:
                    cursor.execute('SELECT RELEASE_LOCK(%s)', (shared.LOCK_NAME,))
    notify()
    return {'ok': True, 'revision': payload['revision']}

def dispatch(connector, data, notify=shared.broadcast):
    try:
        return 200, apply(connector, data, notify)
    except shared.Invalid:
        return 400, {'message': 'Invalid server team snapshot.'}
    except shared.Conflict:
        return 409, {'message': 'Server teams changed. Refresh the full snapshot.'}
    except Exception:
        return 503, {'message': 'Server teams could not be confirmed. Retry the current snapshot.'}
