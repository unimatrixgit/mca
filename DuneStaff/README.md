# DuneStaff — assigned support casework

Frozen-alpha implementation of the previously requested assigned-ticket staff tools. Command: **`/dunecase [ticket-id]`**; exact namespaced command `dunestaff:dunecase`. No collision with DuneMenus' `/dunestaff` overview.

Requires DuneCore, DuneMenus and DuneEss. Java21 / Paper1.21.4 API; compatible candidate for the1.21.8 fleet staging. This module does not replace Core inventory persistence or the canonical osTicket system.

## Implemented workflow

- Loads the current bounded staff-ticket list from `/api/game/tickets`.
- Opens canonical creator/suspect/assignment/version/action context through `/api/game/ticket-context`.
- **Take case** uses the canonical compare-and-swap assignment API.
- **Set suspect** offers actual currently online UUIDs only; the website verifies a known real game identity and current case authority. No offline UUID hashing, names-as-identities, raw console or arbitrary commands.
- **Go to creator / suspect** authorises and durably audits the exact selected target under the case lock in the Core target checkpoint. Only after a successful commit, the same main-thread continuation rechecks online/native authority and teleports the actor to that exact UUID's current location. Teleport is not falsely claimed to be SQL-atomic: assignment can theoretically change in the tiny post-commit/pre-move window. A cancelled teleport can leave an authorised audit intent without a completed move; it never causes a replacement item or currency grant.
- **Inspect suspect inventory** reads the online target's current0–40 slots under the same authority fence. Item name/lore/type/amount are shown. A separate confirmation can **confiscate the exact reviewed stack** only if its serialized bytes still match. Full original item bytes are stored in durable case evidence in the same SQL transaction as the target inventory checkpoint. No item is transferred to the staff member; no new item is created.
- **Wallet correction** shows current target-wallet and network-treasury balances and offers1/10/100/1000 Dunemark transfers. Both directions conserve exact cents. There is no mint path. Current case authority and independent staff-bank authority are required. Each confirmation has a unique receipt, audit row and treasury ledger entry in the same Core target transaction.

The inventory editor is deliberately bounded to removal/confiscation into durable evidence. It is not a general item generator or a free-drag two-player inventory transfer.

## Permissions and protection

No new grants or rank nodes are created. Entry requires current native `dune.tickets.staff`. Target actions additionally require current `dune.admin`, plus the canonical SQL procedure's current mapped agent/department/assignment/website-rank checks. Take/context operations also receive fresh canonical API checks.

Inspection/confiscation/wallet corrections forbid self-targets, offline targets, OP targets and targets with existing `dune.moderation.protected` or `dune.ban.protected` rules. There is no implicit OP/protected override. Teleport may go to a protected creator/suspect because it moves only the authorised staff actor, not the target.

Wallet credit requires native `dunebank.use`, `dunebank.staff.view`, `dunebank.staff.manage` plus canonical bank can_view/can_withdraw. Recovery to treasury requires the first two plus `dunebank.staff.deposit` and canonical can_deposit. Case assignment alone never grants treasury access.

## Persistence and transaction contract

Canonical website migration: `website/integrations/osticket/game-ticket-context.sql`.

`CALL dune_case_action_lock(actorUUID,ticketId,expectedContextVersion,action)` is called inside `DuneCore.transactNetworkInventory(actualOnlineTarget, Consumer<Connection>)`. Returned actor, assigned agent, ticket, version and selected target must exactly match. Current case status/assignment/department/link/rank context remains locked until the caller commits. No module method commits independently.

Native audit table `dune_staff_case_audit` is initialized without reading website tables directly. It records requestUUID, ticket/version, actor/target UUIDs, action, optional slot/exact item BLOB, optional wallet delta and resulting wallet/treasury balances. The existing native schema writer owns this table; do not grant it raw website SELECT privileges.

Core handles inventory rollback and ambiguous commit outcomes. DuneStaff never falls back to its own save, refunds or repeated credits after an unknown checkpoint result. Confirmations are owner-bound and single-use through the shared DuneMenus renderer. Changes in context version, target, assignment or permissions reject the old confirmation.

## Configuration

Uses Core's existing private MySQL configuration and loads its MySQL driver through Core's classloader. Uses DuneEss' existing private `website-bridge.yml` with fixed origin `https://cp.dunerealms.com`. Private requests are bounded to256KiB, limited to the fixed endpoints and have timeouts. Secrets, raw private responses and confiscated item data are never logged or sent to public UI.

No extra database credentials or player data belong in this repository.

## Validation

`gradle test jar` on Java21. Unit tests cover exact API shape and bounded response handling, no implicit OP entry, protected/self/disconnected targets, direction-specific bank permissions, current authority/context/target matching, conservation, duplicate receipts, rollback of audit and funds, underfunded transfers, exact evidence BLOB custody, slots/size bounds, and use of the actual Core connection without a fallback.

Optional isolated MySQL JDBC probe: `src/test/java/com/dunerealms/staff/MySqlCaseProbe.java`. It creates session-local TEMPORARY shadow tables only and uses fixture authorizers. It tests the actual native store SQL for conservation/replay/rollback/evidence without invoking production authority or moving any actual item/funds. This is separate from root's canonical SQL-routine validation and actual player/client acceptance tests.

No deployment or production case actions were performed by this implementation task. Root should deploy the canonical website/API/procedure first, verify grants, install this candidate on the Paper backends and perform the final role/context and actual client menu checks. An ordinary Java book or resource pack is not a substitute for those authority checks.
