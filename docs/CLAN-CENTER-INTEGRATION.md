# Clan Center implementation and integration

Source implementation, 29 October 2026. No live clan data, server route or game state was changed by this task.

## Implemented rules

- One active website clan membership per account and per verified Minecraft UUID. The membership retains the verified UUID used at joining, so unlinking/relinking cannot create a second membership for that UUID.
- Names are 3–40 letters/numbers/spaces/apostrophes/hyphens; tags are 2–6 letters/numbers, normalised uppercase. Names/tags are reserved uniquely, including archived history. Descriptions are plain text, up to 1,000 characters.
- Verified game linkage is required to create, apply, accept members or manage the clan. An unlinked member can still leave or withdraw a request. A clan owner must transfer ownership or archive before leaving.
- Clan owners manage details, officers, removals, ownership transfer and archiving. Officers can review applications. Clan members gain no website staff rank, game operator permission, currency, land or in-game prefix.
- Join requests require an owner/officer decision. Pending applicants are visible only to those reviewers. Acceptance withdraws the applicant's other pending requests. Membership uniqueness is enforced in the transaction.
- Every change is audited. Version checks and a transaction-time role guard reject stale actions, including a reviewer demoted between reading and writing. Ownership and role changes commit together. Archiving retains the clan/audit history and releases memberships.
- Three clan creations per actor in 24 hours; ten pending applications maximum. No open-join option, invitations, treasury, game synchronization or alliance powers were invented.

## Files

- `website/control-panel/clans.mjs`: `initializeClans(db)` creates three InnoDB tables; `createClans(db,{origin})` exposes the authenticated API.
- `website/control-panel/public/clans.mjs`, `clan-request-gate.mjs` and `clans.css`: directory cards, search, owner/officer/member controls, applications, details and safe confirmations. Rendering uses text nodes, never user HTML. A conflict refreshes details without automatically repeating the action. Session generations and per-request lanes reject stale directory/detail/mutation responses; logout or account changes clear private DOM immediately. A late bootstrap sign-in response cannot restore an earlier session.
- `website/integrations/clans/index.html`, `clan-shell.css`, `clan-shell.js`: shared branded standalone page embedding the existing CP session at `https://cp.dunerealms.com/?embedded=1#clans`.
- `website/scripts/build-clan-center.mjs`: rebuilds only this shell using the canonical shared header; does not run the broad page generators.
- `website/tests/clans.test.mjs`: ten focused tests passing against real SQLite transactions, including stale-version rollback and a permission change just before committing.

## Parent CP integration

These edits were deliberately left to the parent to avoid colliding with ongoing server work:

1. Import `createClans, initializeClans` from `./clans.mjs` in `control-panel/server.mjs`.
2. In `createPanel`, construct `const clans=createClans(db,{origin});`. Dispatch exact `/api/clans` or `/api/clans/…` to it before the generic API/static fallback. No CORS or trusted identity-header shortcut is needed.
3. Run `await initializeClans(db)` after the CP database is opened and before the new routes accept writes. Default initialization explicitly uses InnoDB/utf8mb4. The tests pass `{mysql:false}` solely for SQLite syntax.
4. Allow only `clans.mjs`, `clan-request-gate.mjs` and `clans.css` in the CP public-file allowlist. Include `<link rel="stylesheet" href="/clans.css">` and `<script type="module" src="/clans.mjs"></script>` in the CP index head.
5. Add `clans:'web.menu.game'` to the `pages` permission map in `public/pages.mjs`. The module supplies its own section/navigation link. Existing basic game access is reused; clan roles never alter global ranks.
6. Add only `https://clans.dunerealms.com` to the CP response CSP `frame-ancestors` allowlist. Keep all existing restrictions. Check that an upstream X-Frame-Options header does not block this trusted embedding.

## Clan domain publication

Back up its current static document root first; publish the three shell files there. Keep the standalone CP link as fallback. The iframe reuses the existing host-only CP cookie without copying passwords or widening the cookie domain.

Suggested clan-domain CSP: allow self scripts/styles plus the existing `https://www.dunerealms.com` shared scripts/styles/art; connect only to self, www and CP; allow `frame-src https://cp.dunerealms.com`; keep `object-src 'none'`, `base-uri 'none'` and a narrow `frame-ancestors` allowlist. The shell uses no inline scripts and sends no identity data through postMessage.

The iframe sends only a bounded layout height to the exact clan parent when its referrer identifies that parent. The shell checks both the CP origin and exact iframe window before accepting that height. Referrer policy is `origin` to enable this check without forwarding a URL path. Trusted embedding sets html/body height to auto and min-height to zero, measures intrinsic body height and sends changes only; viewport percentage/min-height cannot feed a growing parent-height loop. CP area/side navigation hides only while viewing the clan page in that trusted frame.

## Verification before calling it live

- Run `node --test tests/clans.test.mjs tests/clan-request-gate.test.mjs` and syntax checks: 15 focused tests pass. The five request-gate tests settle deferred responses across logout/account switch, out-of-order refreshes, back navigation and bootstrap sign-in races. Source full-suite run previously observed 165/166 pass; the unrelated current `game-tickets.test.mjs` malformed-request assertion was reported to the parent, not changed here.
- Verify schema creation and guarded SQL on the live MySQL version before testing real memberships. SQLite tests are not a claim of live MySQL verification.
- Check anonymous sign-in, existing CP sign-in, unlinked read-only view, linked create/apply, owner/officer review, role changes, transfer and archival using clearly identified test identities. Confirm cross-clan and stale actions are rejected and do not change website staff ranks.
- Check desktop/mobile cards and tables, trusted iframe rendering, height changes and header/footer consistency. Browser visual acceptance remains pending parent deployment.

Game clan prefixes, chat and in-game controls are separate missing integration work. The page states this accurately rather than claiming a game sync exists.
