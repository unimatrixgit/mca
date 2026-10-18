# CHOAM Exchange — handoff notes (18 October 2026)

## Goal
Match the existing Project Dune website at https://project-dune.bonnystoat9.chatgpt.site rather than a new design. Preserve the original horizontal illustrated menu, PROJECT DUNE wordmark, Cormorant Garamond / Manrope typography, dark teal (#10201f / #142a28), gold (#d5ab63), parchment (#f5eddd), and Minecraft Arrakis imagery. Use the latest approved exchange mockup as inspiration only.

## Current repository
Source: https://github.com/unimatrixgit/mcdune
- `website/web/index.html` and `website/web/styles.css`: actual website reference.
- `website/web/exchange.html`: redesigned CHOAM hero, world tiles, search, filters, sidebar, demo banner, listing grid. Commit `b2a82f92efc00ef4251684332b8734b6cf2d7f91`.
- `website/web/exchange-market.js`: client-side filters, pagination, live API attempt, and eight sample listings if API unavailable. Commit `86591e0af80ea486418bae38ae33a8b36f801ddf`.
- `website/web/exchange-market.css`: existing styling; **verify latest CSS commit before merge**. The HTML redesign changed selectors and requires matching styles for `.exchange-content`, `.category-links`, `.market-heading`, `.demo-banner`, `.choam-hero-foot`, and six world tiles.
- `website/worker/exchange-api.mjs`: read-only proxy; requires secure `EXCHANGE_LISTINGS_URL` and `EXCHANGE_READ_TOKEN` environment variables. No live SQL marketplace is connected.
- `website/worker/runtime.mjs`: routes `/api/exchange/*` to exchange API.

## Work needed before deployment
1. Check `exchange-market.css` against latest `exchange.html` and finish responsive desktop/mobile styles.
2. In `exchange-market.js`, world tile click handling must also handle `data-planet="all"`. Category sidebar buttons `data-category` must set the category selector and re-render. Avoid treating mock listing IDs as real purchases.
3. Mark all mock listings DEMO / non-purchasable. Do not imply the database is connected.
4. Test: `cd website && node scripts/build.mjs && node --test tests/*.test.mjs`. Inspect the built page in a browser, especially mobile nav, image paths and filters.
5. Commit any fixes, review/merge via the team's preferred workflow. Changes committed directly to `main` are not a fork or PR.
6. Publishing is separate from GitHub: `.openai/hosting.json` and `website/README.md` describe the Sites workflow. The GitHub Actions build job only builds/tests/uploads an artifact; it does **not** deploy to the chatgpt.site domain. Publish via the authorized Sites hosting workflow, then verify https://project-dune.bonnystoat9.chatgpt.site/exchange.html.
7. Later: securely connect game SQL marketplace to a read-only API; no credentials in browser code. Test the DuneEss SQL exchange separately before allowing real transactions.

## Scope
The current demo contains eight representative items, not every Minecraft item. A complete Minecraft 1.21.4 catalog and Dune custom item definitions require separate work. No temporary database has been deployed or tested. Do not claim the site is live or the server database is connected until verified.
