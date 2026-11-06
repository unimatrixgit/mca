# Native task 323 — current DuneMenus/Core integration

This is a guarded review-artifact builder. It never installs a plugin, restarts a service, connects to the gameplay database or copies production configuration. Do not deploy the generated compilation donor or a whole source rebuild of Core.

## Current state checked on 6 November 2026

All five actual planetary servers run the same DuneMenus renderer. The file happens to be named `DuneMenus-1.1.0.jar`, but its native descriptor is **1.2.0-graphical** and SHA256 is `24b926c8e49047bada7f9a64a028a4b5a9a709339c2dcb2c1093da4688359810`, matching the tracked graphical release manifest. Current source passes 31 tests; all 19 compiled classes are byte-identical to that installed JAR and the normalized descriptor matches. Rebuilding changes ZIP metadata, so the rebuilt whole-JAR hash differs; do not replace the installed renderer merely to replay the older 1.1.0 feedback candidate.

Arrakis Core is the actual installed `1.0.27-layout1-jobs1-sun374` artifact, SHA256 `acf870cd2b87540b72dd20fd8a1f888e6957392c2f758247136a8c1b5f845759`. Its public `transactNetworkInventory` checkpoint exists, but the newer `adminAssignFaction` facade is absent. Its main staff-desk button still uses `AdminGui.canUse` and the old `dune admin` route. The other four planets use a different shared baseline, `cb2f4c21846c88bf0e11d2cf099c849357d4cbffe9b0ceb4e6b4b6c81236561d`; this Arrakis candidate must not be copied over them.

## Bounded candidate

The builder extracts the exact facade method from current `DuneRealms-minecraft-only/.../DuneCore.java`, including its null/identity guards, into a compilation-only donor. ASM adds only that public method and its own namespaced implementation lambda to the pinned installed Core class. It never copies the donor constructor, map field or transaction stub. All **99 existing Core methods** remain structurally byte-identical, including the stillsuit logic and the real fenced transaction implementation.

The staff-route patch changes exactly two existing `ConfigurableGui` guards and its one staff-desk command to the shared renderer's current capability and `dunemenus:dunemenus staff`. All **73 other GUI methods** stay structurally unchanged. The new `StaffMenuBridge` asks the enabled installed renderer for current permitted services on every invocation and fails closed on missing/incompatible/unavailable renderer. Commands still execute as the player; no console delegation or rank/permission grants are added.

Only `DuneCore.class`, `ConfigurableGui.class` and the descriptor change; `StaffMenuBridge.class` is added. All **2,101 other JAR entries** stay byte-identical, no entry disappears, and the existing `SunWearPolicy.class` is retained. The original descriptor version gains `-case323`, retaining `-sun374`.

## Reproduce for review

Use Python 3, Java 21 and Gradle 8.14.3. Supply read-only copies of the exact installed Core and renderer JARs; no server directories or credentials are needed.

```sh
python3 tools/core-integration-323/build_candidate.py \
  --baseline /review/arrakis-Core-sun374.jar \
  --baseline-sha256 acf870cd2b87540b72dd20fd8a1f888e6957392c2f758247136a8c1b5f845759 \
  --renderer /review/live-DuneMenus-1.2.0.jar \
  --renderer-sha256 24b926c8e49047bada7f9a64a028a4b5a9a709339c2dcb2c1093da4688359810 \
  --out /review/candidate323 --gradle /tools/gradle-8.14.3/bin/gradle
```

The driver first tests the donor, performs guarded merges, verifies every JAR entry, then runs the same behaviour tests with the **actual candidate first on the classpath** and the **actual installed renderer**. The tests assert those code-source paths, preventing the donor from silently shadowing the reviewed artifacts. The facade's checkpoint/connection are injected test doubles; no real faction, inventory, economy or case action occurs.

Wrong Core or renderer hashes and output paths containing a `plugins` directory fail before output creation. These guards also run under `python3 -O`. Wrong class owners, missing map/checkpoint, static checkpoint, existing facade/reserved lambda and unexpected GUI patch shape are rejected. Already-modified input must be reconciled, never patched again blindly. Stable ZIP metadata for the new helper makes repeated builds from the same input byte-identical.

## Actual results and release boundary

The final Arrakis review candidate has SHA256 `dfc339c8acc19e31846171226959699bb9461ccb29ee371b74ea502e7cd96e38`. Two independent output directories produced byte-identical JARs. **24 checks pass with zero skips** against the actual candidate and installed renderer: authority-before-mutation, all five exact factions, null/invalid identity, current permission/disconnect/off-thread denial, rollback preserving absent versus empty faction entries, unknown-commit handling without another checkpoint, current staff capability, no OP-only bypass, dependency disable/revocation and the ASM guards. Six driver rejection checks pass across ordinary and optimized Python. The installed renderer separately passes its existing 31 tests.

This candidate is **staged, not deployed**. Native task 323 stays open for reviewed merge, real Java/Bedrock menu interaction acceptance, current live-hash recheck, backup and a scoped rollout with service/plugin verification. Preserve real players and receipts; use a controlled restart after the target server is clear, never hot-reload Core. Rollback restores only that server's exact backed-up JAR and restarts it. Do not change the other four Core baselines, existing resource packs, worlds, identities or permissions.

The added facade does not activate the source-only game case-context SQL/API or install DuneStaff. Those dependencies are absent from production and require their own reviewed rollout. Passing this packaging/unit check is not a claim that the full in-game case system is available.
