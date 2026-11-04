# Support workspace — ticket 364

Presentation-only client and native staff workspace, using the existing imperial art and Dev desk palette. Route-specific artwork/heading, framed navigation, readable ticket queues and thread panels, keyboard focus and responsive native detail fields. Client intake, tracking and staff/administration share the stylesheet. Dev desk excluded.

Native forms, hidden fields, scripts, messages, attachments and permission checks are preserved. Staff PJAX restores only the presentation hero; it does not submit or modify a ticket. Wide staff queue tables remain horizontally navigable within their panel on small screens rather than truncating columns.

Validation: 66 PHP fixture checks cover client/staff routes, exact form/CSRF/upload preservation, original classes, repeat no-op, JSON/PJAX exclusion and artwork selection. Native browser desktop and mobile checks are recorded in the private execution checkpoint. These checks do not claim exhaustive testing of every osTicket plugin or administrative dialog.

Deployment: shared-header.php calls the support-only helper; three files installed in the native osTicket root. No service restart or database changes needed. Backup `/root/ticket364-before-20261104`. Rollback: restore shared-header.php from that backup; new files then become unused.
