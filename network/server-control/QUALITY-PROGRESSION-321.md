# Quality progression: version 1 foundation (321)

This is the first bounded slice of native321/public112194 (sources97/98/99/100/103/104). **It is not activated or deployed.** There is no new HTTP route, activity collector, database writer, game listener, scheduled job or unlock grant. The parent remains open.

## Current implementation verified on 6 November 2026

All five live planet backends contain `DuneQuests-1.0.1.jar`, SHA256 `33397373d8fd6c8a9e13a50a42256b56820d24e73cf33f4d2a0cc5b2509cd9fe`. The existing engine records worm encounters, not validated active-play minutes. The live gameplay schema has 182 quest-event rows and no quality-progress or clan-membership-history tables. Do not turn those old outcomes, online time, movement packets or survival duration into invented eligible activity.

The existing kit Sales arc remains authoritative. Its claim ledger currently has zero rows; its logic unlocks the *next* quality after a stage claim. The website has one explicit grant for each of the five qualities, all belonging to the existing unimatrix999 override. They remain untouched. Two current website clan memberships have nonzero join timestamps and linked identities. Current membership is not evidence for an earlier faction tenure or a missing historical membership period.

## Versioned policy

`quality-policy-v1.json` records the approved cumulative defaults:

| Quality | Eligible hours | Active days | Varied quests | Weekly contracts | Story chapters |
| --- | ---: | ---: | ---: | ---: | ---: |
| Common | 0 | 0 | 0 | 0 | 0 |
| Uncommon | 28 | 14 | 10 | 2 | 1 |
| Rare | 90 | 45 | 35 | 6 | 3 |
| Very Rare | 180 | 90 | 75 | 12 | 5 |
| Legendary | 360 | 180 | 150 | 24 | 8 |

Every gate is required. The personal and per-group eligible-minute caps are120/day; an active day needs30minutes. A group contributor needs168hours of verified membership. Record at most one clan and one faction per actor per UTC day, one group quest completion/day and one group weekly contract/week. Group progress belongs to its original group. Missed days do not reset progress. Paid supporter benefits and faction designs remain cosmetic; faction kit stats remain vanilla. Legendary+ is not a sixth quest tier and the cancelled paid-power proposal is not revived.

New-policy calendar buckets are UTC dates and ISO weeks starting Monday00:00UTC. This explicit boundary does not alter any existing Sales-arc calendar or weekly office-election logic.

The version is recorded on6November2026. **Its effective date is unset.** A historical schedule date does not activate it. A future activation must record an effective UTC timestamp, this immutable policy hash, audited producer/consumer versions and a reviewed migration baseline. No pre-effective receipt or inferred activity may be backfilled.

Canonical JSON SHA256 is `de796ee906904330581da5ef0444228abfc4cc144d9f661af5d6e6dca61b0c7c`. The loader rejects changes to v1; revised balance defaults require a separately reviewed policy version. Returned snapshots are copies.

## Read-only planning primitives

`quality_policy.py` provides integer/identity checks, exact UTC/ISO-week boundaries, daily-cap planning, a seven-day tenure check and a projection showing **each remaining gate**. It accepts already-authoritative totals; it does not establish that a player was active. A zero/unknown membership timestamp earns no tenure credit. Projection responses explicitly report `active:false` and `wouldQualify`, rather than claiming a persisted unlock.

The legacy preservation planner takes a verified canonical Java/Floodgate UUID and verified legacy evidence. Its result is the union of the existing Sales-arc tier, explicit grants and already-earned qualities, with an empty revocation list and zero activity backfill. Stage0 retains Common and Uncommon; four claimed stages already retain all five qualities. Unavailable evidence, malformed claims, duplicate/gapped sequences or unknown qualities stop migration planning for review; they do not fall back to a smaller grant set. Unlinked account grants stay at their original source until their mapping is verified. No username guess or caller-selected account establishes authority.

## Required remaining implementation

1. Add trusted game event producers for meaningful, varied objectives and anti-AFK eligibility. The existing worm timer is insufficient. Actor UUID, objective completion and elapsed eligible activity must be server-derived, with no browser counters, artificial completion controls or arbitrary reward amounts.
2. Add durable, immutable, replay-safe receipts and transactional per-subject/day totals. A unique actor/day/group-scope fence must enforce the one-clan/one-faction rule across retries, servers and membership changes. The helpers in this slice do **not** implement that ledger or concurrency control.
3. Read current authorised membership and verified tenure, including a real faction-history source. Freeze ownership on receipt and keep already-earned group progress with the original group. Never invent join dates or silently transfer group quality.
4. Implement varied quest families, weekly resets and sequential story receipts. Provide normal two-hour-session and solo alternatives without forced spending/PvP wins or rare-drop luck.
5. Reconcile House Tailor's five-quality storage and native consumer compatibility (312), then review an additive migration preserving paid snapshots, current Sales-arc authority and explicit owner overrides. Reconcile any partial/unknown sources before activation.
6. Wire private read projections and UI gates only after the authoritative ledger is verified. Show remaining requirements separately; XP alone is not an unlock. Complete actual Java/Bedrock, anti-AFK, replay/concurrency, transfer and membership-change acceptance before closing321.

## Checks and rollback

From this directory: `python3 -m unittest -v test_quality_policy`. Twelve tests pass, including every gate one below its boundary, calendar horizon,120-minute caps,30-minute active-day threshold, UTC Sunday/Monday/year/leap-day boundaries,168-hour tenure, canonical identities, owner overrides at zero new progress, exact legacy stage mapping, corrupt/unavailable evidence, policy mutation and immutable version checks. Fixtures contain synthetic UUIDs and make no production calls.

This source-only slice changes no live database, policy activation, inventory, receipt, unlock, membership, game service, CP response or website asset. Reverting the added policy/planner/tests/document removes the slice; no player-data rollback is needed.
