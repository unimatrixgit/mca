# Native ban appeal workflow

The native osTicket ticket is authoritative for whether an undecided appeal is open. Closing it removes it from Pending without inventing an approval or denial. Reopening an undecided ticket returns it to Pending. Approved and Denied remain final until an authorised agent explicitly uses Appeal / reopen.

Staff appeal tickets show Approve, Deny and Appeal / reopen controls. Each action requires current central appeal-management permissions, native ticket access and native close permission. Submissions use native CSRF protection and require a recorded reason. Decisions and ticket status changes remain in their respective audit histories.

Approve claims the appeal, asks the authenticated game control provider to remove the linked local UUID ban, and records approval only after the provider confirms that the ban is absent. A stale creation timestamp, unsupported scope, unavailable source, invalid response or timeout leaves the appeal pending. Retrying a confirmed decision does not issue another removal. Deny closes the appeal without changing its ban. Reopening never reapplies a removed ban.

The local game provider supports Arrakduin and lobby vanilla bans. It sends only a fixed UUID pardon command, checks the original ban timestamp before sending, and checks the source after sending. It does not edit a running server's ban files. An independently issued in-game re-ban cannot be made atomic with a vanilla console command; observed timestamp changes fail closed and require staff review. Unsupported global providers cannot record successful removal.

## Acceptance evidence — 29 October 2026

- 147 website tests pass, including native close/reopen projection, ownership, final-decision preservation, approval failure, matching positive confirmation, replay, denial and demonstration isolation.
- 15 Python provider tests pass, including malformed sources, stale records, command injection, unsupported scope, unconfirmed removal and newer bans during confirmation.
- Live osTicket test #713113 completed Deny → Reopen → Approve → Reopen. Native status, department/team, 72-hour SLA and matching CP flags were verified. It remains pending for operator review.
- Previous appeal #229211 was closed at the operator's request, without a decision or ban removal.
- The authenticated CP-to-game tunnel confirmed an absent synthetic UUID; this read-only check issued no pardon command. No genuine player's ban was changed during testing. Actual removal is covered by the fixed-command provider tests, rather than falsely treating the demo as a real ban.
- CP lists provide Pending, Approved, Denied and Closed filters, coloured status flags and refresh on return to the page. A filtered view shows only matching appeal tickets.
