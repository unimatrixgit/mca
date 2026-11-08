# Internal mail delivery and inbox handoff — 8 November 2026

Scope: private personal mail, shared clan/staff/faction/Imperial mail, report-to-Legal bridge, sequential ticket identifiers, verified external mailbox settings and hosting email ownership. Live hosts and all private credential/configuration paths remain outside repository data.

## Verified live behavior

- Personal mailbox ownership comes from the authenticated immutable account, never the client-supplied user ID. All active eligible members have their own Inbox.
- Shared clan mail follows current owner/officer membership. Registry creation/removal reconciles provisioning and retirement. House Unimatrix has been provisioned.
- Staff access follows actual current staff responsibilities, including future roles. Faction/Imperial access requires a linked UUID and verified office or explicitly assigned Council mail rank. Protected owner permissions alone do not grant private political inbox access. No additional Council memberships were assigned.
- Five faction mailboxes and the Imperial mailbox are provisioned. UI only shows authorized ones.
- Searchable To/Cc/Bcc uses account names. Staff and political recipient restrictions are enforced server-side. Technical addresses stay hidden in the UI.
- Personal/Clan report mail copies the original eml into Staff. Staff conversion creates one Legal ticket with the reporter as requester, Legal department/team, and original attachment. Real synthetic verification: ticket395 / DR_LG-0000006. Repeating conversion reuses that ticket.
- Ticket IDs use department hints and seven sequential base36 characters. Existing aliases preserve legacy links. Dev-generated subjects no longer include the internal hash prefix.
- New-ticket internal notifications use a durable queue, stable per-ticket/recipient send ID, backoff and authorization rechecks. Initial watermark395 skips historic tickets. Creator receipts and assigned staff/team alerts use the branded HTML/plain-text template. The private CLI feed reads authoritative osTicket data. No blanket shared-staff notification exposes Legal ticket details.
- Direct internal SMTP sending to external recipients and spoofed internal senders are rejected. Verified automatic external forwarding remains allowed and retains an internal copy. Authenticated internal delivery bypasses ordinary spam rules; external/unauthenticated mail remains scanned.
- Managed mailboxes have100MiB quota and30-day retention. Purge every15minutes. Fixed Dovecot query grouping; last verification19managed/0failed, systemd Result=success. Queue empty.
- Inline compose/reply/forward and reading panels stack below the single inbox/folders card. Compact metadata/message/action boxes. Row-level Mark/Move/Delete removed, bulk move retained. Colored collapsible per-mailbox tag filters, mailbox tabs and custom logo/seal SVGs.
- DuneRealms Mail has Webmail/Security/Signature/Templates tabs. Existing password and template listeners are preserved. Hosting Control Emails lists only verified owned external mailboxes; Domains/Servers remain explicit empty integration placeholders.

## Deployment and ownership

Node files under website/node map to live current/node; website/control-panel maps to current/control-panel. Deploy shared server/index/menu edits as scoped patches, preserving concurrent colleague work. Do not replace an old complete server/index file over newer live versions.

network/internal-mail/broker.py maps to /usr/local/sbin/dune-mail-internal, purge.py to dune-internal-mail-purge, recipient-policy.py to dune-internal-recipient-policy. Existing legacy external-mail broker stays behind the dispatcher. Encryption keys and SSH credentials never belong in Git.

Native PHP helpers go to the private osTicket root. internal-mail-feed.php and mail-report-ticket.php reject HTTP execution. ticket-identifiers.php is installed as dune-ticket-identifiers.php and is loaded by existing patched native ticket methods. Native changes also have root-only backups.

## Verification

Run: node --test website/tests/internal-mail*.test.mjs website/tests/automated-mail-templates.test.mjs (18 checks). Local tests cover ownership, scope boundaries, forwarding, recipient restrictions, no owner override for private political mail, Legal conversion dedup, notification baseline/delivery retry/dedup and safe HTML templates. Live SMTP, IMAP, quota, folder lifecycle, report conversion and purge were separately exercised.

## Boundaries / remaining integrations

Political application/ballot event notifications are not wired to tickets by this change; the politics owner maintains those event contracts. Hosting domain/server provider integration is intentionally placeholder-only. No email SSO or automatic desktop-client configuration is claimed. Desktop templates are downloadable with instructions. GET /api/internal-mail/summary is authenticated, no-store, own personal inbox unreadCount only; do not add public CORS or expose shared mailbox counts. Portal postbox owner can consume it on the CP origin.

Support scanner investigation: repeated invalid-CSRF requests were blocked probes, not evidence of a successful compromise. Exact source IP blocked on support vhost; minimal security access log and30-day rotation added. Fail2ban SSH jail was already failed due missing file logs; restored using systemd backend and configuration-tested, active.
