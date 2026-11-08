#!/usr/bin/python3
"""Private forced-SSH internal inbox service. CP authenticates its session owner.

Never expose this program directly as a public HTTP service. Mail credentials
remain encrypted in mailadmin; only explicit step-up reveal returns a password.
"""
import base64
import contextlib
import datetime
import email
import email.policy
import email.utils
import fcntl
import imaplib
import json
import os
import pathlib
import re
import secrets
import shutil
import smtplib
import ssl
import subprocess
import sys
import time
import uuid
from email.message import EmailMessage
from cryptography.fernet import Fernet

ROOT = pathlib.Path('/etc/dune-internal-mail')
HOST = 'mail.lgbbenelux.org'
WEBMAIL = 'https://www.lgbbenelux.org/webmail/'
FILE_LIMIT = 10 * 1024 * 1024
MESSAGE_LIMIT = 20 * 1024 * 1024
REQUEST_LIMIT = 32 * 1024 * 1024
FOLDERS = {'INBOX', 'Sent', 'Drafts', 'Trash', 'Junk'}
ACTIONS = {'internal_status', 'internal_ensure', 'internal_list', 'internal_get',
           'internal_attachment', 'internal_send', 'internal_reveal_password', 'internal_set_password',
           'internal_mark_read', 'internal_delete', 'internal_tags', 'internal_forwarding',
           'internal_move', 'internal_create_folder', 'internal_retire_clan',
           'internal_rename_folder', 'internal_delete_folder'}


class MailError(Exception):
    def __init__(self, message, code=400):
        super().__init__(message)
        self.code = code


def sql(query):
    result = subprocess.run(['runuser', '-u', 'postgres', '--', 'psql', '-X', '-qAt',
                             '-v', 'ON_ERROR_STOP=1', '-d', 'mailadmin'],
                            input=query, text=True, capture_output=True, timeout=15)
    if result.returncode:
        raise MailError('Internal mail database unavailable', 503)
    return result.stdout.strip()


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def canonical(value):
    try:
        parsed = str(uuid.UUID(value))
    except (ValueError, TypeError, AttributeError):
        raise MailError('Invalid user identity')
    if parsed != value:
        raise MailError('Use the canonical immutable user ID')
    return parsed


def address(user):
    return canonical(user) + '.internal@dunerealms.com'


def policy():
    return json.loads((ROOT / 'policy.json').read_text())


def cipher():
    return Fernet((ROOT / 'credential.key').read_bytes())


def entry(user):
    result = sql('SELECT row_to_json(a) FROM dune_internal_mail_accounts a WHERE user_id=' + quote(user) + ';')
    return json.loads(result) if result else None


def audit(user, action):
    sql('INSERT INTO dune_internal_mail_audit(user_id,action) VALUES (' + quote(user) + ',' + quote(action) + ');')


def password_hash(password):
    result = subprocess.run(['doveadm', '-O', 'pw', '-s', 'SHA512-CRYPT'],
                            input=password + '\n' + password + '\n', text=True,
                            capture_output=True, timeout=15, check=True)
    value = result.stdout.strip()
    if not re.fullmatch(r'\{SHA512-CRYPT\}\$6\$[A-Za-z0-9./$=]+', value):
        raise MailError('Password hashing unavailable', 503)
    return value


def status(user):
    row = entry(user)
    enabled = bool(policy().get('provisioningEnabled'))
    caps = dict(provision=enabled, read=True, send=True, forward=True, attachments=True,
                revealPassword=True, changePassword=True, webmail=True, webmailSso=False, tags=True, delete=True, markRead=True)
    result = dict(ok=True, configured=bool(row), active=False, capabilities=caps,
                  limits=dict(fileBytes=FILE_LIMIT, messageBytes=MESSAGE_LIMIT, recipients=20, retentionDays=30, mailboxBytes=104857600), webmailUrl=WEBMAIL)
    if row:
        result.update(address=row['address'], credentialRevision=row['revision'])
        result['active'] = row['active'] and sql('SELECT active FROM mailbox WHERE username=' + quote(row['address']) + ';') == 't'
        destinations = sql('SELECT goto FROM alias WHERE address=' + quote(row['address']) + ';').split(',')
        result['forwardingAddress'] = next((v.strip() for v in destinations if v.strip() and v.strip() != row['address']), None)
    return result


