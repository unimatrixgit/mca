#!/usr/bin/python3
"""Place native permission-checked appeal controls beneath each ticket status.
Only changes presentation hooks, not osTicket's submit, CSRF or permission code.
"""
import sys
from pathlib import Path
root=Path(sys.argv[1]).resolve()
path=root/'include/staff/ticket-view.inc.php'
source=path.read_text()
if 'dune-status-appeal-row' not in source:
    source=source.replace("<?php require_once dirname(__DIR__,2).'/appeal-actions.php'; dune_appeal_controls($ticket,$thisstaff); ?>", "<?php require_once dirname(__DIR__,2).'/appeal-actions.php'; ?>")
    source=source.replace('<?php dune_appeal_controls($ticket,$thisstaff); ?>\n','')
    for name in ['reply_status_id','note_status_id']:
        start=source.index('<select name="'+name+'">')
        end=source.index('</tr>',source.index('</select>',start))+5
        source=source[:end]+'\n<tr class="dune-status-appeal-row"><td colspan="2"><?php dune_appeal_controls($ticket,$thisstaff); ?></td></tr>'+source[end:]
    path.write_text(source)
print('Appeal controls placed below Reply and Internal Note Ticket Status')
