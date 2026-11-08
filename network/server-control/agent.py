#!/usr/bin/env python3
"""Private server control. Bind loopback; reach only through the restricted SSH tunnel."""
import hmac
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import threading
import time
import uuid as uuid_module
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import urlopen, Request, build_opener, HTTPRedirectHandler, ProxyHandler
from urllib.error import HTTPError
from urllib.parse import urlsplit, parse_qs

LEGACY_SERVERS = {
    'arrakis': ('dune-arrakis', '/opt/dunerealms/servers/arrakis/console.fifo', 'Arrakduin'),
    'lobby': ('dune-lobby', '/opt/dunerealms/lobby/console.fifo', 'Vaerkring lobby'),
    'proxy': ('dune-proxy', None, 'Network gateway'),
}
PLANET_SERVERS = {
    'arrakis': ('dune-arrakis', '/opt/dunerealms/servers/arrakis/console.fifo', 'Arrakduin'),
    'kaitain': ('dune-kaitain', '/opt/dunerealms/servers/kaitain/console.fifo', 'Kaiterhof'),
    'giedi_prime': ('dune-giedi_prime', '/opt/dunerealms/servers/giedi_prime/console.fifo', 'Giedermark'),
    'caladan': ('dune-caladan', '/opt/dunerealms/servers/caladan/console.fifo', 'Kaladam'),
    'sigma_draconis_iv': ('dune-sigma_draconis_iv', '/opt/dunerealms/servers/sigma_draconis_iv/console.fifo', 'Sigmadraken IV'),
}
MODE = 'legacy'
SERVERS = dict(LEGACY_SERVERS)
REMOTE_SERVERS = {}
REMOTE_ORIGIN = 'http://10.77.0.2:25573'
LOCK = threading.Lock()
JOBS = {}
LAST = {}
SECRET = None
PERMISSIONS_LOCK = threading.Lock()
BAN_LOCK = threading.Lock()
NETWORK_BAN_LOCK = threading.Lock()
BAN_FOLDERS = {'arrakis': '/opt/dunerealms/servers/arrakis', 'lobby': '/opt/dunerealms/lobby'}

def configure(mode):
    """Deployment modes select fixed units, paths, bind IP and remote origin."""
    global MODE, SERVERS, REMOTE_SERVERS, BAN_FOLDERS
    if mode not in ('legacy', 'gateway', 'planets'):
        raise ValueError('Invalid private control mode')
    MODE = mode
    SERVERS = (dict(PLANET_SERVERS) if mode == 'planets' else
               {key: LEGACY_SERVERS[key] for key in ('lobby', 'proxy')} if mode == 'gateway' else dict(LEGACY_SERVERS))
    REMOTE_SERVERS = dict(PLANET_SERVERS) if mode == 'gateway' else {}
    BAN_FOLDERS = {key: '/opt/dunerealms/servers/' + key for key in SERVERS if key in PLANET_SERVERS}
    if 'lobby' in SERVERS:
        BAN_FOLDERS['lobby'] = '/opt/dunerealms/lobby'

def known_servers():
    return {**SERVERS, **REMOTE_SERVERS}

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None

def remote_request(path, body=None, timeout=5):
    # Fixed host/port and enumerated paths. Never use process proxy settings or follow redirects.
    allowed = {'/servers', '/bans', '/action', '/bans/action', '/luckperms/editor', '/luckperms/apply'}
    if path not in allowed and path not in {'/logs?server=' + server for server in REMOTE_SERVERS}:
        raise ValueError('Invalid remote operation')
    if MODE != 'gateway' or not SECRET or len(SECRET) < 64:
        raise RuntimeError('Remote control unavailable')
    payload = None if body is None else json.dumps(body).encode('utf-8')
    request = Request(REMOTE_ORIGIN + path, data=payload,
                      headers={'Authorization': 'Bearer ' + SECRET, 'Content-Type': 'application/json'})
    try:
        response = build_opener(ProxyHandler({}), NoRedirect()).open(request, timeout=timeout)
    except HTTPError as error:
        response = error
    with response:
        data = response.read(262145)
        if len(data) > 262144:
            raise RuntimeError('Remote response exceeds safe limit')
        result = json.loads(data)
        if not isinstance(result, dict):
            raise RuntimeError('Invalid remote response')
        return response.code, result

def relay(path, body=None, timeout=5):
    try:
        return remote_request(path, body, timeout)
    except Exception:
        return 503, {'message': 'The planet host is unavailable. No success was confirmed.'}

def remote_ban_removal(data):
    status, body = relay('/bans/action', data, 18)
    if status == 200:
        if body.get('ok') is True and body.get('confirmed') is True and body.get('absent') is True and body.get('key') == data['key']:
            return 200, {'ok': True, 'confirmed': True, 'absent': True, 'key': data['key']}
        return 503, {'message': 'The planet host did not verify the exact ban removal. Keep the appeal pending.'}
    return (status if status in (400, 409, 503, 504) else 503), {'message': 'The planet ban removal was not confirmed. Refresh before deciding this appeal.'}

