# Ticket361 — unified community member profiles

All `/people/username` pages now share the expanded member layout. Basic identity and existing honorary citations remain public; additional public community details require the central Account Center session. No shop block and no separate ranks box: current role/team recognition is represented by applicable seals.

## Public information included
- Overview, public bio/info/social links and properly projected custom image or two-layer Minecraft face avatar, without exposing a game UUID in the new API.
- Centred full-width medal groups: staff, departments, applicable earned community badges, streamer and current unexpired political office seals. Existing tester eligibility is reused; staff do not acquire tester medals simply by being staff. Generated25-closure department variants reuse existing public assets.
- MyBB public fields through the verified website-to-forum mapping: username/title, joined, posts, threads, reputation and last visit only when native privacy permits. No email, messages, private posts or activity page details.
- Verified public staff closure totals only, never case IDs, private requests, ticket content or customer identity. Nonstaff have an honest no-public-staff-statistics state.
- Active public clan name/tag/description, membership role/date and member count. No bank, requests or private clan records.
- Explicit quest-statistics placeholder with null counters, not invented zeroes.

## Validation and rollout
25 focused Node tests passed across member profiles, central session/CORS, memorial locks, account/profile and staff regressions. Tests cover anonymous/wrong-origin/unsupported-method/disabled/missing targets, current role/badge revocation, exact native mapping, privacy-field absence, invisible-last-visit privacy, clan/placeholder, avatar mode and UUID exclusion. Read-only live HTTP checks verified current uid4 mapping, real forum data,7closure total, House Unimatrix, placeholder, no shop/private fields. Browser1280/390px: no horizontal overflow, full-width centred medals, all image resources loaded, four themed semantic detail tables. Screenshot evidence in output/profile-task-20261104. A later final avatar fixture is included in the25total.

Backups: `/root/ticket361-before-20261104`;8 runtime files deployed, no new database data/migrations, no financial transaction, game restart, permission grant or message publication. Restore these files coherently and restart only the web/account-panel services to roll back. Native platform data remains unchanged.