def forwarding(user, row, request):
    destination = request.get('address')
    if destination is not None:
        if request.get('verified') is not True or not isinstance(destination, str):
            raise MailError('Verify the forwarding email address first', 403)
        if len(destination) > 254 or not re.fullmatch(r'[A-Za-z0-9.!#$%&\x27*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,63}', destination):
            raise MailError('Invalid forwarding email address')
        if destination.lower() == row['address'].lower() or destination.lower().endswith('.internal@dunerealms.com'):
            raise MailError('Use an external forwarding address to avoid internal forwarding loops')
    destinations = row['address'] + (',' + destination if destination else '')
    sql('INSERT INTO alias(address,goto,domain,active) VALUES (' + quote(row['address']) + ',' + quote(destinations) + ", 'dunerealms.com',true) ON CONFLICT(address) DO UPDATE SET goto=EXCLUDED.goto,active=true;")
    audit(user, 'forwarding-enabled' if destination else 'forwarding-disabled')
    return dict(ok=True, forwardingAddress=destination, keepInternalCopy=True)


def ensure(user, request):
    if request.get('eligible') is not True:
        raise MailError('Website account is not eligible for an internal mailbox', 403)
    if not policy().get('provisioningEnabled'):
        raise MailError('Automatic mailbox creation is awaiting reconciliation', 503)
    existing = entry(user)
    if existing:
        if request.get('mailboxKind') in ('clan', 'staff', 'faction', 'imperial') and (existing.get('mailbox_kind') != request['mailboxKind'] or existing.get('resource_id') != canonical(request.get('resourceId'))):
            raise MailError('Shared mailbox binding conflict', 409)
        return status(user)
    kind = request.get('mailboxKind', 'personal')
    resource = canonical(request.get('resourceId')) if kind in ('clan', 'staff', 'faction', 'imperial') else None
    if kind not in ('personal', 'clan', 'staff', 'faction', 'imperial'):
        raise MailError('Unsupported mailbox kind')
    local = request.get('localPart') if kind in ('clan', 'staff', 'faction', 'imperial') else user + '.internal'
    if not isinstance(local, str) or (kind != 'personal' and not re.fullmatch(r'[a-z0-9][a-z0-9-]{0,48}\.internal', local)) or (kind == 'staff' and local != 'staff.internal') or (kind == 'imperial' and local != 'imperial.internal'):
        raise MailError('Invalid clan mailbox name')
    target = local + '@dunerealms.com'
    collision = sql('SELECT EXISTS(SELECT 1 FROM mailbox WHERE username=' + quote(target) +
                    ' UNION SELECT 1 FROM alias WHERE address=' + quote(target) + ');')
    if collision == 't':
        raise MailError('Unlinked existing mailbox preserved; administrator review required', 409)
    if sql("SELECT active FROM domain WHERE domain='dunerealms.com';") != 't':
        raise MailError('Mail domain is unavailable', 503)
    secret = secrets.token_urlsafe(48)
    encrypted = cipher().encrypt(secret.encode()).decode()
    hashed = password_hash(secret)
    home = pathlib.Path('/var/vmail/dunerealms.com') / local
    for path in [home, home / 'cur', home / 'new', home / 'tmp']:
        path.mkdir(mode=0o700, parents=True, exist_ok=True)
        os.chown(path, 5000, 5000)
        path.chmod(0o700)
    # Provider password and encrypted managed credential commit together.
    sql('BEGIN; INSERT INTO mailbox(username,password,name,maildir,domain,local_part,active) VALUES (' +
        ','.join(quote(v) for v in [target, hashed, 'DuneRealms internal inbox',
                                   'dunerealms.com/' + local + '/', 'dunerealms.com', local]) + ',true);' +
        'INSERT INTO alias(address,goto,domain,active) VALUES (' + quote(target) + ',' + quote(target) + ", 'dunerealms.com',true);" +
        'INSERT INTO dune_internal_mail_accounts(user_id,address,credential_ciphertext,mailbox_kind,resource_id) VALUES (' +
        quote(user) + ',' + quote(target) + ',' + quote(encrypted) + ',' + quote(kind) + ',' + (quote(resource) if resource else 'NULL') + '); COMMIT;')
    audit(user, 'created')
    return status(user)


def step_up(request):
    stamp = request.get('reauthenticatedAt')
    # Unix SECONDS, injected by trusted CP backend from its server-side reauth state.
    if type(stamp) not in (int, float) or not 0 <= time.time() - stamp <= 300:
        raise MailError('Fresh website password verification required', 403)


def valid_managed_address(user, row):
    if row.get('mailbox_kind') in ('clan', 'staff', 'faction', 'imperial'):
        return bool(row.get('resource_id') and re.fullmatch(r'[a-z0-9][a-z0-9-]{0,48}\.internal@dunerealms\.com', row['address']))
    return row['address'] == address(user)


