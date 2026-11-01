# Account-bound discovery reward

This integration awards exactly **1,000 Dunemarks to the first finder**, after the winner redeems the opaque code on the website. It creates no discovery or payout during installation. The location and gesture are deliberately absent from this document and public API responses.

## MySQL cutover

The current rollout targets the web-hosted MySQL 8 `dunegameplay` database. Use `quest-rewards-mysql.sql` for that engine; the PostgreSQL migration below is retained only for the old-engine compatibility and rollback path. Do not run PostgreSQL DDL against MySQL.

The MySQL migration adds `dune_quest_first_finder` and three fixed `SQL SECURITY DEFINER` procedures, created under `CURRENT_USER` (the dedicated gameplay schema writer). It never creates a winner, changes an existing winner or credits a wallet during installation. The register/claim procedures own their `START TRANSACTION`/`COMMIT` and roll back on SQL errors. The global primary key selects one finder; claim holds its row lock while performing the fixed 1,000-Dunemark wallet increment and marking redeemed. A failed credit does not consume the code. Run each CALL on an otherwise unused autocommit connection, outside any larger caller transaction.

The CP `MysqlGamePool` adapter maps the existing parameterized PostgreSQL-style calls to `CALL dune_quest_register(?,?,?,?)`, `CALL dune_quest_claim(?,?,?)` and `CALL dune_quest_status(?,?)`. It returns the first result set as `rows` and converts status `available`, `discovered` and `redeemed` numeric 0/1 to JavaScript booleans. It must never interpolate values into SQL. The backend module and frontend form need no engine-specific rewrite.

The private ledger must **not** be covered by the CP reader's SELECT grants. MySQL privileges are additive: revoking a table privilege cannot override `SELECT ON dunegameplay.*`. The MySQL administrator must remove an existing database-wide SELECT grant, restore the previous reader scope through explicit table grants that exclude `dune_quest_first_finder`, and grant only these three routine capabilities (use the actual existing account host from `CURRENT_USER()`):

```sql
GRANT EXECUTE ON PROCEDURE dunegameplay.dune_quest_register TO 'dunegamereader'@'127.0.0.1';
GRANT EXECUTE ON PROCEDURE dunegameplay.dune_quest_claim TO 'dunegamereader'@'127.0.0.1';
GRANT EXECUTE ON PROCEDURE dunegameplay.dune_quest_status TO 'dunegamereader'@'127.0.0.1';
```

Do not grant the reader wallet INSERT/UPDATE/DELETE, CREATE ROUTINE, broad EXECUTE or ledger SELECT. Check role/global grants too. Rerunning DROP/CREATE PROCEDURE removes routine grants, so reapply the three narrow EXECUTE grants after an upgrade. Keep the ledger and wallet data; never reinstall by dropping or clearing them.

`quest-rewards-mysql-verify.py` performs real-engine checks in a newly generated, disposable `dune_quest_qa_<random>` database. It accepts a protected administrator JSON file with `host`, `port`, `user`, `password` or `unix_socket`. It requires PyMySQL and CREATE/DROP DATABASE privileges. It installs the actual routines, tests competing first discoveries, identity/UUID binding, code recovery and revocation, a forced wallet failure with rollback, concurrent exact-once redemption and private status, then drops the QA database in `finally`. It does not change production discoveries or wallets. Optional `MYSQL_QA_READER_CONFIG` points to protected actual CP reader credentials and tests narrow procedure execution plus denied ledger SELECT; temporary QA routine grants vanish when the QA database is dropped. Because the procedures commit internally, wrapping production CALLs in an outer ROLLBACK is **not** a valid harmless test.

Actual MySQL procedure verification remains a parent deployment step. Preserve any old-engine discovery ledger during import even if no discovery is expected; never reset a real first finder during the engine change.

## Installation

1. On the PostgreSQL `dunerealms` database, inspect and run `website/control-panel/quest-rewards.sql` as the administrator with `psql -v ON_ERROR_STOP=1`. The existing `public.dune_economy_balances` and `dune_cp` role must exist. This migration adds one private ledger and three narrow `SECURITY DEFINER` functions with `search_path=pg_catalog`. The owner is a dedicated `NOLOGIN` role; `PUBLIC` execution is revoked. `dune_cp` receives function execution, not wallet INSERT/UPDATE/DELETE or ledger SELECT.
2. Before a real discovery, run `quest-rewards-verify.sql` with `psql -v ON_ERROR_STOP=1`. Its synthetic account, UUID, ledger and wallet changes occur inside one transaction and are rolled back. It refuses to clear an existing winner. For later testing, use an isolated database. The JavaScript tests use a serialized contract model; only this PostgreSQL integration check exercises actual SQL.
3. Source `server.mjs` now imports and constructs `createQuestRewards(db,settings,pool)` using the existing MySQL adapter, settings and PostgreSQL inventory pool, and dispatches the three exact routes before generic fallbacks. No MySQL schema migration is needed. For the drifted live release, stage the new backend module and public files, back up `server.mjs` and `public/index.html`, and use the surgical `output/quest-frontend-patch.py <website-root>` rather than replacing either complete live file. The patch adds only the import/factory/three routes, two public asset allowlist entries and two head tags. It is idempotent and rejects ambiguous/missing anchors. Lint the resulting server before restarting CP.
4. Source `public/quest-rewards.mjs` and `.css` now mount an emerald/gold code card inside the existing game-account page. The field accepts `DRQ-` plus 40 hexadecimal characters (case-insensitive). It uses only same-origin private requests, disables duplicate submission, renders messages with `textContent`, clears codes after success/session/link changes, rejects redirects, and never persists codes or places them in URLs. Session generations and request sequence guards discard stale account/link/read responses; the existing visible link-status update triggers a fresh permission/link check. The current source index loads both files. After deployment, verify the linked, unlinked, pending, error and redeemed states in the actual browser; source tests do not replace visual acceptance.
5. Deploy the menu adapter only when it generates proofs after the actual one-shot player-owned hidden interaction. A general command must not manufacture discoveries. Keep the existing `GAME_LINK_SECRET` private in server configuration; no secret belongs in resource packs, client forms or public Typewriter JSON.

