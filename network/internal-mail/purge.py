#!/usr/bin/python3
"""30-day purge ONLY for validated managed internal mailboxes. No broad user glob."""
import importlib.util
import json
import pathlib
import subprocess
import sys

spec = importlib.util.spec_from_file_location('internal_mail', '/usr/local/sbin/dune-mail-internal')
if spec is None:
    from importlib.machinery import SourceFileLoader
    spec = importlib.util.spec_from_loader('internal_mail', SourceFileLoader('internal_mail', '/usr/local/sbin/dune-mail-internal'))
broker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(broker)
rows = json.loads(broker.sql("SELECT COALESCE(json_agg(json_build_object('userId',user_id,'address',address,'mailbox_kind',mailbox_kind,'resource_id',resource_id)),'[]'::json) FROM dune_internal_mail_accounts;") or '[]')
failed = 0
for row in rows:
    if not broker.valid_managed_address(row['userId'], row):
        failed += 1
        continue
    result = subprocess.run(['doveadm', 'expunge', '-u', row['address'], 'mailbox', '*',
                             '(', 'BEFORE', '30d', 'OR', 'SAVEDBEFORE', '30d', ')'], capture_output=True, timeout=120)
    if result.returncode:
        failed += 1
broker.sql("DELETE FROM dune_internal_mail_outbox WHERE created_at<=NOW()-interval '30 days';"
           "DELETE FROM dune_internal_mail_audit WHERE created_at<=NOW()-interval '30 days';"
           "DELETE FROM dune_internal_mail_tags WHERE modified_at<=NOW()-interval '30 days';")
print(json.dumps({'managedMailboxes':len(rows),'failed':failed,'retentionDays':30}))
sys.exit(1 if failed else 0)
