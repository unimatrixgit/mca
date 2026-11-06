# Game menus — current fleet and acceptance record

Native task **326** remains open. This 6 November 2026 checkpoint reconciles archived tasks 111, 114, 115 and 125 with the actual installed fleet. It changes inventory tooling and documentation only. It is not a menu redeployment, a new resource pack or a client acceptance result.

## Verified current artifacts

[The deduplicated inventory](fleet-20261106.json) records 137 installed plugin JARs, 32 unique artifacts and the present engine files across five planets, lobby and proxy. All seven matching systemd services were active. A read-only `bukkit:version DuneMenus` query separately confirmed the loaded **1.2.0-graphical** renderer on all six Paper nodes. An installed-JAR inventory alone does not prove every plugin enabled or every workflow works.

| Component | Current installed state | Boundary |
| --- | --- | --- |
| DuneMenus | All six Paper nodes: SHA256 `24b926c8e49047bada7f9a64a028a4b5a9a709339c2dcb2c1093da4688359810`; filename retains 1.1.0, descriptor/runtime say 1.2.0-graphical | Current source passes 31 tests; all 19 class files match the installed JAR byte-for-byte. Do not replay older 1.1 candidates. |
| Core | Arrakis `1.0.27-layout1-jobs1-sun374`, SHA256 `acf870cd2b87540b72dd20fd8a1f888e6957392c2f758247136a8c1b5f845759`; other four planets and lobby use `cb2f4c21846c88bf0e11d2cf099c849357d4cbffe9b0ceb4e6b4b6c81236561d` | Preserve the Arrakis stillsuit fix. Task 323's facade/shared staff-route candidate is staged separately; it is not installed here. |
| DuneEss | Arrakis 1.0.11-spicebuyer; lobby 1.0.11-sqltime1; other four planets 1.0.11 | Preserve these distinct fixes and their exact inventory hashes. Never homogenise them with a whole source rebuild. |
| DuneBank / DuneClans | All six: 1.0.2 / 1.0.3 | Source 1.0.3 / 1.0.4 is newer. Full source parity and its pending asynchronous-view changes are not claimed by this inventory. |
| DuneAtelier | All six: cosmetic 1.0.0, SHA256 `b0e640901419eb3e544ae0e60b52867f927341246990a2f5cc67bc1a4dc86bf8` | Source 1.0.2 has a separate enchantment policy. Do not ship paid combat bonuses or replace this artifact during menu work. Task 318 verifies the installed receipt store separately. |
| Other native modules | Paper Ban 1.0.2, Items 1.0.5, NPC 1.0.2, Protect 1.0.0, Quests 1.0.1; Terrain 1.0.12 on planets; Lobby 1.0.1-exchange in lobby | Exact hashes are in the inventory; no whole-source parity claim. |
| Typewriter | 0.8.0 on Caladan, Kaitain and Sigma Draconis IV, with packetevents | Not installed on Arrakis, Giedi Prime or lobby. Do not infer authored-conversation availability on every backend or install dependencies as part of this audit. |
| DuneStaff / canonical case SQL/API | No DuneStaff plugin in this fleet; case-context SQL/API not installed | The full assigned-case workflow is still a dependency, not a delivered menu. |

The four installed shared `NativeMenus` bridges in Arrakis Core/Essentials/Items/NPC have the same class SHA256 `3ebb0cd1a76ad82e294f42341f1bc0a74ac5df70358480c2c662f81995b02598`. Dependent modules can use Core's bridge; absence of a private copy in their JAR is not an absent API. Check exact callable signatures when preparing their newer candidates.

## Presentation and route inventory

Java 1.2.0 uses the graphical inventory route: six cards per page, owner-bound hit slots and separate review/confirmation. The earlier native Java dialog/book navigation route is retired. All item transfer/click/drag paths are cancelled in display inventories. Bedrock uses native SimpleForms, opaque callbacks and the independent `DRUI|` skin. Model identity, current authority, revision, 30-second expiry and single-use mutation gates remain required; presentation never grants game authority.

These are verified source entry points paired with installed modules, not a claim that every entry has been clicked in a real client:

