# Read-only in-game ticket summaries

`control-panel/game-tickets.mjs` exports `createGameTickets(db, settings)`. The parent server must instantiate it and dispatch the exact `/api/game/tickets` path before browser-session routes. No new schema is needed.

## Protocol

Send a server-side HTTPS POST to `https://cp.dunerealms.com/api/game/tickets` with `Authorization: Bearer <GAME_LINK_SECRET>` and JSON:

```json
{"uuid":"the authenticated Player UUID","view":"own"}
```

`view` may be `own` or `staff` and defaults to `own`. The trusted game client must take UUID directly from the current authenticated Player, never from a player-supplied target. The existing shared secret remains in the server-only DuneEss `website-bridge.yml`; it must never appear in resource packs, player messages, logs or item metadata. Browser cookies and claimed account IDs do not authenticate this API.

Successful responses contain `linked`, `view`, and at most 30 recent native ticket summaries:

```json
{"linked":true,"view":"own","tickets":[{"id":"11","number":"713113","subject":"Support request","status":"open","statusLabel":"Open","department":"Support","appealStatus":null,"url":"https://support.dunerealms.com/tickets.php?id=11"}]}
```

An unlinked UUID receives HTTP 200 with `linked:false`, an empty tickets array and linking guidance. Invalid capability returns 401; missing current permissions returns 403; unavailable storage returns generic 503. All responses prohibit caching. Native email addresses, UUIDs, messages, attachments and credentials are absent from responses.

## Access boundaries

- Own tickets require `web.tickets.view` and exact native ownership through the immutable central account alias, derived from the current verified `site_game_links` row.
- Staff tickets require `web.tickets.staff`, current explicit central department nodes and a matching active native agent. Current native department access, direct assignment or team assignment must also permit visibility. Native assigned-only agents cannot enumerate department queues.
- Legal, Sales and Network Staff require their explicit nodes even when stale native membership or assignment exists. Rank labels and `web.tickets.admin` alone do not grant these departments.
- Deleted/archived ticket states and unsupported/custom departments are excluded. Native referral-only visibility is conservatively excluded. Summaries do not grant edit or reply authority; links return to native osTicket, where current session and ticket ACL checks run again.
- The game GUI must additionally recheck its own game staff permission before requesting or displaying staff summaries, clear old views and expire sensitive GUI sessions. It must not accept arbitrary URLs from remote data; canonical links can be reconstructed from the bounded decimal ID and current view.

## Verification

Six focused tests cover capability/method/body validation, UUID-to-account ownership, forged account fields, unlinked accounts, own permissions, staff denial, stale Legal membership, active-agent checks, assigned-only access, assignment/team scope, result limits, bounded plain text and generic failures.

Full website suite at handoff: **166/166 passing**. No live deployment or account/ticket mutation was performed by this source task. Before publishing, verify the installed native tables include `drsupport_ticket__cdata` and `drsupport_staff.assigned_only`, then run a read-only authenticated integration check with a legitimate linked test account.
