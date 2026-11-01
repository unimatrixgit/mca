# Staff roster contact integration — implementation gate

Source audit, 29 October 2026. No deployment or live data mutation was performed for this audit. Per-person Contact buttons are not implemented yet.

## Existing paths and limits

- `node/staff-roster.mjs` renders current effective roles and public profile details. It exports no private account ID, email or department routing authority.
- `integrations/osticket/dune-sso.php` retains only a staff-login request or appeal UUID through SSO. It has no verified contact-recipient flow. An `open.php?staff=username` link would not assign a ticket.
- `integrations/osticket/sync-agents.php` requires `web.tickets.staff` and at least one permitted department. A News/Streamer-only user is deliberately ineligible as an agent under the current policy.
- `integrations/osticket/department-access.php` projects permitted departments into native membership. This does not provide an assigned-only recipient role.

Adding News/Streamer users to Support merely to receive contact requests would expose unrelated Support tickets. A client-controlled staff ID or hidden form field cannot serve as assignment authority.

## Required implementation

1. Add an explicit contact-recipient eligibility policy based on current central roles, separate from full staff queue access. Provision a native assigned-only role and a dedicated private contact department. Verify the installed osTicket version's assigned-only access rules against list, search, direct ticket, attachment, task, collaborator and export routes before enabling this role.
2. Implement a native `contact.php?recipient=<public username>` page. Resolve the username server-side against a fresh verified central roster response; map immutable central identity to its native agent. Reject missing, demoted, disabled or otherwise ineligible targets. Preserve this validated target through the existing SSO session; never accept an arbitrary native staff/department ID from the client.
3. GET displays a themed request form only. POST requires native CSRF protection, verified requester identity, the existing general-support linked-Minecraft policy, rate limiting and an idempotency token. Revalidate the target at submission and assign server-side in the same ticket-creation operation. The request is private to the requester, assigned recipient and explicitly authorised supervisors.
4. Record a central/native association and audit event. A changed or revoked recipient must not silently transfer the request to a broad department. Define supervisor reassignment for orphaned requests and keep an audit trail.
5. Only after these backend checks pass, add decorated Contact buttons to the roster cards linking to this native page. Keep the existing general Support Center link as fallback.

## Acceptance matrix

| Case | Required result |
| --- | --- |
| Owner / Network / Support recipient | Request assigned to the validated recipient, ordinary existing departmental rights retained |
| News-only / Streamer-only recipient | Can read and reply to own assigned contact requests; cannot enumerate unrelated Support, Legal, Sales or other recipients' requests |
| Anonymous visitor | SSO before submission; GET creates no ticket |
| Unlinked requester | Existing general-support linking rule enforced |
| Forged department, agent ID or other recipient in POST | Ignored or rejected; server controls assignment |
| Recipient demoted after GET | POST rejected with a safe fallback, no ticket to a stale agent |
| Replayed POST / retry | One ticket and one association |
| Other requester / recipient | Direct URL, search, attachments and exports denied |
| Native/central broker unavailable | Fail closed without leaking credentials or private routing data |

`tests/staff-contact-policy.test.mjs` checks the current prerequisite boundary: public News/Streamer cards do not grant staff queue or department permissions, and rendering does not mutate rank assignments. These tests do not claim that the future contact workflow exists.