def private_mysql(path, database):
    # Fixed private configuration. No DSN, hostname, credentials or SQL come from a request.
    import pymysql
    config = json.loads(Path(path).read_text(encoding='utf-8'))
    if config.get('host') != '127.0.0.1' or config.get('database') != database or config.get('port') != 13307:
        raise RuntimeError('Invalid private gameplay database target')
    return pymysql.connect(host='127.0.0.1', port=13307, database=database,
                           user=config.get('username', config.get('user')), password=config['password'],
                           charset='utf8mb4', connect_timeout=3, read_timeout=5, write_timeout=5,
                           autocommit=False, cursorclass=pymysql.cursors.DictCursor)

def network_database():
    return private_mysql('/etc/dunerealms-gameplay-mysql.json', 'dunegameplay')

def permissions_database():
    return private_mysql('/etc/dunerealms-luckperms-mysql.json', 'duneluckperms')

def network_created(value):
    parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
    if parsed.tzinfo is None or parsed.microsecond % 1000 or parsed.timestamp() <= 0:
        raise ValueError('Invalid network snapshot')
    return int(parsed.timestamp()) * 1000 + parsed.microsecond // 1000

def instant_text(value):
    return datetime.fromtimestamp(int(value) / 1000, timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z')

def network_bans():
    with network_database() as connection:
        with connection.cursor() as cursor:
            cursor.execute('SELECT id,player_uuid,player_name,actor_name,reason,created_at,expires_at FROM dune_network_bans WHERE active AND (expires_at IS NULL OR expires_at>CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED)) ORDER BY created_at DESC LIMIT 5000')
            return [{'recordId': row['id'], 'key': 'network:' + row['player_uuid'], 'uuid': row['player_uuid'], 'name': row['player_name'],
                     'operator': row['actor_name'], 'reason': row['reason'], 'scope': 'network',
                     'created': instant_text(row['created_at']), 'expires': 'forever' if row['expires_at'] is None else instant_text(row['expires_at'])}
                    for row in cursor.fetchall()]

def remove_network_ban(data, player_uuid):
    try:
        expected = network_created(data['created'])
    except (ValueError, TypeError, OverflowError):
        return 400, {'message': 'Invalid network ban snapshot'}
    if not NETWORK_BAN_LOCK.acquire(blocking=False):
        return 409, {'message': 'Another network ban decision is running. Refresh shortly.'}
    try:
        with network_database() as connection:
            with connection.cursor() as cursor:
                cursor.execute('INSERT INTO dune_network_ban_locks(player_uuid) VALUES(%s) ON DUPLICATE KEY UPDATE player_uuid=VALUES(player_uuid)', (player_uuid,))
                cursor.execute('SELECT player_uuid FROM dune_network_ban_locks WHERE player_uuid=%s FOR UPDATE', (player_uuid,))
                if not cursor.fetchone():
                    raise RuntimeError('Network ban lock unavailable')
                cursor.execute('SELECT created_at FROM dune_network_bans WHERE player_uuid=%s AND active AND (expires_at IS NULL OR expires_at>CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED)) FOR UPDATE', (player_uuid,))
                record = cursor.fetchone()
                if record is None:
                    connection.rollback()
                    return 200, {'ok': True, 'confirmed': True, 'absent': True, 'key': data['key']}
                if int(record['created_at']) != expected:
                    connection.rollback()
                    return 409, {'message': 'The network ban changed. Refresh before deciding this appeal.'}
                cursor.execute('UPDATE dune_network_bans SET active=FALSE,revoked_at=CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED),revoked_by=%s,revoked_reason=%s WHERE player_uuid=%s AND active AND created_at=%s', (data['actor'], data['reason'], player_uuid, expected))
                if cursor.rowcount != 1:
                    raise RuntimeError('Network ban removal not confirmed')
                connection.commit()
                cursor.execute('SELECT created_at FROM dune_network_bans WHERE player_uuid=%s AND active AND (expires_at IS NULL OR expires_at>CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS SIGNED))', (player_uuid,))
                if cursor.fetchone() is not None:
                    return 409, {'message': 'A current network ban remains. Refresh before deciding this appeal.'}
        print(json.dumps({'event': 'network-ban-remove-confirmed', 'key': data['key'], 'actor': data['actor']}), flush=True)
        return 200, {'ok': True, 'confirmed': True, 'absent': True, 'key': data['key']}
    except Exception:
        return 503, {'message': 'Network ban removal could not be verified. Keep the appeal pending.'}
    finally:
        NETWORK_BAN_LOCK.release()

def local_ban_entries(server):
    # A missing/unreadable source is not proof that a ban was removed.
    file = Path(BAN_FOLDERS[server]) / 'banned-players.json'
    if file.is_symlink() or not file.is_file() or file.stat().st_size > 2000000:
        raise RuntimeError('Ban source is unavailable')
    entries = json.loads(file.read_text(encoding='utf-8'))
    if not isinstance(entries, list) or any(not isinstance(entry, dict) for entry in entries):
        raise RuntimeError('Invalid ban source')
    for entry in entries:
        value = entry.get('uuid')
        if (not isinstance(value, str) or str(uuid_module.UUID(value)) != value.lower()
                or not isinstance(entry.get('created'), str) or not entry['created']):
            raise RuntimeError('Invalid ban source entry')
    return entries

def remove_ban(data):
    if not isinstance(data, dict) or data.get('action') != 'remove':
        return 400, {'message': 'Invalid ban action'}
    key, created, actor, reason = (data.get(name) for name in ('key', 'created', 'actor', 'reason'))
    scopes = '|'.join(re.escape(scope) for scope in [*(key for key in known_servers() if key != 'proxy'), *(['network'] if MODE == 'gateway' else [])])
    if (not isinstance(key, str) or not re.fullmatch(r'(?:' + scopes + r'):[0-9a-fA-F-]{36}', key)
            or not isinstance(created, str) or not 1 <= len(created) <= 100 or re.search(r'[\x00-\x1f\x7f]', created)
            or not isinstance(actor, str) or not re.fullmatch(r'[A-Za-z0-9_]{3,32}', actor)
            or not isinstance(reason, str) or not 5 <= len(reason) <= 500 or re.search(r'[\x00-\x1f\x7f]', reason)):
        return 400, {'message': 'Invalid ban action'}
    server, supplied_uuid = key.split(':')
    try:
        player_uuid = str(uuid_module.UUID(supplied_uuid))
        if player_uuid != supplied_uuid.lower():
            raise ValueError()
    except ValueError:
        return 400, {'message': 'Invalid player UUID'}
    data = {name: data[name] for name in ('action', 'key', 'created', 'actor', 'reason')}
    data['key'] = server + ':' + player_uuid
    if server == 'network':
        return remove_network_ban(data, player_uuid)
    if server in REMOTE_SERVERS:
        return remote_ban_removal(data)
    if not BAN_LOCK.acquire(blocking=False):
        return 409, {'message': 'Another ban action is running. Try again shortly.'}
    deadline = time.monotonic() + 12
    def current():
        return [row for row in local_ban_entries(server) if str(row.get('uuid', '')).lower() == player_uuid]
    def audit(event):
        print(json.dumps({'event': event, 'actor': actor, 'server': server, 'key': server + ':' + player_uuid, 'reason': reason}), flush=True)
    try:
        entries = current()
        if not entries:
            audit('ban-remove-already-absent')
            return 200, {'ok': True, 'confirmed': True, 'absent': True, 'key': server + ':' + player_uuid}
        if len(entries) != 1 or str(entries[0].get('created', '')) != created:
            return 409, {'message': 'The ban record changed. Refresh before deciding this appeal.'}
        with LOCK:
            if JOBS.get(server, {}).get('state') == 'running':
                return 409, {'message': 'A server action is running. Try again after it completes.'}
            active = run(['systemctl', 'is-active', SERVERS[server][0]], timeout=3)
            if active.returncode or active.stdout.strip() != 'active':
                return 503, {'message': 'The game server is not ready to remove this ban.'}
            # Recheck after the service query and before sending the fixed command.
            entries = current()
            if len(entries) != 1 or str(entries[0].get('created', '')) != created:
                return 409, {'message': 'The ban record changed. Refresh before deciding this appeal.'}
            # Paper's profile argument does not resolve an offline banned UUID.
            # Use only the stored snapshot name; never a request-supplied command target.
            target_name = entries[0].get('name', '')
            if not isinstance(target_name, str) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,32}', target_name):
                return 409, {'message': 'The stored ban name is unsafe or missing. Staff review is required.'}
            audit('ban-remove-requested')
            fd = os.open(SERVERS[server][1], os.O_WRONLY | os.O_NONBLOCK | os.O_NOFOLLOW)
            try:
                if not stat.S_ISFIFO(os.fstat(fd).st_mode):
                    raise RuntimeError('Console is not a pipe')
                command = ('minecraft:pardon ' + target_name + '\n').encode('ascii')
                if os.write(fd, command) != len(command):
                    raise RuntimeError('Console write incomplete')
            finally:
                os.close(fd)
        while time.monotonic() < deadline:
            entries = current()
            if not entries:
                audit('ban-remove-confirmed')
                return 200, {'ok': True, 'confirmed': True, 'absent': True, 'key': server + ':' + player_uuid}
            if len(entries) != 1 or str(entries[0].get('created', '')) != created:
                audit('ban-remove-conflict')
                return 409, {'message': 'The ban record changed during removal. Staff review is required.'}
            time.sleep(.25)
        audit('ban-remove-unconfirmed')
        return 504, {'message': 'The game host did not confirm ban removal. Keep the appeal pending and refresh before retrying.'}
    except Exception:
        audit('ban-remove-unavailable')
        return 503, {'message': 'Ban removal could not be verified. Keep the appeal pending.'}
    finally:
        BAN_LOCK.release()

