# Casework regression — native task 324 / public 803432

## Confirmed defects and scoped changes

Concurrent appeal decisions could lose their status compare-and-set, commit dependent ban/audit updates, and only then return a conflict. Reopen could acknowledge a pending state which another reviewer had already claimed for approval. Check the status mutation inside the same transaction; a lost comparison rolls back all dependent writes and returns 409. An already-pending reopen is idempotent. A positive matching game acknowledgement is still required before approving a real ban; deny/reopen never call the game host. A failed local audit after an external acknowledgement rolls back local finalisation and leaves the retryable `approving` claim; it does not reapply a game ban.

Personal, staff and clan bank projections accepted numeric or array values through regular-expression coercion. Require decimal strings for balances, wallet amounts and ledger amounts; retain exact large decimal strings without floating-point conversion.

A bank response arriving after logout, permission removal, relinking or clan membership removal could expose the prior authority's data. Recheck the current session/account, view grant and linked identity after the upstream wait. Clan responses additionally require the same active clan membership, and derive role/design/price flags from current authority. A valid role downgrade retains the member's own read access but removes design authority. No new banking writes or transfers are added.

Findings are recorded as native Bugs 382, 383 and 384 in their normal protected review workflow. These fixes are within approved parent 324; the Bug records have not been approved or closed by bypass.

## Validation on 6 November 2026

- 71 focused and adjacent Node tests pass on local Node 24 and server Node 22.23.3: banking, current-authority races, all five House Tailor designs, appeal decisions/status, game summaries/context, unban acknowledgements, public appeal projection and private evidence.
- 44 actual MySQL case-context checks pass using the installed native table definitions with synthetic rows in a separate, socket-only disposable MySQL process. Tests install the source SQL procedure/trigger only in that process. They cover Take, suspect validation, version invalidation, current department/native/team/game authority, audit rollback and a second connection whose rank removal is blocked by the authority transaction's row locks and denies the subsequent request.
- 44 actual MySQL appeal checks pass in the same isolated process: second-connection decision races, preserved winning ban timestamp, audit rollback, retry, exact injected game acknowledgement, outage handling, deny and idempotent reopen. Actual game requests: zero.
- The temporary server was shut down. Production schemas, grants, accounts, tickets, evidence, balances and game services were not changed by this QA.

The two `*-mysql.integration.mjs` harnesses are explicitly opt-in. They require `DUNE_CASE_TEST_SOCKET=/tmp/dune-case324-qa-<unique>/mysql.sock`, a private `.codex-qa324` marker containing `isolated-casework-324`, a matching `@@datadir` and `@@skip_networking=1`. The context harness also requires `DUNE_CASE_TEST_SCHEMA`, a JSON mapping of table names to their native CREATE TABLE statements. Supply schema definitions only, never production rows or credentials. Run the context harness first, then the appeal harness against that same disposable instance. No production GRANT statement is installed. Never point these harnesses at the application database; they create and clear synthetic tables in the isolated schemas.

## Deployment boundary and remaining work

This change is staged for review, not deployed. Only three existing CP backend modules change. No migration, route activation, new public helper, website build or game restart is needed for these fixes. Preserve the deployed House Tailor five-quality/legacy compatibility behaviour. Guard all three installed source baselines, back up the files, install the reviewed revision and restart only the account panel. Verify anonymous denials, private no-store responses, exact installed hashes and service health. Roll back the three code files if needed; no data rollback.

The game case-context procedure, trigger and route are absent from production. Passing isolated tests is not a claim that this feature is deployed; do not apply its migration or grant as part of this regression fix. Parent 324 remains open for the native osTicket bulk-status/assignment handler regression, visible acceptance, reviewed merge and scoped rollout. Browser native input is currently blocked by an earlier local-preview confirmation; no browser acceptance or production moderation action is claimed.