The migration is rerunnable. Back up the CP module/server and database before rollout. To pause a defective rollout, disable route dispatch and the game interaction; do not remove an already-credited wallet amount or erase its ledger. The ledger is the duplicate-payment guard.

## Game discovery contract

`POST /api/game/quest/discover` requires the existing private game bearer capability and JSON:

```json
{"uuid":"actual-lowercase-player-uuid","quest":"imperial-portal-eye-ra-v1","nonce":"64-lowercase-hex-random-bytes","expires":1800000030000,"proof":"64-lowercase-hex-hmac"}
```

The game owns the exact player UUID, rendered-view nonce and callback. Generate 32 secure random bytes for the nonce; expire the original GUI interaction after 30 seconds and consume it once. `expires` is integer Unix milliseconds, later than the server's present time and at most 45 seconds ahead. Hosts must have synchronized clocks. Proof is lowercase hex HMAC-SHA256 using `GAME_LINK_SECRET` over UTF-8:

```text
quest-discovery-v1\n<quest>\n<uuid>\n<nonce>\n<expires>
```

Here `\n` means a newline, and there is **no trailing newline**. The API rejects malformed, stale, forged, different-quest or unlinked discoveries. It resolves UUID to the immutable website account through the actual current `site_game_links`; body account IDs and amounts are ignored. Current `web.menu.game` access is required.

Success returns `{ok:true,code,amount:1000,redeemUrl,message}`; `code` is `DRQ-` plus 40 uppercase hex digits and `redeemUrl` is CP `/#game`. Deliver it only to the finding player with copyable text. The response is private/no-store. Another finder gets 409 without the winner's identity. A repeated identical proof returns the same code. The same winner may rediscover after losing a response, which revokes the old code and issues a new one. Redeemed winners receive `redeemed:true` without a code. No discovery request writes a wallet balance.

## Website contract

- `GET /api/quest/rewards`: real session and currently verified Minecraft link required. Returns only this account's `linked`, `amount`, `available`, `discovered`, `redeemed` flags. No winner, code, proof, UUID or location is exposed.
- `POST /api/quest/rewards/claim`: real session, exact CP Origin and verified link required; JSON `{code}`. The code hash, immutable session account and current link UUID are sent to the fixed PostgreSQL function. Body UUID, account and amount overrides are ignored. Success distinguishes `credited:true` from an idempotent `credited:false`; both report 1,000 as the reward size.

The private ledger's single fixed quest key arbitrates simultaneous first discoveries. Redemption locks that row, checks account/UUID/code, credits the authoritative SQL wallet and marks redeemed in the **same PostgreSQL transaction**. Concurrent or retried claims cannot duplicate the payment. Moving the Minecraft link to another website account does not transfer the winner's code. A changed link cannot redeem until the winning UUID is linked back to the winning account.

The verified link is read from MySQL immediately before each PostgreSQL call. These are separate databases, so there is no distributed lock with a simultaneous unlink; a request already authorized using the winning link may finish. This cannot change the account/UUID stored in the private ledger or create a second reward.

Requests are capped at 4 KiB and attempts at ten/minute per game UUID or website account. Errors fail closed without a local-wallet fallback or credential details. The in-memory attempt limiter is a modest extra guard, not the source of one-use or first-finder correctness.

## Validation

`node --test website/tests/quest-rewards.test.mjs website/tests/quest-rewards-ui.test.mjs` passes 21 checks covering capability/HMAC/freshness, malformed and oversized input, immutable identity binding, global first-finder response, proof retry, lost-code reissue, current links, CSRF, concurrent claim/retry contracts, private status, bounded attempts and fail-closed outages. Frontend tests cover logout/account/relink races, latest-read ordering, duplicate submission, exact private request payload, denied permissions, late bootstrap, safe text rendering and source route/asset/accessibility integration. Static SQL checks verify the fixed payout, row locks, hardened search path and absence of broad CP wallet grants. Actual PostgreSQL verification and game/client interaction are separate deployment checks and were not run by this source-only change.
