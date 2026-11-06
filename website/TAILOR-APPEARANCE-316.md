# Linked skin identity — native316 / public233965

This package delivers the Java/Bedrock identity and skin-metadata work consolidated from native77/78. The standing four-slot armour renderer, colour/trim presentation, viewport acceptance and client-side account-switch disposal belong to the separate native317 package. The existing Tailor mannequin has not been presented as a completed3Dskin preview.

## Authenticated CP contract

`GET /api/tailor/appearance` returns exactly `linked`, `model` (`default` or `slim`) and `skinUrl` (a same-origin URL or null). Only the current authenticated account's completed `site_game_links` record is used. UUID/XUID/account/design-owner query overrides are rejected. Site-uploaded portraits never supply a Minecraft skin. Missing, malformed or incomplete links return `linked:false`; an unavailable skin for a completed link returns `linked:true` with a null URL, allowing a neutral preview fallback.

The image URL is `/api/tailor/skin?v=<opaque-link-version>.<opaque-skin-version>`. It does not expose a game UUID or XUID. Both endpoints authenticate independently. The image version binds account ID, canonical UUID, link timestamp, validated PNG bytes and model. Another account or a previous link is rejected before remote resolution. Session and current-link authority are read again after asynchronous resolution, so logout/revocation or unlink/relink during a lookup cannot release the old result. Private responses use no-store, Vary:Cookie, nosniff and same-origin resource policy. The existing first-login gate also covers these routes.

The client in317 must clear previous textures immediately on logout/account switch, abort its pending image/metadata requests, ignore stale generations and dispose old renderers. An original request remains associated with its original cookie; a server cannot observe a different cookie subsequently installed in the browser. The new image endpoint rejects the old account's URL when requested with the new account's cookie. No client-side renderer/race acceptance is claimed by this backend package.

## Resolution and bounds

Java profiles use the fixed Mojang sessionserver endpoint. Profile ID and embedded profile ID, when present, must match the linked canonical UUID. Slim/default metadata is retained; unknown models and malformed texture data fail closed. A globally linked Bedrock session with a Java UUID follows that Java identity. No platform is inferred from a display-name prefix.

Floodgate UUIDs with zero high64bits are converted to unsigned decimal XUIDs with BigInt, including values beyond Number.MAX_SAFE_INTEGER. The fixed Geyser `/v2/skin/:xuid` API is used; an empty response means unavailable. Its converted texture ID and model must agree with the supplied texture data. Its documented `is_steve` flag maps to classic/default versus slim, as confirmed by the upstream uploader source. No skin-service API key or new login system is introduced.

Texture downloads allow only the exact textures.minecraft.net host and bounded hexadecimal texture path; legacy HTTP metadata is upgraded to HTTPS. Non-default ports, userinfo, query strings, fragments, arbitrary hosts and redirects are refused. No user cookies or credentials are forwarded. Metadata is limited to64KiB, PNG bodies to1MB while streaming, and each fetch has a4second timeout. PNG signature, chunk CRCs, header/dimensions, compressed scanline bounds and filters are checked. Standard64x64 and classic legacy64x32 are supported; animated PNG, unknown models/dimensions and legacy slim combinations fall back honestly. PNG parsing is deliberately a bounded skin-format validator, not a general-purpose image decoder.

The shared resolver coalesces concurrent lookups by canonical identity. Each process bounds entries(256), cached PNG bytes(8MiB), pending lookups(8) and uncached provider lookups(60/minute). Positive caches last5minutes for Java and1minute for Bedrock; failures are cached for30seconds. Authenticated preview requests are also capped at60/account/minute with a bounded window map. Existing public avatar/member/roster routes reuse the resolver and retain their previous fallback presentation.

## Actual staged checks

- 23 focused checks pass on local Node24:22API/identity/avatar checks and1built-asset regression. The22API checks also pass on deployed-runtime Node22.23.3 in an isolated temporary tree with synthetic accounts/UUIDs/PNG fixtures and mocked remote providers.
- The real CP router was tested with the existing first-login gate. Checks include Java regular/slim/legacy, exact high-XUID round trips, Bedrock empty/malformed responses, identity/model/texture mismatch, unsafe targets, redirects, type/size/CRC errors, timeouts, streaming cancellation, concurrency/cache bounds, opaque URL ownership, auth revocation and link changes during pending work. A model-only change invalidates the skin version even when PNG bytes are identical.
- Broader Node24 comparison: baseline457tests/29failures; candidate477tests/the identical29failing cases,20added passing tests. Existing failures remain unresolved; the full suite is not described as passing. Both broad runs use the same generated static Worker because this package does not change authored web assets.
- The original static build failed on an internal seal provenance text file. A narrowly scoped packaging fix excludes `assets/seals/*-provenance.txt`; unknown asset types still fail the build. The candidate build succeeds with422embedded assets. Its regression check retains the approved seal image and excludes the internal note. Authored menu files were restored after test builds; the rebuilt static bundle is test evidence, not a production deployment.

No game state, currency, receipts, purchases, entitlements or linked identity records are mutated. No third-party renderer/dependency is added. The three solid-colour PNG test fixtures were generated locally; no real player's skin is used in tests. Merge, guarded rollout, live mount verification and rollback references must be recorded in native316 before completion. This checkpoint is staged only.

## Primary references

- [Floodgate UUID/XUID format](https://geysermc.org/wiki/floodgate/faq/)
- [Geyser converted-skin endpoint and empty response](https://geysermc.org/wiki/api/api.geysermc.org/global-api-web-api-skin-controller-get-skin/)
- [Geyser classic/slim uploader mapping](https://github.com/GeyserMC/global_api/blob/dev/1.0.2/lib/global_api/skins/skin_uploader.ex)
- [PNG specification: chunks, integrity and interlace](https://www.w3.org/TR/png-3/)
