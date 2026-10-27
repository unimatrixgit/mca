# Account control panel — 27 October 2026

## Published sites

- Standalone account site: https://cp.dunerealms.com/
- Embedded login, registration and account panel: https://www.dunerealms.com/account.html
- Monthly community support: https://www.dunerealms.com/donate.html
- Dutch naming explanation: https://www.dunerealms.com/wiki.html and the first manual chapter.
- Planet sigils: https://www.dunerealms.com/arrakis.html#route-sigils and the currency/travel manual chapter.
- Imperial artwork and Landsraad appointments: https://www.dunerealms.com/diplomacy.html

The panel is a separate Node application and HTTPS host, sharing the existing website account database. Sessions are host-specific; accounts and passwords are shared. This is not a hosting control panel.

## Accounts and rewards

- Registration cannot grant staff access. Passwords use salted scrypt; session cookies are Secure, HttpOnly and host-only.
- The owner staff account was provisioned separately. Its random credentials are in the private local `output/DuneRealms-admin-login.txt`; no password is included in this repository or public site. Change it after signing in.
- Account linking uses a private, single-use 128-bit code, expires after ten minutes, and must be entered with `/linkwebsite CODE` by the actual game player. The game bridge authenticates independently over HTTPS.
- Minecraft UUID is the permanent eligibility and claim key. The first verified link time survives unlinking, website account changes and player renames.
- Personal codes are generated only for goals reached after that first verified link. There is no personal-code backfill. Linking alone does not release currency.
- Staff create a monthly target and fixed equal reward. Verified totals can unlock it during that month, in Europe/Brussels time. Staff changes are audited. Targets and reward amounts cannot be casually edited after creation.
- Public codes remain equally available to all players, including players without a website link. Personal and public codes use one UUID/month claim record.
- `/communitycode CODE` prepares a receipt, then the game durably credits the balance and claim together. Only a game acknowledgement marks the website code used. Unacknowledged receipts retry after restart; used codes leave Available and appear in History.
- Codes have no expiry or shared redemption cap. Currency cannot be cashed out or redeemed for real-world value.
- No payment provider, live funding target or currency reward has been activated. Verified totals are manually recorded, not automatically inferred from a payment provider.

## Deployment

Website release: `/home/dunerealms/apps/dunerealms/releases/20261027-leto-zero`; `current` points there. Main application: `dunerealms-web`; account application: `dunerealms-account-panel`, localhost port 3003. Forgejo uses port 3001 and was left running.

The `cp` CNAME resolves to the existing host. Its certificate covers `cp.dunerealms.com`, `dunerealms.com` and `www.dunerealms.com`; public HTTPS and HTTP-to-HTTPS redirects were checked without disabling certificate verification. The cp vhost shares the CloudPanel-managed certificate. Its ACME location uses the main site's document root. The panel permits framing only by the two main-site origins; the main site explicitly permits the cp frame.

The owner login, staff authorization, forged-header rejection, empty reward state, logout, embedded rendering and registration-mode UI were verified live. No fake campaign, test donation or live currency credit was created.

The gameplay backend remains Paper 1.21.4. Compatible DuneEss, DuneCore and DuneNPC binaries come from the separate `dunemarks-live-1.21.4` checkout. It was backed up and restarted with zero players. The proxy and lobby stayed running. The account bridge secret is kept in operator-only configuration, never source or public assets.

Resource pack: `/downloads/Dunemarks-ResourcePack-1.21.4.zip`, SHA1 `f9470108e9cff93231968e6b8b2d99293e62fce2`. Pack acceptance is optional; Java clients declining it see material fallbacks. Bedrock texture rendering and in-client menu appearance have not been verified.

The active account/reward bridge is installed on the gameplay backend. Do not deploy independent local currency ledgers on multiple redemption backends: concurrent cross-backend redemption requires the shared PostgreSQL ledger. Its existing economy/market connection warning is separate from the website account database.

