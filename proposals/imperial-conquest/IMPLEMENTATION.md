# Implementation checkpoint — 6 November 2026

The Owner explicitly approved starting implementation on 6 November 2026. This supersedes the design's original implementation-approval gate; it does not establish that the full system is built or ready for Alpha testing.

## First source increment

`CaptureProgress` in DuneCore is a dependency-free, immutable calculation for CAPTURE_ONLY and CAPTURE_AND_HOLD. It separates capture from continuous occupation, caps credit at one second per accepted sample, anchors the first observation, rejects stale/replayed/future/version-mismatched samples and fences calculations by lease and campaign generation. Losing occupancy, prerequisites or a continuous observation interval resets occupation. Recovery with a newer lease preserves completed capture while resetting continuous hold and the monotonic clock. Completed generations cannot be reopened through recovery.

The output contains transition flags for a future transaction; invoking this class does not publish an event, change territorial ownership, grant a quest reward or select an Emperor. A future SQL authority must compare-and-swap the persisted state and commit its event/receipt/outbox in the same transaction. The caller must load the current pinned rules and lease from that authority. Caller-supplied lease/version fields alone are not authorization.

The sample's counts are an internal observer contract. The Paper observer still needs to deduplicate identities and verify faction enrollment, world/volume, live Survival status, visibility, transfers and meaningful activity. A pair of counts is not proof that those checks exist. Wall-clock/DB deadlines remain separate from the same-process monotonic observation clock.

## Validation

The focused JUnit suite contains 21 scenarios: capture/hold completion, one-time transition flags, surplus attackers, contests, defender reversal, insufficient/empty presence, lost prerequisites, inactive campaigns, lag and offline gaps, time freshness, sequence replay, lease/generation/version rejection and recovery. These compile with Java 21 bytecode targeting. They exercise the actual production calculation, without Bukkit, SQL, accounts, currency, game commands or live campaign data.

## Remaining implementation

- SQL transactions, durable state loading, CAS/fencing checks against current authority, event receipts and outbox.
- Point definitions, surveyed worlds, graph validation and publication/version pinning.
- Paper observation and asynchronous authority bridge; lease acquisition and outage recovery policy.
- Funded declarations, preparation/windows, escrow, office migration and succession settlement.
- HOLD_CONTROL, DEFEND, recapture ownership generations, compound quest graphs and current-identity revocation.
- Council, legitimacy, rebellion, public redacted projections and Java/Bedrock interfaces.
- Real MySQL concurrency/crash tests, live artifact reconciliation and actual client acceptance before activation.

This source increment is not wired into the old `Politics` command or deployed. It deliberately cannot activate a campaign or mutate existing office, money, quest, receipt or world state. The immutable release `alpha-1.0.0.3-20261106` was made before this increment and does not contain it.
