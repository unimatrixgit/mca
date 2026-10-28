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
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import urlopen
from urllib.parse import urlsplit, parse_qs

SERVERS = {
    'arrakis': ('dune-arrakis', '/opt/dunerealms/servers/arrakis/console.fifo', 'Arrakduin'),
    'lobby': ('dune-lobby', '/opt/dunerealms/lobby/console.fifo', 'Vaerkring lobby'),
    'proxy': ('dune-proxy', None, 'Network gateway'),
}
LOCK = threading.Lock()
JOBS = {}
LAST = {}
SECRET = None

def run(args, timeout=8):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)

def scrub(text):
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
        if url.path == '/servers' and not url.query:
            return self.reply(200, state())
        args = parse_qs(url.query)
        if url.path == '/logs' and set(args) == {'server'} and len(args['server']) == 1 and args['server'][0] in SERVERS:
            unit = SERVERS[args['server'][0]][0]
            result = run(['journalctl', '-u', unit, '-n', '160', '--no-pager', '-o', 'cat'])
            return self.reply(200, {'log': scrub(result.stdout)})
        return self.reply(404, {'message': 'Not found'})

    def do_POST(self):
        if not self.authorized():
            return self.reply(401, {'message': 'Unauthorized'})
        if self.path != '/action':
            return self.reply(404, {'message': 'Not found'})
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 4096:
                raise ValueError()
            data = json.loads(self.rfile.read(length))
            server, action = data['server'], data['action']
            if server not in SERVERS or action not in ('start', 'stop', 'restart', 'command'):
                raise ValueError()
            command = data.get('command', '')
            actor = data['actor']
            if not isinstance(actor, str) or not re.fullmatch(r'[A-Za-z0-9_]{3,32}', actor):
                raise ValueError()
            if action == 'command' and (not SERVERS[server][1] or not isinstance(command, str) or not re.fullmatch(r'[A-Za-z][ -~]{0,239}', command)):
                raise ValueError()
        except (ValueError, KeyError, TypeError):
            return self.reply(400, {'message': 'Invalid server action'})
        with LOCK:
            if JOBS.get(server, {}).get('state') == 'running' or time.monotonic() - LAST.get(server, -100) < 5:
                return self.reply(409, {'message': 'A server action is already running. Wait a moment.'})
            LAST[server] = time.monotonic()
            JOBS[server] = {'state': 'running', 'action': action, 'startedAt': int(time.time() * 1000)}
        threading.Thread(target=perform, args=(server, action, command, actor), daemon=True).start()
        return self.reply(202, {'ok': True, 'message': 'Action accepted. Status will update shortly.'})

if __name__ == '__main__':
    SECRET = Path('/etc/dunerealms-server-control.secret').read_text().strip()
    if len(SECRET) < 64:
        raise SystemExit('Private control secret is missing')
    ThreadingHTTPServer(('127.0.0.1', 25573), Handler).serve_forever()
