# Dev workflow validation — ticket344

## Confirmed defect and candidate

The production policy rejects160 accented title characters although the advertised maximum is160 characters: it counts320 UTF8 bytes. The new regression fails against the unchanged live policy at assertion32 and passes against the candidate. Finding is recorded as Bug354 (pending Owner review); this correction is within approved audit344, not separate execution of354.

The candidate validates UTF8 character length, malformed encodings and non-string values, rejects non-positive/noncanonical action IDs, and rejects explicit foreign/null Origin headers. Missing Origin remains supported for native clients, still requiring the session CSRF token. No role or approval gate is widened.

## Coverage and actual results

| Form/action | Isolated checks | Served verification |
|---|---|---|
| New request | Required/blank; every field type; each text-field boundary with Unicode; select enums; privacy acknowledgement | Rejected-only HTTP script prepared, not yet run against candidate |
| Execution brief | Shared UTF8 limits and positive ID guard | Pending rollout verification |
| Priority | Positive IDs and existing allowlist | Pending rollout verification |
| Approve/deny | Owner identity, state transitions, minimum/maximum note length | Pure fixtures only; no real approvals/denials for testing |
| Complete | Approved-only and role/state rejection | Pure fixtures only; no real production closure for testing |

104 PHP policy assertions pass. Three focused Node Dev-role tests pass. PHP syntax check passes. Source fixtures include unauthorized roles, reviewer-name spoofing, revoked reviewer rank, repeated invalid transitions, Unicode limits, malformed UTF8, array input and invalid IDs. No successful end-to-end creation replay/concurrency test is claimed yet.

The page is server-rendered HTML; it does not parse JSON from form responses. Attachments are intentionally handled through the resulting native private ticket, not this Dev intake form. Network retry and duplicate-submission lock behaviour still require isolated route-level verification. Desktop/mobile and keyboard checks must be repeated on the candidate before closing344.

## Release state

Candidate only, NOT deployed. Do not close344 until remaining route/replay checks, authenticated rejection probes, browser checks and backed-up merge/rollout verification succeed. Never use real tickets as disposable approval or completion fixtures. No credentials, message bodies or paid receipts are modified by the policy tests.
