# Dune Resource Pack

Current fleet note, 6 November 2026: the six Paper backends use the optional **Imperial GUI v7** archive at `https://www.dunerealms.com/downloads/DuneRealms-Imperial-GUI-1.21.8-v7.zip`, SHA1 `6222875ddd969bebbeefc5b607051fdf61b1a14e`. Its full HTTP download and ZIP/JSON integrity are verified; actual client rendering remains pending. See [task 326](../docs/menu-acceptance-326/README.md) for exact hashes and acceptance boundaries. The older texture/history sections below describe their original releases and must not be used to downgrade the current server pack.

Client-side resource pack for Minecraft Java 1.21.4 servers. Works with vanilla and Fabric clients running Minecraft 1.21.4; Fabric Loader does not require a special mod wrapper for standard resource packs.

- Gives the visible Sniffer sandworm a desert-tan, segmented hide with ivory toothlike head details.
- Gives zombie raiders a haunted Fremen stillsuit texture.
- Recolors suspicious sand to a bright spice-gold so deposits are easier to spot.

On a Fabric client, copy `DuneResourcePack.zip` into `.minecraft/resourcepacks`, launch the Fabric profile for Minecraft 1.21.4, then enable **Dune Resource Pack** in Options → Resource Packs. Do not put the ZIP in the `mods` folder. For automatic server delivery, host the ZIP at a direct-download URL and set `resource-pack` in `server.properties` for each backend. Players must accept it. Reaccept or reload the updated pack after upgrading. DuneCore 1.0.5 spawns a real Sniffer body with an invisible Husk providing native zombie AI. The Sniffer texture keeps the vanilla model's UV layout; the pack does not create a custom worm mesh. The legacy Husk texture remains for compatibility.

DuneCore 1.0.7 adds an Arrakis smuggler texture for the vanilla wandering trader model.
Enable the updated pack to see sand-tan robes, a face covering, amber trim and leather gear.
This texture applies to all wandering traders client-side; smuggling missions only apply to Arrakis traders.

## Vaerkring Reserve GUI series (2026-10-27)

Item-model keys: dunerealms:dunemark_1, dunemark_5, dunemark_100, dunemark_1000 and dunemark_5000. Each has its own transparent 128px texture and item/model definition. The legacy dunerealms:dunemark_note remains a 100-note visual alias. These are presentation-only; currency stays in the server ledger, and GUI clicks do not issue bearer notes. Wallet art selects the largest supported denomination not exceeding the balance (minimum visual fallback 1).

The 0-Dunemark Leto II search tribute is deliberately absent from this pack and the game denomination list. It is website/design artwork only. The five planet textures now use letter rings for Arrakduin, Kaladam, Giedermark, Kaiterhof and Sigmadraken IV; existing internal model IDs remain stable.

Published pack SHA1: f9470108e9cff93231968e6b8b2d99293e62fce2. Java 1.21.4 item-model format; in-client appearance and Bedrock conversion still require a client check. Character fan art is not represented as licensed merchandise.
