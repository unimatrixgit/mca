# DuneRealms API v1 — isolated foundation

This implementation is additive and **disabled by default**. It targets the historical `mca/main` snapshot, not the confirmed live DuneHub release. **Do not deploy it over production without reconciliation.**

Set `API_V1_ENABLED=true` on an isolated control-panel staging instance to enable it.

All endpoints require the existing `__Host-dune_session` cookie and are same-origin GET only:

| Endpoint | Response |
| --- | --- |
| `/api/v1/me` | Own website account |
| `/api/v1/me/minecraft` | Own verified Minecraft link or `linked:false` |
| `/api/v1/me/permissions` | Existing legacy effective nodes (informational only) |
| `/api/v1/servers` | Sanitized existing world monitor |
| `/api/v1/catalogue?limit=20&before=123` | Native exchange listing projection, max 100/page |

Catalogue requires `EXCHANGE_LISTINGS_URL` (HTTPS) and `EXCHANGE_READ_TOKEN` (at least 32 characters). Unconfigured dependencies return 503. Prices and IDs stay as strings to preserve precision. The legacy listing bridge exposes at most 500 entries.

Responses include a server-generated request ID and private no-store headers. Request audit defaults to structured process output and fails closed if the injected audit callback fails. The in-process 120/minute limiter is not sufficient for multi-instance deployment; configure a shared edge limiter before rollout.

Run `node --test tests/api-v1.test.mjs` from `website/` using Node 22+.

**Not included:** bank transfers, balances, quests, kit purchases, native write operations, production database changes or new permissions. These require the current deployed source, explicit authorization and transaction contracts, staging tests and approval.