def retire_clan(user, request):
    row = entry(user)
    if not row:
        return dict(ok=True, removed=True)
    if row.get('mailbox_kind') != 'clan' or row.get('resource_id') != canonical(request.get('resourceId')):
        raise MailError('Clan mailbox binding mismatch', 403)
    if not valid_managed_address(user, row):
        raise MailError('Clan mailbox address conflict', 409)
    root = pathlib.Path('/var/vmail/dunerealms.com').resolve()
    home = root / row['address'].split('@')[0]
    if home.is_symlink() or home.resolve().parent != root:
        raise MailError('Mailbox path conflict', 409)
    sql('BEGIN; UPDATE dune_internal_mail_accounts SET active=false WHERE user_id=' + quote(user) + '; UPDATE mailbox SET active=false WHERE username=' + quote(row['address']) + '; DELETE FROM alias WHERE address=' + quote(row['address']) + '; COMMIT;')
    subprocess.run(['doveadm', 'kick', row['address']], capture_output=True, timeout=15)
    if home.exists():
        shutil.rmtree(home)
    sql('BEGIN; DELETE FROM dune_internal_mail_tags WHERE user_id=' + quote(user) + '; DELETE FROM dune_internal_mail_outbox WHERE user_id=' + quote(user) + '; DELETE FROM mailbox WHERE username=' + quote(row['address']) + '; DELETE FROM dune_internal_mail_accounts WHERE user_id=' + quote(user) + '; COMMIT;')
    return dict(ok=True, removed=True)


def active_entry(user):
    row = entry(user)
    if not row or not row['active']:
        raise MailError('No active internal mailbox', 403)
    if not valid_managed_address(user, row):
        raise MailError('Internal mailbox binding conflict', 409)
    if sql('SELECT active FROM mailbox WHERE username=' + quote(row['address']) + ';') != 't':
        raise MailError('Internal mailbox is disabled', 403)
    return row


def credentials(row):
    return row['address'], cipher().decrypt(row['credential_ciphertext'].encode()).decode()


@contextlib.contextmanager
def imap(row):
    conn = imaplib.IMAP4_SSL(HOST, 993, ssl_context=ssl.create_default_context(), timeout=15)
    try:
        conn.login(*credentials(row))
        available = set(folders(conn))
        for name in sorted(FOLDERS - available):
            code, _ = conn.create('"' + name + '"')
            if code != 'OK':
                raise MailError('Default mail folder could not be prepared', 503)
            conn.subscribe('"' + name + '"')
        yield conn
    finally:
        try:
            conn.logout()
        except Exception:
            pass


def folder_name(value):
    if not isinstance(value, str) or not 1 <= len(value) <= 96 or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9 _./-]*', value) or any(p.strip() in ('', '.', '..') for p in value.split('/')):
        raise MailError('Use a folder name with letters, numbers, spaces, dashes or slashes')
    return value


def folders(conn):
    code, rows = conn.list()
    if code != 'OK':
        raise MailError('Folder list unavailable', 503)
    names = []
    for raw in rows:
        if not isinstance(raw, bytes) or b'\\Noselect' in raw:
            continue
        match = re.match(rb'\([^)]*\) (?:"[^"]*"|NIL) (.*)$', raw)
        if not match:
            continue
        value = match[1].decode('ascii', errors='replace').strip('"')
        try:
            names.append(folder_name(value))
        except MailError:
            continue
    return sorted(set(names), key=lambda name: (name not in FOLDERS, name.lower()))


def create_folder(row, request):
    name = folder_name(request.get('name'))
    if name.upper() == 'CLAN':
        raise MailError('CLAN is reserved for your shared clan mailbox')
    with imap(row) as conn:
        if name in folders(conn):
            return dict(ok=True, folders=folders(conn), created=False)
        code, _ = conn.create('"' + name + '"')
        if code != 'OK':
            raise MailError('Folder could not be created', 409)
        conn.subscribe('"' + name + '"')
        return dict(ok=True, folders=folders(conn), created=True)