def permission_groups():
    if MODE == 'planets':
        raise RuntimeError('Permission groups are available only on the gateway database host')
    if os.environ.get('DUNE_LUCKPERMS_MYSQL') == '1':
        with permissions_database() as connection:
            with connection.cursor() as cursor:
                cursor.execute('SELECT g.name,count(p.id) AS nodes FROM luckperms_groups g LEFT JOIN luckperms_group_permissions p ON p.name=g.name GROUP BY g.name ORDER BY g.name')
                return {'groups': cursor.fetchall(), 'checkedAt': int(time.time() * 1000)}
    # Read only fixed LuckPerms tables. No credentials or arbitrary SQL cross the tunnel.
    query = "SELECT coalesce(json_agg(t ORDER BY name),'[]'::json) FROM (SELECT g.name,count(p.id) AS nodes FROM luckperms_groups g LEFT JOIN luckperms_group_permissions p ON p.name=g.name GROUP BY g.name) t"
    result = run(['runuser', '-u', 'postgres', '--', 'psql', '-d', 'dunerealms_permissions', '-At', '-c', query])
    if result.returncode:
        raise RuntimeError('LuckPerms storage is unavailable')
    return {'groups': json.loads(result.stdout), 'checkedAt': int(time.time() * 1000)}

def bans():
    rows = []
    for server in BAN_FOLDERS:
        for entry in local_ban_entries(server):
            uuid = entry.get('uuid', '')
            if not re.fullmatch(r'[0-9a-fA-F-]{36}', uuid):
                continue
            rows.append({'key': server + ':' + uuid, 'uuid': uuid, 'name': str(entry.get('name', uuid))[:40],
                         'operator': str(entry.get('source', 'Unknown'))[:120], 'reason': str(entry.get('reason', ''))[:2000],
                         'scope': server, 'created': str(entry.get('created', '')), 'expires': str(entry.get('expires', 'forever'))})
    if REMOTE_SERVERS:
        status, result = remote_request('/bans')
        if status != 200 or not isinstance(result.get('bans'), list) or set(result.get('sources', [])) != set(REMOTE_SERVERS):
            raise RuntimeError('Planet ban sources unavailable')
        for entry in result['bans']:
            if not isinstance(entry, dict) or entry.get('scope') not in REMOTE_SERVERS:
                raise RuntimeError('Invalid planet ban source')
            player = entry.get('uuid')
            try:
                if not isinstance(player, str) or str(uuid_module.UUID(player)) != player.lower() or entry.get('key') != entry['scope'] + ':' + player:
                    raise ValueError()
            except (ValueError, TypeError):
                raise RuntimeError('Invalid planet ban identity')
            limits = {'name': 40, 'operator': 120, 'reason': 2000, 'created': 100, 'expires': 100}
            if any(not isinstance(entry.get(field), str) or len(entry[field]) > limit or re.search(r'[\x00-\x1f\x7f]', entry[field]) for field, limit in limits.items()) or not entry['created']:
                raise RuntimeError('Invalid planet ban metadata')
            rows.append({field: entry[field] for field in ('key', 'uuid', 'scope', *limits)})
    if MODE == 'gateway':
        rows.extend(network_bans())
    return {'bans': rows, 'sources': [*BAN_FOLDERS, *REMOTE_SERVERS, *(['network'] if MODE == 'gateway' else [])], 'globalConnected': MODE == 'gateway'}

