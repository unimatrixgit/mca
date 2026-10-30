# DuneBank1.0.1 — canonical bank contract

Artifact SHA256 `6b79b86349755db46df5543347b079cb8193b913f16c0af4d12dd3b1b5a84717`.

Native permissions: `dunebank.use` defaults true for personal bank use. Staff entry additionally requires `dunebank.staff.view`; deposits additionally require `dunebank.staff.deposit`; withdrawals additionally require `dunebank.staff.manage`. All staff nodes default false. Main checks native nodes at command entry, render, callback and again inside Core's fenced transaction. Staff choices are hidden unless both the native action node and SQL snapshot allow them. Every staff mutation also calls fresh authoritative `dune_staff_bank_access_lock(actualUUID)` on that same SQL connection; native OP/wildcards never substitute for SQL authorization. There is no `dune.bank.view` compatibility grant.

Public private-gateway GET `/bank/staff` now returns `account:"server"`, while the database wallet key remains `network`. Staff ledger entries include canonical `actorUuid`, selected from player_uuid. Personal ledger entries continue to omit actorUuid. Endpoints and gateway/bearer authentication are unchanged. CP must still apply its independent website authorization and authenticated personal UUID binding.

14 Java tests and42 Python tests pass. Three new Java tests cover canonical nodes, independent staff action nodes, revocation, hidden choices despite SQL permission, and no old-node lookup. Python projections cover server/network mapping and staff-only actor UUIDs. Gradle jar timestamps are disabled and entry order fixed; two jar builds produced identical bytes.

No remote install/restart/live money changes. Previous README.md and1.0.0 artifact are superseded. Existing storage, decimal arithmetic, unique transfer references, rollback and TEMP-only probe contracts remain unchanged.

Personal walletDunemarks may be negative due to existing fines; only this wallet projection is signed. Personal and staff bank balances stay nonnegative. Debtor wallets have no deposit presets and cannot deposit even0.01DM until funded.