def edit_folder(row, request):
    name = folder_name(request.get('name'))
    if name.upper() in {value.upper() for value in FOLDERS} | {'CLAN'}:
        raise MailError('Standard mail folders cannot be renamed or removed', 403)
    with imap(row) as conn:
        existing = folders(conn)
        if name not in existing:
            raise MailError('Folder not found', 404)
        if request['action'] == 'internal_rename_folder':
            target = folder_name(request.get('newName'))
            if target.upper() in {value.upper() for value in FOLDERS} | {'CLAN'} or target in existing:
                raise MailError('Choose a new custom folder name', 409)
            code, _ = conn.rename('"' + name + '"', '"' + target + '"')
            if code != 'OK':
                raise MailError('Folder could not be renamed', 409)
            for source in existing:
                if source == name or source.startswith(name + '/'):
                    destination = target + source[len(name):]
                    sql('UPDATE dune_internal_mail_tags SET folder=' + quote(destination) + ' WHERE user_id=' + quote(row['user_id']) + ' AND folder=' + quote(source) + ';')
            conn.subscribe('"' + target + '"')
            return dict(ok=True, folders=folders(conn), renamedTo=target)
        if any(value.startswith(name + '/') for value in existing):
            raise MailError('Remove or move child folders first', 409)
        code, _ = conn.select('"' + name + '"', readonly=True)
        code, values = conn.uid('search', None, 'ALL')
        if code != 'OK' or values[0]:
            raise MailError('Move all messages out before deleting this folder', 409)
        conn.unselect()
        code, _ = conn.delete('"' + name + '"')
        if code != 'OK':
            raise MailError('Folder could not be removed', 409)
        sql('DELETE FROM dune_internal_mail_tags WHERE user_id=' + quote(row['user_id']) + ' AND folder=' + quote(name) + ';')
        return dict(ok=True, folders=folders(conn), removed=True)


def select(conn, request, readonly=True):
    folder = folder_name(request.get('folder', 'INBOX'))
    code, _ = conn.select('"' + folder + '"', readonly=readonly)
    if code != 'OK':
        raise MailError('Folder is unavailable', 404)
    _, value = conn.response('UIDVALIDITY')
    validity = value[0].decode() if value else None
    if not validity:
        raise MailError('Mailbox identity unavailable', 503)
    return validity


def uid_request(conn, request, readonly=True):
    validity = select(conn, request, readonly)
    if str(request.get('uidValidity', '')) != validity:
        raise MailError('Mailbox changed; refresh the message list', 409)
    uid = str(request.get('uid', ''))
    if not re.fullmatch(r'[1-9][0-9]{0,9}', uid):
        raise MailError('Invalid message UID')
    return uid


def raw_message(conn, request):
    uid = uid_request(conn, request)
    code, size = conn.uid('fetch', uid, '(RFC822.SIZE INTERNALDATE)')
    metadata = b' '.join(x for x in size if isinstance(x, bytes))
    match = re.search(rb'RFC822.SIZE (\d+)', metadata)
    if code != 'OK' or not match:
        raise MailError('Message not found', 404)
    if expired(metadata):
        raise MailError('This message expired after 30 days', 410)
    if int(match.group(1)) > MESSAGE_LIMIT:
        raise MailError('Message exceeds CP limit; open it in webmail', 413)
    code, parts = conn.uid('fetch', uid, '(BODY.PEEK[])')
    data = next((p[1] for p in parts if isinstance(p, tuple)), None)
    if code != 'OK' or data is None:
        raise MailError('Message not found', 404)
    return email.message_from_bytes(data, policy=email.policy.default)


def expired(metadata):
    parsed = imaplib.Internaldate2tuple(metadata)
    if parsed is None:
        raise MailError('Message retention timestamp unavailable', 503)
    return time.mktime(parsed) <= time.time() - 30 * 86400


def message_list(row, request):
    limit = request.get('limit', 25)
    offset = request.get('offset', 0)
    if type(limit) is not int or not 1 <= limit <= 50 or type(offset) is not int or not 0 <= offset <= 100000:
        raise MailError('Invalid message pagination')
    with imap(row) as conn:
        validity = select(conn, request)
        since = (datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=30)).strftime('%d-%b-%Y')
        ordering = {'newest': '(REVERSE DATE)', 'oldest': '(DATE)', 'sender': '(FROM)', 'subject': '(SUBJECT)'}
        sort = request.get('sort', 'newest')
        if sort not in ordering:
            raise MailError('Unsupported message sort order')
        code, values = conn.uid('SORT', ordering[sort], 'UTF-8', 'SINCE', since, 'UNSEEN' if request.get('unread') is True else 'ALL')
        if code != 'OK':
            raise MailError('Message list unavailable', 503)
        uids = values[0].split()
        tagrows = sql('SELECT COALESCE(json_object_agg(uid,tags),\'{}\'::json) FROM dune_internal_mail_tags WHERE user_id=' + quote(row['user_id']) + ' AND folder=' + quote(request.get('folder', 'INBOX')) + ' AND uid_validity=' + quote(validity) + ';')
        tagmap = json.loads(tagrows or '{}')
        if request.get('tag'):
            uids = [u for u in uids if request['tag'] in tagmap.get(u.decode(), [])]
        items = []
        for uid in uids[offset:offset + limit]:
            code, values = conn.uid('fetch', uid, '(UID FLAGS RFC822.SIZE INTERNALDATE BODY.PEEK[HEADER.FIELDS (DATE FROM TO CC SUBJECT MESSAGE-ID)])')
            part = next((v for v in values if isinstance(v, tuple)), None)
            if code != 'OK' or not part or len(part[1]) > 65536:
                continue
            if expired(part[0]):
                continue
            head = email.message_from_bytes(part[1], policy=email.policy.default)
            size = re.search(rb'RFC822.SIZE (\d+)', part[0])
            items.append(dict(uid=uid.decode(), subject=str(head.get('Subject', '')), sender=str(head.get('From', '')),
                              to=str(head.get('To', '')), cc=str(head.get('Cc', '')), date=str(head.get('Date', '')),
                              seen=b'\\Seen' in part[0], size=int(size.group(1)) if size else 0, tags=tagmap.get(uid.decode(), [])))
        catalog = json.loads(sql('SELECT COALESCE(json_agg(DISTINCT tag),\'[]\'::json) FROM dune_internal_mail_tags, jsonb_array_elements_text(tags) tag WHERE user_id=' + quote(row['user_id']) + ';') or '[]')
        return dict(ok=True, uidValidity=validity, total=len(uids), items=items, folders=folders(conn), tags=catalog)


