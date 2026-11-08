# Provider, postbox and political notification completion — 8 November 2026

Preserve the elected-office access hook from PR118. This change does not assign roles, cast votes, approve applications, or grant in-game offices.

## Live behavior and validation

- CP Imperial Postbox consumes authenticated own-personal /api/internal-mail/summary. Shows actual unread count, highlights unread mail, refreshes on focus/visibility, each minute while visible, and dune:mail-changed. Logout/account changes invalidate old requests. Public portal does not gain CORS access to the count. Browser verified2unread.
- Political immutable application, ballot, round and office events feed a durable deduplicated notification queue with retry/backoff. Application receipts link the immutable applicant as requester, use existing department numbering and hidden workflow INT, and close automatically as registration receipts. No candidature decision is implied.
- Real current applications APP-HSE-2026-000001 and APP-IMP-2026-000001 linked to DR_SP-0000008(ticket397) and DR_SP-0000009(ticket398). Both closed, both correct requester, both INT. Reconciliation replay creates no duplicates.2application notices sent.
- Ballot receipts go to the voter; candidate notices omit voter identity and allocations, with provisional current top-three aggregate standings. No live ballots were cast for verification. Round/office notices do not claim rank or seal grants.
- Native support dashboard/ticket desk has Show application registration tickets (defaultoff). Normal department permissions remain enforced. Native queue filter and direct staff access check hide application receipts until enabled. Applicant access is unaffected. Real browser test enabled switch, found both closed receipts, disabled it again and confirmed neither remained visible.
- Hosting Control connects each user's own GoDaddy production key/secret, Hetzner Cloud project token and/or Robot webservice credentials. Requires website-password verification and same-origin POST. Secrets encrypted AES256GCM in site_hosting_connections; master key /home/dunerealms/.config/hosting-master.key owner-only0600, created at first successful connection. Secrets are never returned to browser. Disconnect also requires password.
- Domains/DNS and servers are loaded only from authenticated fixed provider endpoints; no guessed ownership. DNS view checks the requested domain is in that connected account. Provider edits/power operations open provider's existing controls; native CP does not implement DNS writes, power operations or purchases.
- No actual provider credentials existed at deployment. Provider forms/status verified in browser; live GoDaddy/Hetzner inventory remains pending the owner's credentials. No fabricated resources.

## Native hooks to preserve on upgrades

Deploy political-application-ticket.php, application-visibility.php and internal-mail-feed.php to private osTicket root; application-visibility-action.php maps to scp/application-visibility.php.

include/class.queue.php getBasicQuery: after mangleQuerySet require root/application-visibility.php and apply dune_filter_application_receipts only for Ticket root.
include/class.ticket.php checkStaffPerm: require same helper; deny if !dune_application_staff_visible($this->getId()), then continue all original staff/department checks.

support-workspace.php adds the checkbox form on staff index/tickets. Use $ost->getCSRFFormInput() directly: this renderer runs inside an output-buffer callback, so starting another output buffer is invalid. scp action requires native staff.inc.php and its CSRF validation.

Native creation first binds ticket_id before closing. Retrying a processing receipt reuses that ticket. Legacy interrupted rows with no ticket_id recover only if exactly one receipt matches requester, public application ID, private topic and creation timestamp; ambiguity requires review. Choose one closed TicketStatus by native numeric ID (there can be multiple closed-state statuses).

## Deployment

Node createHosting mounted under /api/hosting/. Internal mail reconciliation runs runPoliticalNotifications after native ticket mail. Feed excludes application-receipt topic, preventing duplicate generic ticket receipts. Preserve all concurrent election clocks and elected office authority.

Update both web/portal-account.js and css plus those two embedded dist/server/index.js assets only, retaining every other asset/runtime byte. CP files served directly. Root backup /root/provider-political-before-20261108. No providers are contacted without user credentials.

## Tests

39targeted Node checks pass including existing election-authority/office-ledger regressions, mail access, queue dedup, candidate privacy, encrypted credential tamper rejection, fixed API endpoints, own-account provider scope and cross-origin rejection. Native PHP files linted; live closed-ticket ownership/workflow and default/checked visibility verified separately.

Sources: https://developer.godaddy.com/en/docs/references/rest/domains/v1 ; https://docs.hetzner.cloud/ ; https://robot.hetzner.com/doc/webservice/en.html
