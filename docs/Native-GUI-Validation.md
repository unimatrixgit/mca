# Native GUI branch validation — 2026-10-21

Branch: `feature/native-gui-1.21.8`, based on `c692949a78f09a117d84684dbae7f9aef39189af`.

Java: Temurin 21.0.12.1; Gradle: 8.14; Paper API: 1.21.8.

| Module | Compile/build | Automated tests |
| --- | --- | --- |
| DuneRealms Essentials | Passed | 19 passed |
| DuneRealms Core | Passed | 11 passed |
| DuneRealms Terrain | Passed | 2 passed |
| DuneRealms NPC | Passed | 5 passed |
| DuneRealms Lobby | Passed | No existing automated tests |

New checks include owner-bound menu sessions, stale screen invalidation, one-use selection,
expiry, normalized/validated home names and teleport consent with repeated acceptance rejected.
The existing SQL exchange, economy, announcements, terrain, and NPC tests remain enabled.
The Paper API upgrade required stubbing `Plugin.namespace()` in the sandworm mock; production
sandworm code was unchanged. Mockito is now installed explicitly as a test-only Java agent.

Geyser/Floodgate Spigot and Velocity variants and ViaVersion downloaded successfully and
matched the official pinned SHA-256 metadata. The all-server ZIP has eleven plugin JARs;
the network ZIP verifies 45 JAR placements across the proxy and six backends. ZIP integrity,
plugin descriptors, workflow YAML, Python syntax and `git diff --check` were checked.

No existing worlds, runtime configuration, database or player data were opened or modified.
The local packaging test creates fresh-install templates only in ignored generated directories.

## Pending

* Live Paper 1.21.8/Velocity, Java 1.21.8, supported older Via clients, and Bedrock clients.
* Visual dialog/form behavior, bed/anchor death/respawn, actual transfers and live shared SQL.
* DuneGit push: unauthenticated HTTPS write requires repository authentication.
* GitHub publishing: connected API returned 403, "Your account was suspended".

No production deployment or merge has been performed. This is a staging candidate, not a
claim of live compatibility. Follow the checklist in Native-GUI.md before production rollout.