def permission_command(command, editor=False):
    if 'arrakis' not in SERVERS:
        raise RuntimeError('LuckPerms console is not local')
    if not PERMISSIONS_LOCK.acquire(blocking=False):
        return 409, {'message': 'Another LuckPerms request is running. Try again shortly.'}
    try:
        cursor_result = run(['journalctl', '-u', 'dune-arrakis', '-n', '0', '--show-cursor', '--no-pager'])
        cursor = re.search(r'-- cursor: (\S+)', cursor_result.stdout)
        if not cursor:
            raise RuntimeError('Cannot read server response')
        fifo = SERVERS['arrakis'][1]
        fd = os.open(fifo, os.O_WRONLY | os.O_NONBLOCK | os.O_NOFOLLOW)
        try:
            if not stat.S_ISFIFO(os.fstat(fd).st_mode):
                raise RuntimeError('Console is not a pipe')
            os.write(fd, (command + '\n').encode())
        finally:
            os.close(fd)
        deadline = time.monotonic() + 18
        while time.monotonic() < deadline:
            result = run(['journalctl', '-u', 'dune-arrakis', '--after-cursor', cursor.group(1), '--no-pager', '-o', 'cat'])
            if editor:
                match = re.search(r'https://luckperms\.net/editor/[A-Za-z0-9_-]{3,200}', result.stdout)
                if match:
                    return 200, {'url': match.group(0)}
            else:
                lines = [line for line in result.stdout.splitlines() if '[LP]' in line]
                if lines:
                    # Only the command response, never unrelated player messages or session URLs.
                    return 200, {'message': scrub('\n'.join(lines))}
            time.sleep(.25)
        return 504, {'message': 'LuckPerms did not confirm the request in time. Check its server status before retrying.'}
    finally:
        PERMISSIONS_LOCK.release()

def run(args, timeout=8):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)

def scrub(text):
    text = re.sub(r'https://luckperms\.net/editor/[^\s\x1b]+', '[private LuckPerms editor session]', text)
    text = re.sub(r'(?i)(\b(?:linkwebsite|communitycode)\s+)\S+', r'\1[redacted]', text)
    text = re.sub(r'(?i)(\bdunemenus\s+(?:action|detail|back|close|ornament)\s+)\S+', r'\1[redacted]', text)
    return re.sub(r'(?i)((?:password|secret|token)\s*[=:]\s*)\S+', r'\1[redacted]', text)[-60000:]