def tags(row, request):
    labels = request.get('tags')
    if not isinstance(labels, list) or len(labels) > 10 or any(not isinstance(t, str) or not 1 <= len(t.strip()) <= 48 or re.search(r'[\x00-\x1f\x7f]', t) for t in labels):
        raise MailError('Use up to 10 tags, each 1–48 characters')
    labels = list(dict.fromkeys(t.strip() for t in labels))
    with imap(row) as conn:
        uid = uid_request(conn, request)
        code, values = conn.uid('fetch', uid, '(UID)')
        if code != 'OK' or not values or values == [None]:
            raise MailError('Message not found', 404)
    sql('INSERT INTO dune_internal_mail_tags(user_id,folder,uid_validity,uid,tags) VALUES (' +
        ','.join(quote(x) for x in [row['user_id'], request.get('folder', 'INBOX'), str(request['uidValidity']), uid, json.dumps(labels)]) +
        ') ON CONFLICT(user_id,folder,uid_validity,uid) DO UPDATE SET tags=excluded.tags,modified_at=NOW();')
    return dict(ok=True, tags=labels)


def mark_read(row, request):
    if type(request.get('seen')) is not bool:
        raise MailError('Seen must be true or false')
    with imap(row) as conn:
        uid = uid_request(conn, request, False)
        code, _ = conn.uid('store', uid, '+FLAGS.SILENT' if request['seen'] else '-FLAGS.SILENT', '(\\Seen)')
        if code != 'OK':
            raise MailError('Message flag could not be changed', 503)
    return dict(ok=True)


def move(row, request):
    destination = folder_name(request.get('destination'))
    if destination == request.get('folder', 'INBOX'):
        return dict(ok=True, movedTo=destination)
    with imap(row) as conn:
        if destination not in folders(conn):
            raise MailError('Destination folder is unavailable', 404)
        uid = uid_request(conn, request, False)
        code, found = conn.uid('fetch', uid, '(UID)')
        if code != 'OK' or not found or found == [None]:
            raise MailError('Message not found', 404)
        code, _ = conn.uid('MOVE', uid, '"' + destination + '"')
        if code != 'OK':
            raise MailError('Message could not be moved', 503)
        _, copied = conn.response('COPYUID')
        if copied and copied[0]:
            bits = copied[0].decode().split()
            if len(bits) == 3 and all(re.fullmatch(r'[1-9][0-9]{0,9}', x) for x in bits):
                sql('UPDATE dune_internal_mail_tags SET folder=' + quote(destination) + ',uid_validity=' + quote(bits[0]) + ',uid=' + quote(bits[2]) + ' WHERE user_id=' + quote(row['user_id']) + ' AND folder=' + quote(request.get('folder', 'INBOX')) + ' AND uid_validity=' + quote(str(request['uidValidity'])) + ' AND uid=' + quote(uid) + ';')
    return dict(ok=True, movedTo=destination)


def delete(row, request):
    if request.get('folder') == 'Trash':
        raise MailError('Permanent deletion is available only in webmail', 403)
    return move(row, dict(request, destination='Trash'))


def attachment_parts(message):
    return [(str(i), part) for i, part in enumerate(message.walk())
            if part.get_content_disposition() == 'attachment' or part.get_filename()]


def attachment_bytes(part):
    data = part.get_payload(decode=True)
    if data is None and part.get_content_type() == 'message/rfc822':
        payload = part.get_payload()
        if isinstance(payload, list):
            return b'\r\n'.join(item.as_bytes(policy=email.policy.SMTP) for item in payload)
    return data or b''


