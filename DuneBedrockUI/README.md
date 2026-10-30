# DuneRealms Bedrock Navigator — staging candidate

This is the Bedrock presentation candidate for the existing player, clan, economy and staff menus. It adds no gameplay or permissions. It is **not deployed** and has **not been visually validated in a Bedrock client**.

## Design and native contract

A dark emerald panel with gold border, unchanged imperial portal artwork, large 48-pixel card buttons and a scrollable record area replaces the normal long-form appearance only for DuneRealms-tagged screens. Hover/pressed states, native form collection details, and `button.form_button_click` are retained. The native collection order is used directly; the skin adds no pseudo-buttons or command data. Back/Close are the real server-provided choices. Native form cancellation remains available through the screen's Escape/back/controller mapping.

Unrelated long forms retain the normal interface. Custom/modal forms are not intercepted. Width/height are capped at 420×340 logical pixels and bounded to viewport minus16, so a smaller phone gets a scrollable narrower panel rather than a desktop-sized overlay. Keyboard/controller and touch still require actual client testing.

## Exact Java change required (root owns this file)

In `DuneMenus`'s private Bedrock class, prefix **both** SimpleForm titles:

- overview: `.title("DRUI|" + view.title)`
- detail: `.title("DRUI|" + card.label)`

Do not prefix the Java dialog title, change choices or button indices, or create a second callback implementation. The pack strips `DRUI|` from its displayed heading. It uses the reserved marker as a title router; avoid this literal in unrelated plugin form titles. A client without the skin will see the marker but still has a fully functional native form.

## Package

Run `python build_pack.py`, then `python -m unittest discover -s . -q` in this directory. Output is `dist/DuneRealms-Imperial-Navigator-Bedrock-v1.mcpack`.

The pack uses its own stable UUIDs, distinct from Imperial Items. It contains no item definitions and can be stacked alongside the current item pack. For Geyser staging, add this candidate to the resource-pack stack without deleting the item pack or changing Floodgate mappings/identity keys. Root should manage the production deployment and version bump after client validation.

Minimum engine version is **1.21.100**. The native dynamic-button factory and binding names were checked against Mojang/Microsoft's vanilla resource-pack file at the pinned 1.21.100 mirror revision below, and also against the current26.50 version. Do not lower the minimum or assert older client support without a separate test.

## Validation performed

Eight automated package/contract checks pass: archive integrity/JSON, distinct manifest IDs, additive title routing/default fallback, native index/input contract, all owned references/textures, unchanged original art hashes, touch-row/focus/scroll structure, valid code-drawn PNG primitive dimensions. These checks do not simulate Minecraft's UI engine. No screenshot was fabricated and no actual client render, focus movement, resource-pack download, or touch hit test has been claimed.

Required client acceptance before deployment: current supported Bedrock version on phone and desktop/controller; player/clan/economy/staff overview and confirmation screens; long labels and long records; two pages; Back/Close/cancel; URL icon load failure; existing item textures; unrelated plugin simple/custom forms; decline/no-pack fallback; double click/stale view rejection by the existing server callback gates.

## Asset and source provenance

`textures/dunerealms/ui/imperial-portal.png` and five faction heraldic images are byte-for-byte copies from the existing DuneResourcePack artwork. Gold/emerald panel/card textures are original code-drawn 16px 9-slice UI primitives; existing artwork was not edited.

Native UI contract, authored by Mojang/Microsoft, examined for compatibility (no wholesale vanilla file bundled):

- https://github.com/ZtechNetwork/MCBVanillaResourcePack/blob/7a9c12d0e8680f3a2415bc87577cea99a73c254d/ui/server_form.json (1.21.100)
- https://github.com/ZtechNetwork/MCBVanillaResourcePack/blob/61bd205b092107a6fd42fece413764e63c7b401d/ui/server_form.json (26.50)

The UI hook/namespace/card layout in this pack is original DuneRealms code using the engine's exposed form bindings and input names. Mojang/Microsoft retain ownership of the native game UI referenced by inherited controls.