def state():
    rows = []
    for key, (unit, fifo, name) in SERVERS.items():
        result = run(['systemctl', 'show', unit, '--property=ActiveState,SubState,MemoryCurrent,ActiveEnterTimestamp'])
        values = dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)
        with LOCK:
            job = dict(JOBS[key]) if key in JOBS else None
        rows.append({'id': key, 'name': name, 'state': values.get('ActiveState', 'unknown'),
                     'detail': values.get('SubState', ''), 'memory': values.get('MemoryCurrent'),
                     'since': values.get('ActiveEnterTimestamp'), 'console': bool(fifo), 'job': job})
    if REMOTE_SERVERS:
        try:
            status, result = remote_request('/servers')
            remote_rows = result.get('servers')
            if status != 200 or not isinstance(remote_rows, list) or len(remote_rows) != len(REMOTE_SERVERS) or {row.get('id') for row in remote_rows if isinstance(row, dict)} != set(REMOTE_SERVERS):
                raise RuntimeError('Invalid planet status')
            for row in remote_rows:
                key = row['id']
                if row.get('state') not in ('active', 'inactive', 'activating', 'deactivating', 'failed', 'reloading', 'maintenance', 'unknown'):
                    raise RuntimeError('Invalid planet service state')
                job = row.get('job')
                if job is not None and (not isinstance(job, dict) or job.get('state') not in ('running', 'complete', 'failed') or job.get('action') not in ('start', 'stop', 'restart', 'command')):
                    raise RuntimeError('Invalid planet action status')
                rows.append({'id': key, 'name': REMOTE_SERVERS[key][2], 'state': row['state'],
                             'detail': scrub(str(row.get('detail', '')))[:100], 'memory': str(row.get('memory', ''))[:30],
                             'since': str(row.get('since', ''))[:100], 'console': True,
                             'job': None if job is None else {'state': job['state'], 'action': job['action'], 'startedAt': job.get('startedAt'), 'message': scrub(str(job.get('message', '')))[:200]}})
        except Exception:
            rows = [row for row in rows if row['id'] not in REMOTE_SERVERS]
            rows.extend({'id': key, 'name': value[2], 'state': 'unavailable', 'detail': 'Planet host unavailable', 'memory': None, 'since': None, 'console': True, 'job': None} for key, value in REMOTE_SERVERS.items())
    try:
        with urlopen('http://127.0.0.1:25572/status', timeout=3) as response:
            network = json.load(response)
    except Exception:
        network = None
    return {'servers': rows, 'network': network, 'checkedAt': int(time.time() * 1000)}

