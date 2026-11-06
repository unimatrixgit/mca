# Java resource pack and native dialog compatibility

Historical audit, 30 October 2026. Its v4/native-dialog findings are preserved below. The current release uses graphical Java inventory cards and pack v7; follow [the 6 November fleet/acceptance record](../docs/menu-acceptance-326/README.md). Do not apply the historical "keep v4" instructions to the current fleet.

Audit date: 2026-10-30. Target: Java client/Paper1.21.8, new DuneMenus dialog renderer. No production changes made by this audit.

## Result: keep v4

The existing published `DuneRealms-Imperial-GUI-1.21.4-v4.zip` already supports resource formats46 through64. Mojang's official1.21.8 client JAR `version.json` declares resource format64, data format81, protocol772 and Java21. Therefore a compatibility-only v5 is unnecessary; retaining v4 preserves every original texture/item/font byte and avoids a pointless client redownload.

- Public URL: `https://www.dunerealms.com/downloads/DuneRealms-Imperial-GUI-1.21.4-v4.zip`
- SHA-1: `9c7311ff078851686e0ede1252dba73b25d27c9d`
- SHA-256: `9df29dc6ce8f0b507d27babfec4c2762f3983585a70d074b2638bacfdc478509`
-84 unique archive entries;48 strict JSON files parsed;23 item-model definitions;7 bitmap-font providers.
- All custom item model and texture references, font image references, provider dimensions/divisibility and ascent/height bounds validate.
-64 files shared with current `DuneResourcePack/` source are byte-identical. No original file was replaced.

## Safe server configuration

Keep the existing `resource-pack` URL and `resource-pack-sha1` above on the upgraded backends. Keep existing resource-pack UUID/prompt/require policy. Do not change Floodgate keys, Geyser item mappings or Bedrock item resource-pack identity for this Java engine update. The optional Bedrock Navigator skin has its own independent pack UUID and staging instructions under `DuneBedrockUI/`.

Clients newer than1.21.8 are outside this audited format interval. Do not increase supported_formats blindly: a1.21.9+ client needs a separate compatibility audit.

## Native dialog texture hooks

Official1.21.8 client assets contain only three dialog-specific sprite textures:

- `assets/minecraft/textures/gui/sprites/dialog/warning_button.png`
- `.../warning_button_highlighted.png`
- `.../warning_button_disabled.png`

These are Minecraft's custom-content warning affordance and should remain visible/recognisable. Ordinary dialog buttons use the shared widget sprites; generic menu/popup/background sprites also exist. Those are **global** assets, not DuneRealms-only window skins. Replacing them would reskin other game menus too. No invented `dialog/background.png` or Dune-specific native window hook was added. The supported per-menu illustration mechanism already used by DuneMenus is the custom bitmap font in body text, plus native item body elements if needed. Native dialog positions/shapes stay constrained by the client API.

## Renderer spacing follow-up

Existing font display heights: ordinary menu icons32px, faction crests40px, portal crest48px. Preserve the artwork/fonts and reserve sufficient body height in the renderer rather than stretching/replacing them. Six ordinary text lines (54 logical pixels at the default9px line height) are a conservative reservation for a48px crest; five lines for40px. Actual glyph baseline/wrapping still requires client validation.

## Reproducible evidence

Workspace scripts and reports under `output/bedrock-gui-alpha/`:

- `audit-java-v4.py`: archive/JSON/custom-reference/font bounds check; report `java-audit/java-v4-audit.json`.
- `audit-official-client.py`: downloads Mojang version manifest and exact1.21.8 client, verifies published SHA-1, lists native GUI assets; reports `java-audit/version.json` and `native-gui-assets.json`.
- `audit-source-and-native.py`: compares source bytes and prints official embedded version/scaling metadata.

Mojang's official metadata: https://piston-meta.mojang.com/mc/game/version_manifest_v2.json . Official release notes: https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-8 . No actual client screenshot/render test was performed by this structural audit.

Native hook verification also examined Mojang's published1.21.8 client mappings and compiled class constants: DialogControlSet (gfj) constructs ordinary Button builders (fxo); AbstractButton (fxe) uses widget/button, widget/button_disabled, widget/button_highlighted. DialogScreen (gfl) separately names only the three dialog/warning_button sprites. Evidence saved as java-audit/client-mappings.txt, dialog-controls-bytecode.txt and dialog-bytecode.txt. No client code is included in the resource pack.
