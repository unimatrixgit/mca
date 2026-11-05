# Kit publishing and reversible archive — ticket 311

One Save & Publish request validates the full design, destination, minimum price and optimistic design/listing versions, then commits the blueprint, matching listing and audit together. A failed listing or audit write rolls back the design. Legacy publish requests remain supported. Draft saving remains secondary for unavailable categories and token prerequisites.

Archive deactivates the design and every listing in one transaction. Restore reuses the same unique owner/kind/quality slot as a draft, increments its revision, and leaves every listing unpublished. Restoring does not re-enable old purchases. Both require the current owner revision; server designs retain protected controls. Clan publication rechecks current active clan officer/owner membership and verified UUID inside the transaction. Native office and upgrade delivery gates remain enforced.

No paid snapshot, receipt, token or delivered item is rewritten. The existing deployed native checkout procedure checks blueprint/listing activity, publication and matching revisions with FOR SHARE; archive/edit lock the same blueprint with FOR UPDATE. New requests with stale revisions fail; a caller can refresh after a lost response without duplicate listings. Publishing a new revision to one destination makes other older-revision destinations unavailable, and the UI explains republishing when there are multiple active listings.

Scope: Imperial Tailor player/official/faction and protected server studio, including personal designs listed in clan shops. House Tailor five-quality storage remains ticket312. No new quality rules or game delivery deployment.

## Validation

- 33 focused Node tests: studio schema/access/UUID/revision/rollout gates; cart identity and retries; catalogue; upgrade policy.
- 40 actual MySQL checks: connection-local temporary copies of current tables, first publish/edit, stale replay, invalid price, denied clan/other account, injected audit failure rollback, archive/restore/republication, upgrade gate. Read-only checks of deployed native checkout locking/filter contract. No real purchase or receipt mutation.
- Browser isolated fixture: one form; publish once; archive confirmation; restore stays draft; mobile390 has no horizontal overflow.
- Live browser/read-only route verification recorded in completion evidence.

## Rollout / rollback

Only control-panel/kit-studio.mjs, public/kit-market.mjs and public/kit-generator.mjs are deployed. Baseline matched current production before backup. No schema change. Restart only dunerealms-account-panel. Restore those three backup files and restart that service to roll back. Archived rows remain intact if source is rolled back; restore capability requires the new UI/API.

No game service, payment provider, permissions, existing receipt or source reference material changed.