def perform(server, action, command, actor):
    unit, fifo, _ = SERVERS[server]
    print(json.dumps({'actor': actor, 'server': server, 'action': action, 'event': 'started'}), flush=True)
    try:
        if action == 'command':
            fd = os.open(fifo, os.O_WRONLY | os.O_NONBLOCK | os.O_NOFOLLOW)
            try:
                if not stat.S_ISFIFO(os.fstat(fd).st_mode):
                    raise ValueError('Console is not a pipe')
                os.write(fd, (command + '\n').encode())
            finally:
                os.close(fd)
            message = 'Command sent. Check the console log for its result.'
        else:
            result = run(['systemctl', action, unit], timeout=140)
            if result.returncode:
                raise RuntimeError('Service action failed; check the server log.')
            message = 'Service action completed.'
        outcome = {'state': 'complete', 'message': message}
    except Exception:
        outcome = {'state': 'failed', 'message': 'Action failed. Check the server log or retry after startup.'}
    with LOCK:
        JOBS[server].update(outcome)
    print(json.dumps({'actor': actor, 'server': server, 'action': action, **outcome}), flush=True)

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def reply(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def authorized(self):
        received = self.headers.get('Authorization', '')
        return hmac.compare_digest(received.encode(), ('Bearer ' + SECRET).encode())

    def do_GET(self):
        if not self.authorized():
            return self.reply(401, {'message': 'Unauthorized'})
        url = urlsplit(self.path)
        if kit_shop_route(self, 'GET', url):
            return
        if player_kits_route(self, 'GET', url):
            return
        if quest_route(self, 'GET', url):
            return
        if url.path == '/bank/official':
            try:
                args=parse_qs(url.query,strict_parsing=True)
                if set(args)!={'key'} or len(args['key'])!=1:raise ValueError()
                from official_bank import read_official
                return self.reply(200,read_official(network_database,args['key'][0]))
            except (ValueError,KeyError):
                return self.reply(400,{'message':'Invalid account'})
            except Exception:
                return self.reply(503,{'message':'Official account unavailable'})
        if url.path == '/bank/mcserver':
            if url.query: return self.reply(400,{'message':'Account overrides are not accepted'})
            try:
                from official_bank import read_mcserver
                return self.reply(200,read_mcserver(network_database))
            except Exception:
                return self.reply(503,{'message':'Official bank unavailable'})
        if url.path in ('/bank/player', '/bank/staff', '/bank/faction'):
            if MODE != 'gateway':
                return self.reply(404, {'message': 'Not found'})
            args = parse_qs(url.query, keep_blank_values=True)
            try:
                from player_bank import read_player, read_staff, read_faction
                if url.path in ('/bank/player', '/bank/faction'):
                    if set(args) != {'uuid'} or len(args['uuid']) != 1:
                        raise ValueError()
                    return self.reply(200, (read_player if url.path == '/bank/player' else read_faction)(network_database, args['uuid'][0]))
                if url.query:
                    raise ValueError()
                return self.reply(200, read_staff(network_database))
            except ValueError:
                return self.reply(400, {'message': 'Invalid bank request'})
            except Exception:
                return self.reply(503, {'message': 'Bank storage is unavailable.'})
        if url.path == '/clans/bank':
            args = parse_qs(url.query, keep_blank_values=True)
            if MODE != 'gateway':
                return self.reply(404, {'message': 'Not found'})
            try:
                if set(args) != {'clanId'} or len(args['clanId']) != 1:
                    raise ValueError()
                from clan_bank import read_bank
                return self.reply(200, read_bank(network_database, args['clanId'][0]))
            except ValueError:
                return self.reply(400, {'message': 'Invalid clan identifier'})
            except Exception:
                return self.reply(503, {'message': 'Clan bank storage is unavailable.'})
        if url.path == '/servers' and not url.query:
            return self.reply(200, state())
        if url.path == '/luckperms/groups' and not url.query:
            try:
                return self.reply(200, permission_groups())
            except Exception:
                return self.reply(503, {'message': 'LuckPerms groups are unavailable.'})
        if url.path == '/bans' and not url.query:
            try:
                return self.reply(200, bans())
            except Exception:
                return self.reply(503, {'message': 'Ban sources are unavailable.'})
        args = parse_qs(url.query)
        if url.path == '/logs' and set(args) == {'server'} and len(args['server']) == 1 and args['server'][0] in known_servers():
            server = args['server'][0]
            if server in REMOTE_SERVERS:
                status, body = relay('/logs?server=' + server)
                if status != 200 or not isinstance(body.get('log'), str):
                    return self.reply(503, {'message': 'Planet console is unavailable.'})
                return self.reply(200, {'log': scrub(body['log'])})
            unit = SERVERS[server][0]
            result = run(['journalctl', '-u', unit, '-n', '160', '--no-pager', '-o', 'cat'])
            return self.reply(200, {'log': scrub(result.stdout)})
        return self.reply(404, {'message': 'Not found'})

    def do_POST(self):
        if not self.authorized():
            return self.reply(401, {'message': 'Unauthorized'})
        if self.path == '/bank/managed-transfer':
            try:
                length=int(self.headers.get('Content-Length','0'))
                if not 0<length<=2000:raise ValueError()
                data=json.loads(self.rfile.read(length))
                if not isinstance(data,dict) or set(data)!={'actor','source','target','amount','request_id','memo','from_number','to_number'}:raise ValueError()
                from managed_bank_transfer import transfer
                return self.reply(200,transfer(network_database,**data))
            except (ValueError,TypeError,KeyError):
                return self.reply(409,{'message':'Transfer declined'})
            except Exception:
                return self.reply(503,{'message':'Confirmation unavailable'})
        if self.path == '/bank/remittance':
            try:
                length=int(self.headers.get('Content-Length','0'))
                if not 0<length<=2000: raise ValueError('Invalid transfer')
                data=json.loads(self.rfile.read(length))
                if not isinstance(data,dict) or set(data)!={'player','source','amount','request_id','memo','target','target_type','from_number','to_number'}: raise ValueError('Invalid transfer')
                from bank_remittances import remit
                return self.reply(200,remit(network_database,**data))
            except (ValueError,TypeError,KeyError):
                return self.reply(409,{'message':'Transfer declined'})
            except Exception:
                return self.reply(503,{'message':'Transfer confirmation unavailable'})
        if self.path == '/bank/transfer-own':
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 < length <= 2000: raise ValueError('Invalid transfer')
                data = json.loads(self.rfile.read(length))
                if not isinstance(data,dict) or set(data) != {'player','source','amount','request_id','memo'}: raise ValueError('Invalid transfer')
                from bank_transfers import transfer
                return self.reply(200, transfer(network_database, **data))
            except (ValueError, TypeError, KeyError):
                return self.reply(409, {'message':'Transfer declined'})
            except Exception:
                return self.reply(503, {'message':'Transfer confirmation unavailable'})
        if kit_shop_route(self, 'POST', urlsplit(self.path)):
            return
        if player_kits_route(self, 'POST', urlsplit(self.path)):
            return
        if quest_route(self, 'POST', urlsplit(self.path)):
            return
        if managed_permissions_route(self, urlsplit(self.path)):
            return
        if self.path == '/bans/action':
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 < length <= 4096:
                    raise ValueError()
                data = json.loads(self.rfile.read(length))
            except (ValueError, TypeError):
                return self.reply(400, {'message': 'Invalid ban action'})
            status, body = remove_ban(data)
            return self.reply(status, body)
        if self.path in ('/luckperms/editor', '/luckperms/apply'):
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if not 0 < length <= 4096:
                    raise ValueError()
                data = json.loads(self.rfile.read(length))
                if not isinstance(data, dict):
                    raise ValueError()
                if not re.fullmatch(r'[A-Za-z0-9_]{3,32}', data.get('actor', '')):
                    raise ValueError()
                command = 'lp editor groups' if self.path.endswith('/editor') else data.get('command', '')
                if not self.path.endswith('/editor') and not re.fullmatch(r'lp (?:applyedits|trusteditor) [A-Za-z0-9_-]{3,200}', command):
                    raise ValueError()
                # No session token or apply code is logged.
                print(json.dumps({'actor': data['actor'], 'event': 'luckperms-editor' if self.path.endswith('/editor') else 'luckperms-apply'}), flush=True)
                if MODE == 'gateway':
                    status, body = relay(self.path, {'actor': data['actor'], 'command': command}, 22)
                    if status == 200:
                        if self.path.endswith('/editor') and isinstance(body.get('url'), str) and re.fullmatch(r'https://luckperms\.net/editor/[A-Za-z0-9_-]{3,200}', body['url']):
                            return self.reply(200, {'url': body['url']})
                        if self.path.endswith('/apply') and isinstance(body.get('message'), str):
                            return self.reply(200, {'message': scrub(body['message'])})
                    return self.reply(status if status in (409, 503, 504) else 503, {'message': 'LuckPerms did not confirm the planet-host request.'})
                status, body = permission_command(command, self.path.endswith('/editor'))
                return self.reply(status, body)
            except (ValueError, TypeError, KeyError):
                return self.reply(400, {'message': 'Invalid LuckPerms request'})
            except Exception:
                return self.reply(503, {'message': 'LuckPerms is unavailable. Check the Arrakis server status.'})
        if self.path != '/action':
            return self.reply(404, {'message': 'Not found'})
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 4096:
                raise ValueError()
            data = json.loads(self.rfile.read(length))
            server, action = data['server'], data['action']
            if server not in known_servers() or action not in ('start', 'stop', 'restart', 'command'):
                raise ValueError()
            command = data.get('command', '')
            actor = data['actor']
            if not isinstance(actor, str) or not re.fullmatch(r'[A-Za-z0-9_]{3,32}', actor):
                raise ValueError()
            if action == 'command' and (not known_servers()[server][1] or not isinstance(command, str) or not re.fullmatch(r'[A-Za-z][ -~]{0,239}', command)):
                raise ValueError()
        except (ValueError, KeyError, TypeError):
            return self.reply(400, {'message': 'Invalid server action'})
        if server in REMOTE_SERVERS:
            payload = {'server': server, 'action': action, 'actor': actor}
            if action == 'command':
                payload['command'] = command
            status, body = relay('/action', payload)
            if status == 202 and body.get('ok') is True:
                return self.reply(202, {'ok': True, 'message': 'Planet-host action accepted. Status will update shortly.'})
            return self.reply(status if status in (400, 409, 503) else 503, {'message': 'The planet-host action was not accepted. Refresh its status before retrying.'})
        with LOCK:
            if BAN_LOCK.locked() and server in BAN_FOLDERS:
                return self.reply(409, {'message': 'A ban action is running. Try again after it completes.'})
            if JOBS.get(server, {}).get('state') == 'running' or time.monotonic() - LAST.get(server, -100) < 5:
                return self.reply(409, {'message': 'A server action is already running. Wait a moment.'})
            LAST[server] = time.monotonic()
            JOBS[server] = {'state': 'running', 'action': action, 'startedAt': int(time.time() * 1000)}
        threading.Thread(target=perform, args=(server, action, command, actor), daemon=True).start()
        return self.reply(202, {'ok': True, 'message': 'Action accepted. Status will update shortly.'})


def kit_shop_route(handler, method, url):
    routes={'/kits/catalogue','/kits/orders','/kits/arc','/kits/checkout','/kits/cart/checkout','/kits/redeem','/kits/arc/claim'}
    if url.path not in routes:
        return False
    if MODE != 'gateway':
        handler.reply(404, {'message':'Not found'})
        return True
    import kit_commerce
    def key():
        value=Path('/etc/dunerealms-kit-code.key').read_bytes()
        if len(value)!=32:
            raise RuntimeError('Private item-code key unavailable')
        return value
    try:
        if method=='GET' and url.path in {'/kits/catalogue','/kits/orders','/kits/arc'}:
            args=parse_qs(url.query,keep_blank_values=True)
            if url.path=='/kits/catalogue':
                if url.query:raise ValueError()
                result=kit_commerce.catalogue(network_database)
            else:
                if set(args)!={'uuid'} or len(args['uuid'])!=1:raise ValueError()
                player=kit_commerce.identity(args['uuid'][0])
                result=kit_commerce.read_arc(network_database,player) if url.path=='/kits/arc' else kit_commerce.read_orders(network_database,key(),player)
        elif method=='POST' and url.path in {'/kits/checkout','/kits/cart/checkout','/kits/redeem','/kits/arc/claim'} and not url.query:
            length=int(handler.headers.get('Content-Length','0'))
            if not 0<length<=8192:raise ValueError()
            def unique(pairs):
                data={}
                for name,value in pairs:
                    if name in data:raise ValueError()
                    data[name]=value
                return data
            data=json.loads(handler.rfile.read(length),object_pairs_hook=unique)
            if not isinstance(data,dict):raise ValueError()
            if url.path=='/kits/checkout':
                result=kit_commerce.checkout(network_database,key(),data)
            elif url.path=='/kits/cart/checkout':
                result=kit_commerce.checkout_cart(network_database,key(),data)
            elif url.path=='/kits/redeem' and set(data)=={'uuid','code'}:
                result=kit_commerce.queue_redemption(network_database,data['uuid'],data['code'])
            elif url.path=='/kits/arc/claim' and set(data)=={'uuid','index'}:
                result=kit_commerce.claim_arc(network_database,data['uuid'],data['index'])
            else:raise ValueError()
        else:
            handler.reply(405,{'message':'Method not allowed'});return True
        handler.reply(200,result)
    except (ValueError,TypeError,UnicodeError):handler.reply(400,{'message':'Invalid kit commerce request'})
    except PermissionError:handler.reply(403,{'message':'Verified kit ownership or current tier eligibility required'})
    except kit_commerce.Conflict:handler.reply(409,{'message':'The order or listing changed. Review it before buying.'})
    except kit_commerce.InsufficientFunds:handler.reply(409,{'message':'Insufficient Dunemarks','reason':'insufficient-funds'})
    except Exception:handler.reply(503,{'message':'Kit commerce is unavailable. No payment or delivery was confirmed.'})
    return True

def player_kits_route(handler, method, url):
    if url.path not in ('/kits/player', '/kits/player/definition'):
        return False
    if MODE != 'gateway':
        handler.reply(404, {'message': 'Not found'})
        return True
    import player_kits
    try:
        if method == 'GET' and url.path == '/kits/player':
            args = parse_qs(url.query, keep_blank_values=True)
            if set(args) != {'uuid'} or len(args['uuid']) != 1:
                raise ValueError('One verified UUID required')
            result = player_kits.read(network_database, args['uuid'][0])
        elif method == 'POST' and url.path == '/kits/player/definition' and not url.query:
            length = int(handler.headers.get('Content-Length', '0'))
            if not 0 < length <= 8192:
                raise ValueError('Invalid request size')
            def unique(pairs):
                result = {}
                for key, value in pairs:
                    if key in result:
                        raise ValueError('Duplicate field')
                    result[key] = value
                return result
            data = json.loads(handler.rfile.read(length), object_pairs_hook=unique)
            result = player_kits.save(network_database, data)
        else:
            handler.reply(405, {'message': 'Method not allowed'})
            return True
        handler.reply(200, result)
    except (ValueError, TypeError, UnicodeError):
        handler.reply(400, {'message': 'Invalid office blueprint request'})
    except PermissionError:
        handler.reply(403, {'message': 'Current elected office required'})
    except player_kits.Conflict:
        handler.reply(409, {'message': 'Blueprint changed; reload before saving'})
    except Exception:
        handler.reply(503, {'message': 'Office blueprint storage is unavailable'})
    return True

def quest_route(handler, method, url):
    if not url.path.startswith('/quests/'):
        return False
    if MODE != 'gateway':
        handler.reply(404, {'message': 'Quest operations are available only through the gateway'})
        return True
    try:
        from quest_engine import dispatch
        body = None
        if method == 'POST':
            length = int(handler.headers.get('Content-Length', '0'))
            if not 0 < length <= 16384:
                raise ValueError()
            body = json.loads(handler.rfile.read(length))
        status, result = dispatch(network_database, method, url.path, parse_qs(url.query), body)
        handler.reply(status, result)
    except (ValueError, TypeError):
        handler.reply(400, {'message': 'Invalid quest request'})
    except Exception:
        handler.reply(503, {'message': 'The authoritative quest engine is unavailable. No state was invented.'})
    return True

def managed_permissions_route(handler, url):
    if url.path not in ('/bank/permissions', '/permissions/teams', '/permissions/server-teams'):
        return False
    if MODE != 'gateway':
        handler.reply(404, {'message': 'Managed game permissions are available only through the gateway'})
        return True
    try:
        from managed_game_roles import dispatch, strict_json
        if url.query:
            raise ValueError()
        length = int(handler.headers.get('Content-Length', '0'))
        if not 0 < length <= 262144:
            raise ValueError()
        body = strict_json(handler.rfile.read(length))
        if url.path == '/permissions/server-teams':
            from server_game_roles import dispatch as scoped_dispatch
            status, result = scoped_dispatch(permissions_database, body)
        else:
            status, result = dispatch(permissions_database, body, teams=url.path == '/permissions/teams')
        handler.reply(status, result)
    except (ValueError, TypeError):
        handler.reply(400, {'message': 'Invalid managed game-permission request'})
    except Exception:
        handler.reply(503, {'message': 'Native game permission synchronization is unavailable'})
    return True

if __name__ == '__main__':
    configure(os.environ.get('DUNE_CONTROL_MODE', 'legacy'))
    SECRET = Path('/etc/dunerealms-server-control.secret').read_text().strip()
    if len(SECRET) < 64:
        raise SystemExit('Private control secret is missing')
    ThreadingHTTPServer(('10.77.0.2' if MODE == 'planets' else '127.0.0.1', 25573), Handler).serve_forever()