def get_message(row, request):
    with imap(row) as conn:
        message = raw_message(conn, request)
    attachments = [dict(partId=i, filename=p.get_filename() or 'attachment', contentType=p.get_content_type(),
                        size=len(attachment_bytes(p))) for i, p in attachment_parts(message)]
    text, html = message.get_body(preferencelist=('plain',)), message.get_body(preferencelist=('html',))
    def body(part):
        return str(part.get_content())[:1048576] if part else ''
    return dict(ok=True, subject=str(message.get('Subject', '')), sender=str(message.get('From', '')),
                to=str(message.get('To', '')), cc=str(message.get('Cc', '')), date=str(message.get('Date', '')),
                messageId=str(message.get('Message-ID', '')),
                reportId=str(message.get('X-Dune-Mail-Report-ID', '')) or None,
                text=body(text), html=body(html), attachments=attachments)


def attachment(row, request):
    with imap(row) as conn:
        message = raw_message(conn, request)
    part = next((p for i, p in attachment_parts(message) if i == str(request.get('partId', ''))), None)
    if part is None:
        raise MailError('Attachment not found', 404)
    data = attachment_bytes(part)
    if data is None or len(data) > FILE_LIMIT:
        raise MailError('Attachment exceeds CP limit; open webmail', 413)
    return dict(ok=True, filename=part.get_filename() or 'attachment', contentType=part.get_content_type(),
                dataBase64=base64.b64encode(data).decode())


def recipients(value):
    if not isinstance(value, list) or len(value) > 20:
        raise MailError('Recipients must be an array with at most 20 addresses')
    output = []
    for item in value:
        if not isinstance(item, str) or len(item) > 254 or not re.fullmatch(r"[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\.[A-Za-z]{2,63}", item):
            raise MailError('Use plain email addresses without display names or line breaks')
        if '..' in item or len(item.split('@')[0]) > 64:
            raise MailError('Invalid recipient address')
        if not re.fullmatch(r'[a-z0-9][a-z0-9-]*\.internal@dunerealms\.com', item, re.I):
            raise MailError('Internal Mail cannot send to external recipients', 403)
        output.append(item)
    return output


def build_message(row, request, forwarded=None):
    to, cc, bcc = (recipients(request.get(k, [])) for k in ('to', 'cc', 'bcc'))
    targets = list(dict.fromkeys(to + cc + bcc))
    if not targets or len(targets) > 20:
        raise MailError('Use 1–20 recipients across To, Cc and Bcc')
    subject = request.get('subject', '')
    text, html = request.get('text', ''), request.get('html', '')
    if not isinstance(subject, str) or len(subject) > 200 or any(c in subject for c in '\r\n\0'):
        raise MailError('Invalid subject')
    if not all(isinstance(x, str) and len(x.encode()) <= 1048576 for x in (text, html)):
        raise MailError('Message body exceeds limit', 413)
    message = EmailMessage(policy=email.policy.SMTP)
    sender_name = request.get('senderName', '')
    if not isinstance(sender_name, str) or len(sender_name) > 100 or re.search(r'[\x00-\x1f\x7f]', sender_name):
        raise MailError('Invalid sender name')
    message['From'] = email.utils.formataddr((sender_name, row['address'])) if sender_name else row['address']
    if to:
        message['To'] = ', '.join(to)
    if cc:
        message['Cc'] = ', '.join(cc)
    message['Subject'] = subject
    message['Date'] = email.utils.formatdate(localtime=False)
    message['Message-ID'] = email.utils.make_msgid(domain='dunerealms.com')
    if request.get('reportId'):
        message['X-Dune-Mail-Report-ID'] = canonical(request['reportId'])
    message.set_content(text)
    if html:
        message.add_alternative(html, subtype='html')
    attachments = request.get('attachments', [])
    if not isinstance(attachments, list) or len(attachments) > 20:
        raise MailError('Use at most 20 attachments')
    total = 0
    for item in attachments:
        if not isinstance(item, dict):
            raise MailError('Invalid attachment')
        filename = item.get('filename', '')
        kind = item.get('contentType', 'application/octet-stream')
        if not isinstance(filename, str) or not 1 <= len(filename.encode()) <= 180 or re.search(r'[\x00-\x1f\x7f/\\]', filename):
            raise MailError('Invalid attachment filename')
        if not isinstance(kind, str) or not re.fullmatch(r'[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+', kind):
            raise MailError('Invalid attachment type')
        try:
            data = base64.b64decode(item.get('dataBase64', ''), validate=True)
        except (ValueError, TypeError):
            raise MailError('Invalid attachment encoding')
        total += len(data)
        if len(data) > FILE_LIMIT or total > MESSAGE_LIMIT:
            raise MailError('Attachment exceeds size limit', 413)
        message.add_attachment(data, maintype=kind.split('/')[0], subtype=kind.split('/')[1], filename=filename)
    if forwarded:
        message.add_attachment(forwarded, filename='original-message.eml')
    raw = message.as_bytes()
    if len(raw) > MESSAGE_LIMIT:
        raise MailError('Encoded MIME message exceeds 20 MiB', 413)
    return message, raw, targets


