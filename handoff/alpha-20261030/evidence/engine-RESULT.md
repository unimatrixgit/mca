# Paper 1.21.8 compatibility validation — frozen Alpha

Status: PASSED isolated engine/plugin startup and synthetic item upgrade checks. This is not a production rollout or Java/Bedrock visual acceptance test.

## Actual checks

- Official Paper 1.21.8 build60, SHA256 `8de7c52c3b02403503d16fac58003f1efef7dd7a0256786843927fa92ee57f1e`.
- Actual production Java21 binary `/usr/lib/jvm/java-21-openjdk-amd64/bin/java`.
- Genuine live Arrakis plugin JARs copied without production plugin directories, worlds, credentials or database configs; DuneMenus replaced by root's staged1.1.0.
- Actual live lobby DuneLobby1.0.1 and ImageFrame2026.1.5.0 also loaded successfully on1.21.8.
- LuckPerms5.5.88 initialized successfully using only its immutable cached libraries, with fresh local default storage. First isolated run lacked those caches and failed to fetch them; this was fixed without opening network access.
- `systemd PrivateNetwork=yes` isolated BOTH external network and host-local DB tunnels. Core shared storage disabled; bank, Atelier, quests, clan transactions, office claims and global-ban verification correctly stayed unavailable without private configs. No production database was connected or queried.
- Clean server boot and shutdown, no plugin-enable errors or NoSuchMethod/NoClassDefFound/AbstractMethod/UnsupportedClass errors in final successful run.
- New DuneMenus1.1.0 actual illustrated dialog definitions successfully constructed on the real engine.
- An isolated1.21.4 server generated26 real factory items across all five factions, serialized each using native NBT bytes AND BukkitObjectOutputStream. The1.21.8 engine read both payload types and preserved exact normalized material, amount, display name, lore, enchantments, attribute keys/amount/operation/slot, armor trim, color, string PDC (including faction and quality). No player inventory/funds were used. Both engine runs shut down with exit0.
- Current deployed Java resourcepack v4 advertises supported formats46–64, covering1.21.4–1.21.8. This metadata check is not visual validation.
- All three production planet services remained active after testing. Lobby and production services were never stopped/reconfigured by this audit.

## Evidence

- `java21-validation.log`: full Arrakis plugin startup under actual Java21.
- `codec-write.log`: real1.21.4 factory serialization,26items.
- `codec-read.log`: real1.21.8 full plugin load including lobby/ImageFrame and26item dual-codec preservation.
- `jar-manifest.json`: SHA256 and native descriptor of genuine Arrakis source JARs. Its DuneMenus entry describes the copied source1.0.0; runner explicitly replaced this one candidate with1.1.0.
- Scripts at output/prepare-fleet-staging.py, output/run-fleet-java21.py, output/run-crossversion-codec.py; QA plugin source output/engine-codec-probe.
- Remote isolated test directories: `/opt/dunerealms/staging/fleet-1218-20261030`, `/opt/dunerealms/staging/codec-1214-20261030`.

Do not deploy these entire directories: they contain intentionally fresh/default config, QA artifacts and test worlds. Deploy only verified release engine/plugin artifacts using the normal coordinated cutover.

## Required cutover and rollback

1. Finish root's final GUI and Bedrock resourcepack candidates, and actual Java+Bedrock visual/interaction acceptance. Startup checks cannot prove client rendering, controller/touch behavior, receipt delivery with real authority or live gameplay transactions.
2. Plan one maintenance window for ALL four shared-inventory backends. Do not allow a rolling1.21.4/1.21.8 mixed fleet to write new serialized items through the central database.
3. Freeze all game-state writes, including native sessions, private agent commerce/bank, direct website reward/redemption writers and any relevant automation. Keep read-only/account services where appropriate. Have all players quit/checkpoint and gracefully stop the four backends.
4. Capture a consistent gameplay MySQL snapshot plus all four world/playerdata/config/JAR/engine snapshots. Keep current bridge/identity secrets and LP database/config intact; do not change UUIDs, database endpoints, worlds or permissions as a side effect of engine upgrade.
5. Replace engine across all four together, retain compatible exact plugins, install root's final GUI and staged ban fix. Start in a controlled window; verify database ownership, actual inventory/experience, Java+Bedrock forwarding, authoritative bank/kit/ticket/quest flows, and proxy travel before accepting writes/users broadly.
6. A rollback after1.21.8 saves must restore BOTH the consistent gameplay database and all backed-up world/playerdata together with prior engines/plugins. Merely reverting the Paper JAR risks newer item/world data on an older engine. Reconcile any external events admitted after snapshot; this is why write freeze is required.

No automatic cutover was performed. Database/inventory transactional semantics remain untested on copied real state in this isolated audit; those stay explicit release checks.