| Area | Entry / implementation | Required acceptance |
| --- | --- | --- |
| Main navigator | `/navigator` or `/dune menu`; enabled Core is preferred; DuneLobby's `openNavigator` is the fallback without Core | Native player menu, several pages, optional-pack fallback, correct backend, menu item and command agree. |
| Guild departures | Lobby liaison and `DuneLobby.openNavigator` / departure confirmation | Only configured destinations; fresh availability/fare/permission; cancellation moves no player or funds. |
| Jobs | Core main menu → Spice Jobs → Spice Miner / Manage Job (`JobsScreens`) | Hourly offer rollover, one current contract, original quota/pay/deadline/fine, no duplicate acceptance or early payout. Existing job engine is authoritative. |
| Essentials / exchange | DuneEss help/travel/homes and `ExchangeMenu` via `NativeMenus` | Current listings/ownership, escrow, back/cancel/reopen and stale selection; preserve lobby SQL timestamp fix. No real trade was executed by this audit. |
| Personal / faction / staff bank | `/dunebank`, `/staffbank`, `DuneBank` | Verified UUID and current role on every action; exact decimal values, duplicate confirmation, revoked permission, stale async replies and ledger. No transfers in this audit. |
| Clan center / shop | `/duneclan`, `DuneClans` | Actual membership/officer scope, current clan bank/design/receipt; clan quality delivery beyond the legacy native flow remains a separate dependency. |
| Kits exchange / paid delivery | `/duneatelier`, `DuneAtelier` | Immutable paid cosmetics, receipt owner, full/partial inventory, reconnect/transfer and uncertain checkpoint recovery. Task 318's synthetic SQL checkpoint tests do not replace this client test. |
| Quests and NPCs | `/dunequests`, existing managed NPC/smuggler tools and installed authored conversations | Actual enabled quest definitions, discoverability, staff scope and permission revocation; no invented conversation engine or reward producer. |
| Staff console | `/dunestaff` or `/navigator staff`; `StaffConsole` | Show only enabled services/current permissions, namespace commands as the player, no OP-only/console bypass. The old Core staff-desk route awaits task 323's reviewed patch. |
| Houses / governance | `/dunemenus governance`, `GovernanceScreen` | Current read model and actual authority; assigned-case faction correction remains unavailable until its reviewed backend dependencies exist. |

## Resource-pack proof

All six Paper property files point to the optional public [v7 Java archive](https://www.dunerealms.com/downloads/DuneRealms-Imperial-GUI-1.21.8-v7.zip), SHA1 `6222875ddd969bebbeefc5b607051fdf61b1a14e`, SHA256 `49821f43d515c6c5151bf22f936aa55706b8eafd66d8be2697bd5c1fd077b33c`. `require-resource-pack=false` is retained. [Actual HTTP proof](java-pack-http-20261106.json): HEAD 200 with length **20,061,331**, ZIP MIME and byte ranges; bytes 0–31 return 206 and the correct Content-Range; the full downloaded bytes match both hashes. ZIP CRC/path/duplicate-entry checks pass; all 62 JSON files in 104 entries parse and the imperial font is present. Pack metadata declares its format interval; this is not a claim that every client in that interval has been tested.

The Geyser stack contains the independent item pack and the Navigator skin, without duplicate UUIDs. The Navigator's retained `...-v1.mcpack` filename contains the installed v2 archive, SHA256 `42464b91ed730758b1ab77a24892322b9e89c1cd7be1fd8dc240e080122a9b9f`, manifest version `[1,1,0]`. Source passes eight package/contract checks; the exact installed archive also passes those eight checks in an isolated extraction. All 15 entries agree with current source after normalising four JSON files' CRLF/LF endings; artwork bytes and parsed JSON are unchanged. Its whole build ZIP hash differs, so do not replace the installed archive merely to obtain a matching build timestamp or line ending.

No client screenshot, resource acceptance dialog, actual touch target or focus movement was verified in this audit.

## Outstanding real-client matrix

Every row below is **PENDING** in both columns. Record actual client version, platform/input, verified test identity, backend, exact loaded pack, steps and observed result. Screenshots must come from the real client and show the tested state; an artwork reference or headless model is not acceptance.

| Scenario | Java | Bedrock desktop / touch / controller |
| --- | --- | --- |
| Pack accepted, declined, download interrupted and reconnect | Pending | Pending |
| Main/player/clan/bank/jobs/staff overview; two pages; long labels/records | Pending | Pending |
| Review, confirm, Back, Close, Escape/controller-back; no extra action | Pending | Pending |
| Hover/focus/pressed states, card boundaries, item tooltip/skin and icon failure | Pending | Pending |
| Double click, expired view, replaced screen, reopened view, wrong owner | Pending | Pending |
| Permission/membership revoked while loading or before confirmation | Pending | Pending |
| Inventory shift/number-key/double-click/drag/drop cannot take display items | Pending | Pending |
| Full/partly full inventory, delivery retry and reconnect/server transfer | Pending | Pending |
| Existing item textures and unrelated plugin/custom/modal forms remain usable | Pending | Pending |
| Real configured jobs/quest/NPC paths and supported host dependencies | Pending | Pending |

Use controlled non-financial QA for mutations; do not generate real purchases or operate existing players' items/funds to fill this matrix. The available PC has not exposed a targetable game client, so no client result is invented. The blocked local-browser confirmation also remains a separate review/merge limitation.

## Reproduce and next gate

Run `tools/inventory-game-menus.py --host-role planets --out /review/planets.json` on the planetary host and `--host-role gateway` on the gateway. The tool selects only the current five planets, actual lobby and proxy; it never traverses the gateway's obsolete Arrakis instance/backups. It reads JAR metadata, hashes, matching service state and whitelisted public pack properties, never database/proxy/rcon credentials. The loaded-renderer proof used only a read-only Bukkit version query; no player commands, messages, game mutations or restarts were issued.

Review/merge this documentation/tooling slice, then complete the real client matrix and its dependencies before task 326 closes or a coordinated task 327 rollout is allowed. No runtime rollback is required for this audit. Any later release must preserve the per-host Core/Ess variants, exact paid snapshots/earned progression, current pack identities and original worlds, with scoped backups and a consistent rollback plan.