def send(row, request):
    request_id = canonical(request.get('requestId'))
    previous = sql('SELECT row_to_json(o) FROM dune_internal_mail_outbox o WHERE user_id=' + quote(row['user_id']) + ' AND request_id=' + quote(request_id) + ';')
    if previous:
        previous = json.loads(previous)
        if previous['state'] == 'sent':
            return dict(ok=True, accepted=True, replay=True, messageId=previous['message_id'], savedToSent=previous['saved_to_sent'])
        raise MailError('Previous send outcome requires review; do not resend automatically', 409)
    count = int(sql('SELECT count(*) FROM dune_internal_mail_outbox WHERE user_id=' + quote(row['user_id']) + " AND created_at>NOW()-interval '10 minutes';"))
    if count >= 10:
        raise MailError('Sending limit reached; try again later', 429)
    forwarded = None
    if request.get('forward') is not None:
        if not isinstance(request['forward'], dict):
            raise MailError('Invalid forward source')
        with imap(row) as conn:
            forwarded = raw_message(conn, request['forward'])
    message, raw, targets = build_message(row, request, forwarded)
    sql('INSERT INTO dune_internal_mail_outbox(user_id,request_id,message_id,state) VALUES (' + quote(row['user_id']) + ',' + quote(request_id) + ',' + quote(str(message['Message-ID'])) + ",'sending');")
    try:
        with smtplib.SMTP(HOST, 587, timeout=20) as conn:
            conn.starttls(context=ssl.create_default_context())
            conn.login(*credentials(row))
            refused = conn.sendmail(row['address'], targets, raw)
    except Exception:
        sql("UPDATE dune_internal_mail_outbox SET state='uncertain' WHERE user_id=" + quote(row['user_id']) + ' AND request_id=' + quote(request_id) + ';')
        raise MailError('Send outcome uncertain; check Sent/webmail before retrying', 409)
    sql("UPDATE dune_internal_mail_outbox SET state='sent' WHERE user_id=" + quote(row['user_id']) + ' AND request_id=' + quote(request_id) + ';')
    saved = False
    try:
        with imap(row) as conn:
            conn.create('Sent')
            saved = conn.append('Sent', '\\Seen', imaplib.Time2Internaldate(time.time()), raw)[0] == 'OK'
    except Exception:
        pass
    sql('UPDATE dune_internal_mail_outbox SET saved_to_sent=' + ('true' if saved else 'false') + ' WHERE user_id=' + quote(row['user_id']) + ' AND request_id=' + quote(request_id) + ';')
    audit(row['user_id'], 'sent')
    return dict(ok=True, accepted=True, messageId=str(message['Message-ID']), savedToSent=saved, refusedRecipients=list(refused))


def rotate(user, row, request):
    step_up(request)
    request_id = canonical(request.get('requestId'))
    if row.get('last_rotation_request') == request_id:
        return dict(ok=True, credentialRevision=row['revision'], replay=True)
    password = secrets.token_urlsafe(48) if request.get('generate') is True else request.get('password')
    if not isinstance(password, str) or not 12 <= len(password) <= 256 or any(x in password for x in '\r\n\0'):
        raise MailError('Use 12–256 characters without line breaks')
    encrypted = cipher().encrypt(password.encode()).decode()
    hashed = password_hash(password)
    sql('BEGIN; SELECT 1 FROM dune_internal_mail_accounts WHERE user_id=' + quote(user) + ' FOR UPDATE;' +
        'UPDATE mailbox SET password=' + quote(hashed) + ',modified=NOW() WHERE username=' + quote(row['address']) + ' AND active=true;' +
        'UPDATE dune_internal_mail_accounts SET credential_ciphertext=' + quote(encrypted) + ',revision=revision+1,last_rotation_request=' + quote(request_id) + ',modified_at=NOW() WHERE user_id=' + quote(user) + '; COMMIT;')
    subprocess.run(['doveadm', 'auth', 'cache', 'flush', row['address']], capture_output=True, timeout=10)
    audit(user, 'password-rotated')
    return dict(ok=True, credentialRevision=entry(user)['revision'])


