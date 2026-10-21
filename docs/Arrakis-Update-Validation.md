# Arrakis update validation

Development branch: `feature/organic-arrakis-plateaus`.

Built on Java 21 for Paper 1.21.8: DuneRealms-Core 1.0.11, DuneRealms-Essentials 1.0.3, and DuneRealms-Terrain 1.0.9. Existing compiled NPC and Lobby JARs remain unchanged. 50 JUnit tests pass across Core (11), Essentials (19), Terrain (15), and NPC (5). Lobby has no unit tests.

Terrain tests cover deterministic asymmetric contours, cardinal gaps 300–500, upper and lower terrace groups, bounded erosion ridges, biome footprint alignment, exact 900-column rich patches including negative coordinates, seeded fortress attempt rate, persistent deposit identity consumption across listener restarts, farm-free village palette/bed/job-site generation, and fortress entrance/vault geometry. Deposit tests use mocked Paper chunk PDC and creative harvesting; real server event dispatch still requires staging.

The development preview invokes the actual compiled private Arrakis formation generator using a ChunkData recording proxy. At X 166, Z 165 the lower flat sand surfaces are Y 85 / 89 / 93 and upper surfaces Y 117 / 121 / 125. Each group has four-block steps. The rocky shoulder between groups is intentionally taller. Both exposed block positions and material types are recorded for the renderer. [Generator render](../DuneTerrain/previews/dune-highlands.png) is not an in-game screenshot or shader promise.

Biome and loot JSON parse successfully; data pack archive and all five project JARs pass ZIP integrity checks. Release packaging verifies pinned crossplay SHA-256 checksums and includes the server-side Arrakis datapack alongside all server JARs in one ZIP.

No live Minecraft server or Java/Bedrock clients were run. Biome registry startup, Geyser biome translation, ViaVersion handling, asynchronous chunk generation/population, natural village AI, real block physics, protection-plugin interactions and economy/NPC purchases need a separate staging world. No existing world, configuration, economy database or player data was modified. Install the updated Core and Terrain together for authoritative Harkonnen kit loot, and Essentials for seller purchases.

Publication requires authenticated DuneGit access. The current browser runtime cannot resume native credential state and Git has no credential helper. No credentials from chat are used; local commits and packages are not proof of a remote push.

Highland sietch placement: 441 default highland regions across positive and negative coordinates each have three deterministic, distinct sites at least 26 blocks apart inside the highland footprint. A population test also checks three actual chest placements and the highlands-only disable switch. The original open-desert placement formulas and spacing are unchanged; highland sites use separate cache identities. Compact cave geometry, real chunk population, cave connectivity, fluid physics and personal loot still need live staging.