The main checkout also contains the 1.21.8 source implementation, retaining native menu support. Do not deploy its binaries onto Paper 1.21.4. The source is included in the DuneGit inventory/account integration. Server publication still uses the saved deployment packages; new inventory runtime activation is documented separately in INVENTORY-EXCHANGE-INTEGRATION.md.

## Artwork and wording

Public houses: Aerdrecht, Harkveen, Korenholt, Oordwijk, Vrijduin. Travel: Vaerkring; exchange: Koopkring; water sellers: Welvaarders. The Emperor's spice and water offices are presented as **Landsraad appointments**, with Specerhuis and Welvaart House office labels. Existing faction/world IDs, command arguments, ballots and balances remain compatible. Old binaries, imported lobby/world text and technical identifiers may still contain earlier names; this is not a claim of a full saved-world migration.

The five route sigils were authored as SVGs, with angular letter rings encoding the Dutch-inspired destination names, retaining their original-name roots. GUI textures are derived from those same SVGs. The imperial banner was generated with the built-in image tool; its exact prompt is saved beside the artwork documentation. The rights disclaimer does not claim that renaming or attribution clears underlying IP.

## Tomorrow's checks

1. Open the private login file, sign in on cp, and change the password in Account security.
2. Generate a private link command, enter it on the gameplay backend, and refresh the panel. Check the exact Minecraft UUID.
3. Confirm no old personal rewards appear. No rewards should exist before an actual funded goal is configured and reached.
4. Accept the Java resource pack; open travel and Landsraad menus. Check the sigils, office seals, back button and unchanged travel/appointment rules.
5. Once a real goal is met, claim its reward, confirm the balance and History entry, then retry both its personal and public codes. Neither may issue a second credit.

Automated checks: 54 website tests; 27 compatible DuneEss/DuneCore checks; both target-version game builds; local ledger restart, duplicate-receipt, failed-write and acknowledgement tests. Client interaction remains a manual check, not a completed test.

## Vaerkring note series and theme update

Five full front/reverse designs and transparent GUI icons: 1 (Stilgar), 5 (Aliah), 100 (Mira Vesk), 1,000 (Ghanima), 5,000 (Leto and an imagined victory over House Harkonnen). Every note reads Vaerkring Reserve and uses the newly designed crown/sun/dunes imperial seal. The 0-Dunemark Leto II search tribute is website/design-only: no game item model, GUI denomination, balance value or resource-pack entry. It is explicitly commemorative and has no cash value.

The character editions are an explicit operator-requested exception to the earlier original-character-only artwork policy. The wiki and IP risk review disclose this; they are not cleared merchandise. Exact built-in imagegen prompts and PNG masters are in docs/artwork/dunemarks/; production WebP and 64px/128px transparent icons are in website/web/assets/.

Main account page, standalone control panel, login/register, dashboard and staff forms share the site artwork, fonts, parchment and gold/teal theme. Full-note artwork uses natural image proportions; the preview icon selector is scoped separately at 64px to prevent accidental full-column enlargement. Browser measurements confirmed all five previews are 64 by 64.

The Dutch explanation appears both on wiki.html#dutch-inspired-names and the first manual chapter. It describes Landsraad's Dutch-language connection and broader Germanic parallels, rather than claiming an exclusively Dutch origin. It cites a linguistic study and retains the no-licence/no-endorsement wording.

Latest gameplay binaries: DuneEss SHA256 bec211ed8807d841cc997e1fce264af497ceea6718fda563b62bf612de9f0dad; DuneCore SHA256 8df87b13122a28a932913cc6e11b1fb5a1ce74efbf180f1bc54dcd04d613701b. Startup completed in 35.524 seconds without ERROR/Exception entries. Proxy, lobby and gameplay services were active after deployment. The public resource-pack download SHA1 matches the configured hash above. No zero-note game changes were deployed.