def initialize():
    ROOT.mkdir(mode=0o700, exist_ok=True)
    if not (ROOT / 'credential.key').exists():
        fd = os.open(ROOT / 'credential.key', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as stream:
            stream.write(Fernet.generate_key())
    if not (ROOT / 'policy.json').exists():
        (ROOT / 'policy.json').write_text(json.dumps(dict(provisioningEnabled=False)))
        (ROOT / 'policy.json').chmod(0o600)
    sql('''CREATE TABLE IF NOT EXISTS dune_internal_mail_accounts (
        user_id uuid PRIMARY KEY,address text UNIQUE NOT NULL,credential_ciphertext text NOT NULL,
        active boolean NOT NULL DEFAULT true,revision integer NOT NULL DEFAULT 1,last_rotation_request uuid,
        created_at timestamptz NOT NULL DEFAULT NOW(),modified_at timestamptz NOT NULL DEFAULT NOW());
        CREATE TABLE IF NOT EXISTS dune_internal_mail_outbox (
        user_id uuid NOT NULL REFERENCES dune_internal_mail_accounts(user_id),request_id uuid NOT NULL,
        message_id text NOT NULL,state text NOT NULL,saved_to_sent boolean NOT NULL DEFAULT false,
        created_at timestamptz NOT NULL DEFAULT NOW(),PRIMARY KEY(user_id,request_id));
        CREATE TABLE IF NOT EXISTS dune_internal_mail_audit (
        id bigserial PRIMARY KEY,user_id uuid NOT NULL,action text NOT NULL,created_at timestamptz NOT NULL DEFAULT NOW());
        CREATE TABLE IF NOT EXISTS dune_internal_mail_tags (
        user_id uuid NOT NULL REFERENCES dune_internal_mail_accounts(user_id),folder text NOT NULL,
        uid_validity text NOT NULL,uid text NOT NULL,tags jsonb NOT NULL DEFAULT '[]',modified_at timestamptz NOT NULL DEFAULT NOW(),
        PRIMARY KEY(user_id,folder,uid_validity,uid));''')
    sql("ALTER TABLE dune_internal_mail_accounts ADD COLUMN IF NOT EXISTS mailbox_kind text NOT NULL DEFAULT 'personal'; ALTER TABLE dune_internal_mail_accounts ADD COLUMN IF NOT EXISTS resource_id uuid;")


def handle(request):
    if not isinstance(request, dict) or request.get('action') not in ACTIONS:
        raise MailError('Unsupported internal mail action')
    user = canonical(request.get('userId'))
    # Lock per mailbox: credentials, sends and idempotent ensure never race.
    lockpath = ROOT / (user + '.lock')
    fd = os.open(lockpath, os.O_RDWR | os.O_CREAT, 0o600)
    with os.fdopen(fd, 'w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        action = request['action']
        if action == 'internal_status':
            return status(user)
        if action == 'internal_ensure':
            return ensure(user, request)
        if action == 'internal_retire_clan':
            return retire_clan(user, request)
        row = active_entry(user)
        if action == 'internal_forwarding':
            return forwarding(user, row, request)
        if action == 'internal_create_folder':
            return create_folder(row, request)
        if action in ('internal_rename_folder', 'internal_delete_folder'):
            return edit_folder(row, request)
        if action == 'internal_move':
            return move(row, request)
        if action == 'internal_reveal_password':
            step_up(request)
            audit(user, 'password-revealed')
            return dict(ok=True, address=row['address'], password=credentials(row)[1], credentialRevision=row['revision'])
        if action == 'internal_set_password':
            return rotate(user, row, request)
        return {'internal_list': message_list, 'internal_get': get_message,
                'internal_attachment': attachment, 'internal_send': send, 'internal_tags': tags,
                'internal_mark_read': mark_read, 'internal_delete': delete}[action](row, request)


def main():
    if sys.argv[1:] == ['--initialize']:
        initialize()
        print(json.dumps(dict(ok=True, initialized=True, provisioningEnabled=policy().get('provisioningEnabled'))))
        return
    raw = sys.stdin.buffer.readline(REQUEST_LIMIT + 1)
    if len(raw) > REQUEST_LIMIT:
        raise MailError('Request exceeds size limit', 413)
    try:
        request = json.loads(raw)
    except (ValueError, UnicodeError):
        raise MailError('Invalid JSON request')
    print(json.dumps(handle(request)))


if __name__ == '__main__':
    try:
        main()
    except MailError as error:
        print(json.dumps(dict(error=str(error), code=error.code)))
        sys.exit(1)
    except Exception:
        # Never expose provider replies, credentials, input bodies or traceback.
        print(json.dumps(dict(error='Internal mailbox service temporarily unavailable', code=503)))
        sys.exit(1)
