# DuneQuests 1.0.0

Authoritative MySQL encounter state with a basic virtual game GUI and private control API. Requires Paper 1.21.4 / Java21 and Core20 or later with `WormBridge`; does not replace financial, inventory, faction or profile code.

Install `build/libs/DuneQuests-1.0.0.jar` on each backend where players need `/dunequests`. Set `plugins/DuneQuests/config.yml` `server-id` to `arrakis`, `kaitain`, `giedi_prime` or `lobby`. Blank database settings inherit the existing private Core MySQL configuration. Only the exact Arrakis instance schedules encounters; other instances show authenticated personal history. Connector/J26.10.0 and its runtime dependency are packaged; no live credentials are packaged or logged.

`schema.sql` creates only `dune_quest_definitions` and `dune_quest_events`, then idempotently seeds **Buried Thunder**. The initial definition is enabled, hidden, hourly, with 300-second survival and **zero additional Dunemarks**. Its first due time is database-now plus one hour; restarts do not reset or duplicate the schedule. No quest code writes a wallet, receipt or economy balance. Existing Core first-finder/combat mechanics remain unchanged.

## Encounter lifecycle

A transactional `FOR UPDATE SKIP LOCKED` claim advances the definition's next-run clock and creates a private preparing attempt. The engine chooses uniformly among eligible online Arrakis survival players without looking at faction. Eligibility excludes dead/offline/newly joined players, the spawn area and WorldGuard claims. A real encounter requires safe dry ground 48–80 blocks away, a conservative unclaimed box covering the large initial body, a valid world border and the Core's persistent two-worm cap. Unknown active claim systems or unavailable protection checks fail closed. Chunk loading for initial placement is bounded to32 chunks.

No eligible player, occupied cap, changed target or unsafe terrain produces a skipped administrative attempt. A real spawned worm is linked to the persisted assignment and its database-clock deadline before a discovery notice is sent. The owner must remain alive, in survival on Arrakis and outside protected areas; leaving eligibility fails the encounter. Surviving the whole configured duration or a real worm-death event completes it. Completion is compare-and-set and cannot pay a reward or replay a payout. Interrupted preparation expires; failed preparation with no confirmed deadline is excluded from personal history.

Assigned quest worms are retired without death rewards on cancellation/expiry cleanup. A real defeated worm retains the existing Core death behavior. Saved body markers and the Core retirement ledger prevent an expired unloaded quest worm from gaining a new quota slot when it returns.

## Game interface

- `/dunequests`: own assigned/discovered/retained quest cards only; `dune.quests.view` defaults true.
- `/dunequests admin`: definition timing, hidden state and CP administration link; `dune.quests.admin` defaults operator.

Both use the existing virtual renderer, with no chest fallback. Asynchronous list responses recheck the actual player UUID, current permission and exact virtual loading view. Definitions are never sent to ordinary players before assignment/discovery. There is no artificial completion/test button or reward grant on this interface.

## Private API

Deploy `network/server-control/quest_engine.py` beside the latest `agent.py`. The quest routes retain the existing private bearer and gateway-mode restrictions; website authentication, verified UUID derivation and `web.quests.view/manage` checks belong in the CP relay. The installer at `output/worm-quest-20261029/install-quest-routes.py` surgically adds these routes to a current agent without replacing its ban/pardon fixes.

| Method / fixed route | Result |
|---|---|
| GET `/quests/definitions` | `{definitions:[{key,title,description,engine,enabled,hidden,intervalSeconds,surviveSeconds,rewardDunemarks,version,nextRunAt,updatedAt,updatedBy}],cap:2,rewardDunemarks:0}` |
| GET `/quests/events` | Private administrative last100 attempts, including preparing/skipped state and UUID/diagnostic fields |
| GET `/quests/player?uuid=<verified UUID>` | That UUID's last50 real assigned/completed/failed/discovered events; no unassigned definitions, failed preparation, skipped attempts, other UUID or worm/location metadata |
| POST `/quests/definitions` | Versioned definition create/update; returns `{ok,key,version,cap:2,rewardDunemarks:0}` |

POST accepts only `key,title,description,enabled,hidden,intervalSeconds,surviveSeconds,rewardDunemarks,version,actor`. Key3–64 lowercase characters; title3–120; description10–2000; interval3600–604800seconds; survival60–1800seconds; reward exactly0; version0 for a new definition and the current version for an update. Actor must be replaced by the authenticated staff username upstream. Unknown fields, including a cap override or arbitrary engine, are rejected. Stale versions return409. No method can fabricate player events or mark them completed. Pausing, enabling or changing schedules does not replay a past outcome.

## Validation

Four Java outcome tests cover actual duration boundaries, ineligible owners, interrupted preparation, real defeat and visible states. Seven Python engine tests cover bounds, fixed rewards/cap, exact UUID filtering, private definitions, parameter binding, schedule preservation, new-definition creation and version conflicts. Agent tests cover private authentication, mode isolation and failure responses, while retaining its existing ban/pardon tests.

`output/worm-quest-20261029/quest-probe.jar` runs the actual JDBC store against connection-local TEMPORARY tables only:

```
java -cp quest-probe.jar:DuneQuests-1.0.0.jar com.dunerealms.quests.QuestStoreProbe /private/database.properties schema.sql
```

It cannot expose its fixtures to live game/API connections or change real assignments, worms, wallets or rewards. Live Paper/WorldGuard startup, Java/Bedrock presentation and actual encounter behavior remain separate acceptance checks; unit tests are not represented as a client playthrough.
