# Alpha v0.0.0.1 release tracker

Updated 29 October 2026. This records verified stages; it does not declare the whole Alpha complete.

## Stage 1 — Reproducible website baseline

- Website baseline: 134 tests passed before the additional SmartTemplates reply test. Existing security assertions retained.
- Default website build packages authored pages and worker source without executing legacy page generators. Explicit generation remains separate.
- Source/live homepage parity is being checked before any full website release. Surgical live changes must be reflected in source.
- Website source build and tests are local validation. No new full-site deployment or DuneGit push is implied.

## Stage 2 — Live-compatible reward bridge

- Live Paper servers: 1.21.4. Current broader plugin source targets 1.21.8 and cannot be deployed as a routine replacement.
- Deployed DuneEss 1.0.5 season compatibility patch against the exact live artifact. Only two classes and the version descriptor changed; 994 other archive entries remained identical. Five isolated ledger tests passed.
- Preserve unrelated JAR entries and existing game data. Do not repeat the already delivered Unimatrix reward.
- Arrakduin restarted with zero players online; Paper 1.21.4/Java 21 and DuneEss 1.0.5 startup verified. Reward/link commands registered. Both game services remain active.
- Previous season receipt remains acknowledged and wallet remains 5,000; no new credit issued. Website redemption enabled with Arrakduin guidance. A fresh legitimate player claim still needs an in-game acceptance check.
- Rollback backup: `/opt/dunerealms/backups/season-plugin-20261029-061319`. Artifact SHA-256 `c9dae5db3b4aa26842dbe003653aaa76546fa74eaec2fa726185aabefb76bcb6`.

## Stage 3 — Ticket API retirement and mail replies

- Legacy `/api/tickets`, `/api/cp/tickets` and support-profile bridge now return 410 for reads and mutations on main and CP domains. Broad legacy staff forwarding removed. Existing osTicket data retained.
- Main, Account Center and Support Center respond HTTP 200 after restart.
- MyBB administrative projection now requires explicit `web.forum.admin`; rank-management access alone cannot grant forum administration. Native PHP scripts lint clean. Stored permission choices preserved.
- CP Webmail includes a separate branded reply panel and SmartTemplates HTML download. Cursor token appears only in that variant; generic templates remain unchanged. Own-mailbox authentication and escaping tests pass. Live browser verified personalised preview and available download controls; desktop application rendering remains a separate acceptance check.

## Remaining previously authorised work

These require implementation or verified acceptance; their presence in the backlog is not a completion claim.

- Staff roster contact buttons creating properly assigned osTicket requests with restricted access.
- Role-specific in-game staff/user menus and unified tickets, appeals and suspect actions.
- Staff manual split into website and in-game features with tested tutorials.
- Complete clan website, role permissions, game GUI, prefixes and clan chat.
- Complete Alpha cosmetic keepsake delivery.
- Finish parental/privacy request handling and validate retained-data policy; current previews are not a compliance certification.
- Audit wider game integration and resolve version compatibility before any broader plugin rollout.
- Review source, secrets, migration/deployment recipes and test evidence before the final release commit and publication.

## Stage 4 — Durable native synchronization

- Rank, permission and seal changes now commit a durable update event with the central change. Failed updates retry with backoff; empty queues do not load accounts or poll ranks.
- Database lease serializes delivery; revision checks preserve changes arriving during an update. The retry worker runs only in the main process and stops on shutdown.
- Deployed schema and modules together; main/CP healthy afterward. No initial bulk sync or account/rank change performed.
- Live MySQL failure/retry check passed using a connection-local temporary table and injected adapter. Real accounts, native memberships and persistent queue remained untouched.
- Full website suite now **140/140 passing**. Backup `/root/alpha-sync-outbox-1793254708111`.
- osTicket staff sessions now store a fingerprint of verified central identity, effective ranks and permissions. Changed scope rejects stale writes/AJAX and renews GET sessions through SSO; department/team projection completes before the new scope is saved.
- Pure PHP tests cover unchanged/reordered scope, removed staff/department/admin access, changed user/rank, malformed input and missing/forged session fingerprints. Live staff SSO and subsequent reload passed. Backup `/root/alpha-staff-scope-1793254950`.
- Assigned-only personal staff contact remains separate work. Contact policy tests prevent solving this by exposing the Support department to News/Streamer staff.

## Verified recent live stages

- Shared main/CP/forum/support/Git branding, installer page, DuneGit verified accounts and CP Git tools.
- Staff mail provisioning and self-service password setup, tested with a temporary mailbox.
- Canonical branded signatures and message-template downloads with authorised seals; generated MIME validated. Thunderbird rendering still needs the user's fresh-file check.

YouSMP migration and WordPress cleanup remain after DuneRealms Alpha, as instructed.
