# Stillsuit sunlight wear — ticket #590078 (native374)

Implementation and staged validation, 6 November 2026. This checkpoint is not a deployment claim.

Direct clear daytime sunlight in the Arrakis world wears each equipped tagged, damageable stillsuit piece independently. Water, shade, night, rain, other worlds, Creative, Spectator and dead players do not accumulate wear. Unbreakable and unrelated equipment are untouched. Per-item exposure counters persist in item metadata and pause outside exposure. A complete stillsuit protects against heatstroke until a piece breaks; protection is checked again after the normal durability operation. Water resets the uninterrupted unarmoured heatstroke timer.

`arrakis-sun.stillsuit-wear-seconds` configures Common/Uncommon/Rare/Epic/Legendary intervals, defaulting to15/30/45/60/90 exposed seconds per one-point normal durability attempt. Unknown quality uses Common. Invalid values are bounded to1..86400 and clamped in rarity order to remain strictly increasing. Existing configurations automatically use the defaults without replacing their other settings.

Paper's `Player.damageItemStack(slot,1)` retains normal Unbreaking, item-damage events and break handling. This code never directly writes an armour damage value. Cosmetic ignition and the old blanket sun-related FIRE_TICK cancellation are removed. Other combat/environment handlers and inventory, trade, progression, jobs and menus are unchanged.

## Actual installed-artifact validation

Live Arrakis runs Paper1.21.8 build60 and Java21 on159.69.82.29. Its Core metadata is1.0.27-layout1-jobs1, despite the legacy DuneCore-1.0.10.jar filename. All five planet copies matched recorded Alpha3 SHA256 cb2f4c21846c88bf0e11d2cf099c849357d4cbffe9b0ceb4e6b4b6c81236561d. The checkpoint source includes other undeployed work, so deploying its entire rebuilt JAR would replace unrelated live functionality.

`tools/PatchSunWear374.java` guards that exact original hash, transplants only solar/wear methods and their supplier lambda, removes the old sun fire-damage handler, adds SunWearPolicy and changes plugin version to1.0.27-layout1-jobs1-sun374. It verifies every unrelated JAR entry and every unrelated Core method remains unchanged. It refuses an unexpected original artifact. Reproduce using Java21 and ASM9.7.1/asm-tree9.7.1 from Gradle8.14.3's lib directory after compiling the candidate source; arguments are original.jar, compiled-main-directory, output.jar.

Six new SunWearTest tests PASS against this actual patched artifact: rarity defaults/config bounds, normal per-slot durability boundary, preservation of unrelated/unbreakable equipment, protection loss in the same tick as a break, and exposure gating across nine environmental/player scenarios. Water resets are covered. These are server-API mocks, not real-player gameplay acceptance.

Full-suite comparison with class isolation (`tools/test-sun-artifact.gradle`) against the original installed artifact:76tests/43failures; patched artifact82tests/the same43failures. No new failures, no missing tests, all six added tests passed. The checkpoint-source baseline separately has76tests/37failures with isolation; without isolation it has51failures due partly to leaking Bukkit static mocks. These unrelated suite failures remain unresolved and are not described as passing validation.

Staged verified patch SHA256 acf870cd2b87540b72dd20fd8a1f888e6957392c2f758247136a8c1b5f845759. Binary output and private credentials are not committed. Runtime rollout must retain backups, stop/save the affected Arrakis backend gracefully, replace only the guarded artifact, restart, and inspect current startup/health logs. No server engine, world or database migration is required. Rollback restores the original JAR; no inventory/schema/world downgrade is involved.
