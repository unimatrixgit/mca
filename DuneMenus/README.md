# Imperial Navigator renderer

Paper 1.21.4 / Java 21, optional backend Floodgate. Install this plugin alongside the matching NativeMenus bridge in Core, Essentials, NPC and Lobby. `/navigator` routes to the current backend's player menu. `/dune` and the existing navigator item use the same player menu on game backends; the lobby Guild liaison opens the configured planet list.

Java receives illustrated written-book panels with ordinary book pagination and explicit review/action links. Bedrock receives real native SimpleForms with image buttons and separate detail/confirmation views. Server-created Inventory objects are display models only; no chest inventory is opened and no decorative menu stack enters a player's inventory.

Each view is bound to the player UUID, an opaque 256-bit callback token, exact model identity, current permission supplier, render revision and 30-second expiry. A state-changing callback is single-use and is queued to the main thread, then rechecks the player connection, exact current view, expiry and permissions. New screens, real inventory windows, cancellation and logout invalidate earlier views. Older Bedrock form responses and older book render commands cannot operate a later view. Existing economy escrow and moderation permission/snapshot checks remain authoritative.

The Java 1.21.4 client has no native dialog-screen API. Typewriter 0.8.0 remains available for authored conversations and quests; its stock navigation does not automatically convert every transactional GUI. DuneMenus supplies the dynamic bridge for existing transaction callbacks. The Java book may be closed client-side without a server close event; its opaque buttons remain bounded by the 30-second lease, single-use claim and backend stale-state checks.

The additive published resource pack must contain `dunerealms:imperial_menus` bitmap font. Java custom fonts do not transfer to Bedrock; form buttons use HTTPS DuneRealms asset images. Full Java/Bedrock client appearance and interaction acceptance must be checked after deployment; unit tests do not claim a client test.

A private optional discovery adapter calls the authenticated website endpoint with a current view HMAC proof and actual UUID. It never credits game currency or broadcasts a code. The website owns eligibility, the global winner and account-bound code redemption. Secrets are read from the existing Essentials website bridge file and are never logged.
