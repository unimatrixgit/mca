# Alpha 1.0.0.3 — Shop and community update

Released 2 November 2026. This is an Alpha patch; the full roadmap is not complete.

## Included

- Native website shop with central DuneRealms sign-in, Member/VIP website role selection, protected/staff-rank exclusion and scoped support/coupon permissions.
- Six selectable game servers connected using official AzLink 1.3.13. Verified Java/Bedrock recipient snapshots, UUID-aware online delivery and network-operator-only command configuration. The proxy is excluded.
- Owner-configured PayPal enabled. VIP product artwork/lore and storefront styling updated. The redundant introduction and full-window button removed.
- Separate Account/Staff Center portals with contextual menus and artwork. Website team ban-list access retained.
- Searchable public ban register: 50 records per page, latest 12 on the portal and a full-list link. Private evidence and unresolved identities stay private.
- Canonical shared navigation scripts/styles across website, Boards and Support; desktop/mobile menu fixes and matching Support controls.
- Readable news titles/dates, category artwork and safe article formatting with HTTPS signup links.
- Private Alpha Tester Signup intake in Support, seven custom application fields and a Support-home invitation. Existing recruitment article rewritten with plans, test priorities and signup links.

## Verification

- Seven focused Node checks passed: public data/pagination, CP grouping, SSO, safe news rendering and search visibility.
- Thirteen isolated PHP command-bridge checks passed, including Java/Bedrock identity, command safety, operator gating and online UUID matching.
- Read-only `version` commands executed through the native queue on all six live game servers; test queue drained.
- Native machine API: unsigned request 401, valid status 200, registration blocked 403.
- Browser verified server choices, VIP artwork, canonical community headers, article layout, seven signup fields and signup links.

## Limits and open work

No successful real-money PayPal capture was tested; the existing pending payment was preserved. No synthetic Alpha application was submitted. Upcoming VIP generator benefits remain explicitly planned. Outstanding game GUI work, native kit/quest progression and the separately staged ban-fleet import are not claimed complete in this patch.

Two earlier broad permission-suite assertions remain stale (retired game-node documentation and an overly strict web-node regex); focused checks for this patch pass. The legacy patch-note generator cannot validate the pre-existing 2026-10-28 metadata format, so this Alpha is published as a Git prerelease with these notes rather than rewriting historical records.

## Operation

Backups were taken before application/configuration changes. No world regeneration, inventory-engine upgrade or real financial transaction was performed. Work and scheduled production remain paused at the user's request after publication.
