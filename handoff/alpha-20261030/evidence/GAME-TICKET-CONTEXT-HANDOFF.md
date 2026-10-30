# Assigned game ticket context — source handoff

Ready in source only; no production API/procedure/trigger installed by this agent. API tests11PASS; actual MySQL8 stored-routine TEMP-fixture checks27PASS, including edit_faction. Production witness counts/digests unchanged; all unique QA routines dropped in finally.

## Surgical route wiring

Import `createGameTicketContext` from `./game-ticket-context.mjs` in the existing CP server, instantiate once with the same auth MySQL `db` and settings as `createGameTickets`, and dispatch exact `/api/game/ticket-context` to it. Do not replace full server.mjs or existing game-tickets.mjs.

POST only; existing private game-link Bearer credential (`GAME_LINK_SECRET`). Native plugin MUST set actorUuid from its actual authenticated online Player; never accept a client-supplied actor identifier. Query identity overrides, unknown properties, duplicate/escaped property aliases, malformed/zero UUIDs and bodies over2048bytes rejected.

- Read: `{actorUuid,ticketId:"42",action:"read"}`.
- Take: `{actorUuid,ticketId:"42",action:"take",version:1}`.
- Set suspect: `{actorUuid,ticketId:"42",action:"set-suspect",version:2,suspectUuid}`.
- Native selector supplies an actual ONLINE UUID. API admits only known verified site_game_identities with positive first_linked_at; never derives UUIDs from player names or offline hashes.

Response: `{ticketId:string,contextVersion:number,assignedAgentUuid:string|null,creatorUuid:string|null,suspectUuid:string|null,canTake:boolean,canSetSuspect:boolean,actions:{teleport,inspectInventory,editInventory,adjustEconomy,editFaction}}`.

## Canonical SQL migration

Explicit migration file: website/integrations/osticket/game-ticket-context.sql. Review/install before enabling the route or native module. It creates only the owned context/events tables, assignment-change invalidation trigger and fixed SQL SECURITY DEFINER procedure. Existing game writer receives EXECUTE only; no raw website-table grants.

Preflight real native and owned context/audit tables are InnoDB; current prefixes/database names are website.drsupport_* and dunegameplay. Definer website@% must already have needed website SELECT/INSERT/UPDATE/TRIGGER and procedure rights; do not broaden unrelated grants. Native case audit table remains in game schema and is owned/initialized by DuneStaff.

`CALL dunegameplay.dune_case_action_lock(actorUUID,ticketId,expectedContextVersion,action)` runs INSIDE caller's existing transaction and never commits. Native actions: `read`, `teleport_creator`, `teleport_suspect`, `inspect_inventory`, `edit_inventory`, `adjust_economy`, `edit_faction`. Non-read requires an explicit expected version.

Result columns: ticket_id,context_version,actor_uuid,assigned_agent_uuid,creator_uuid,suspect_uuid,target_uuid,can_take,can_set_suspect,can_teleport,can_inspect_inventory,can_edit_inventory,can_adjust_economy,can_edit_faction. Additional internal columns agent_id,assigned_staff_id,ticket_state,actor_account_id,last_action,last_actor_uuid,last_from_version support the trusted API; they are not included in the public game DTO.

Errors are SQLSTATE45000 with case_invalid/case_conflict/case_denied; native code must fail closed. Expected actor, target and context version must match its actual online player and fenced target transaction. Native tools independently recheck dune.admin, self/protected target restrictions. Economy ALSO requires existing dune_staff_bank_access_lock; case assignment does not create financial authority.

## Authority and transaction behavior

Current actor link/account, central rank membership/permission rows, native mapped active agent, ticket/status/department, department/team memberships, context, creator link and known suspect are locked. Department scope matches existing game-tickets.mjs; no implicit Legal/Sales/Network access from stale native membership or web.tickets.admin alone. Assigned-only agents retain their native restriction.

Take requires central web.tickets.manage, current native visibility, open ticket, available agent and native department assignment restrictions. It never steals another agent's case. The same transaction changes native drsupport_ticket.staff_id, records the canonical osTicket `assigned`/claim thread event, and writes context/account audit. The native trigger invalidates versions on reassignment, status, department or ticket owner edits. Suspect edits require current assignment and triage authority.

Identical mutation retry against its exact preceding version is acknowledged without another event/change only while the matching last action/actor/from-version/current subject remain unchanged. Other stale versions409. Missing invalidation trigger causes Take to roll back503 rather than acknowledge stale metadata. Closing/reassigning or removing rank/link authority immediately invalidates native actions.

High-risk case flags use existing current dune.admin plus assigned/open/visible case; no new permission nodes, rank grants, elected-office rights, item minting, arbitrary console or teleport bypass are created. Faction edit authorizes only the current case suspect; Core facade owns the actual fenced faction mutation and preserves elections/items/travel semantics.

## Validation limits

Actual MySQL QA used uniquely named, reviewed READS SQL DATA procedure and connection-private TEMPORARY shadow tables, no production case routine or trigger. It checked27 cases including native/central scope separation, current UUID linkage, actor/creator relink, assignment, closed/stale/invalid actions, inventory/economy/faction flags, native availability restrictions and caller rollback. TEMP tables cannot be shared across sessions or have MySQL triggers, so actual cross-session revocation-lock blocking and native invalidation trigger execution remain deployment validation items. Source route tests cover missing-trigger rollback and late-audit atomic rollback.

Scripts: output/probe-game-ticket-context.py; it reads a private client config in-process and never prints secrets. Do not run an unreviewed rewrite or broaden schema privileges to make QA pass.
