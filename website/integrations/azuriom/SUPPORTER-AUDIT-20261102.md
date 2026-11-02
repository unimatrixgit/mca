# Scheduled supporter audit — 2 November 2026 morning

Scope: current real-money catalogue and the superseded VIP roadmap. This is a bounded technical audit, not certification of every server feature or future package.

The current [official Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) allow cosmetics except capes/lookalikes and disallow gameplay entitlements that give competitive advantage or spoil other players' experience. DuneRealms therefore uses a stricter cosmetic-only supporter plan. Stronger Legendary+ equipment, selected combat enchantments and paid random power tokens are excluded from that plan.

## Actual findings and change

- One enabled package: VIP, EUR50, central website rank `vip`. No package/global console commands, money reward, gift-card balance, downloadable files or native role grant were configured.
- Current central VIP permissions cover account/game menus, personal banking, quests and support tickets; no staff permission or kit-quality override was present. The kit studio checks game quest unlocks and explicit design overrides, not the VIP rank.
- There were 28 pending native payments and no completed payments at the audit snapshot. No real transaction was made. Pending records were preserved, not cancelled or rewritten.
- The public VIP description still promised a future Legendary+ generator, enchantment selection and three random power codes. Replaced only that future-roadmap section with explicitly unimplemented cosmetic proposals and a no-paid-power statement. Existing current-rank wording, image, EUR50 price and all other package fields remain unchanged.
- Earned Dunemark Upgrade Tokens, existing kit designs, manually granted unlocks and game progression were not changed. They are separate from the real-money catalogue audited here.
- New rank prices and cosmetic rewards remain drafts. No new package, permission, voucher, cosmetic or gameplay entitlement was enabled.

## Verification and rollback

`update-vip-roadmap.php` ran as the application owner, locked package 1, guarded VIP/EUR50/central-rank identity, created a private exclusive-create backup, and updated only `description`. A second execution returned `Already applied` without writing.

Before/after comparisons found exactly one changed package field: `description`. All payment rows and payment-item rows retained their SHA-256 snapshot hashes:

- Payments: `65de2519de667405867d264a0388eaf4163dca990cda9449aa2224aa1f3c5f4d`.
- Payment items: `a025586d3d4e5fbe30d798246415ab82b7a84f59fae0916b699b84ed40f0a16c`.

The actual public `/store/shop/packages/1` response returned HTTP200 with the cosmetic roadmap and without the old power-code promise. The package slug `/packages/vip` is not a route (404); verification used the actual numeric route linked by the storefront. Existing shop role/route policy regression checks passed. No payment-provider transaction, game delivery test or server restart was needed or performed.

Private rollback snapshot: `/home/dunerealms/.config/vip-audit-20261102/vip-package-before.json` (mode0600 inside mode0700 directory). To reverse the copy change, restore only its description after checking the current description has not since been edited. Do not restore the whole package or any receipts. Public migration source intentionally excludes the private snapshot.

Next scheduled packet: cosmetic rank/reward design and artwork. This audit does not mark that packet complete.
