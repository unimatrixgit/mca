# Website update handoff

Prepared 26 October 2026 from self-hosted DuneGit `unimatrix/MC`, revision `73e1bc8b75608b77280c6d957bf590be3f3ed8b6`.

Status updated 27 October 2026: deployed and verified on https://www.dunerealms.com. Active release: /home/dunerealms/apps/dunerealms/releases/20261027-manual-disclaimer. The existing PM2 website process was restarted after an atomic release switch. The preceding releases remain available for rollback. The source changes have not been pushed to DuneGit.

The owner requested `dunerealms.com` as the visible brand and retained `spice`. The public setting labels and supporting story are original proposals; see `PUBLIC-NAMES.json`, `STORYLINE.md` and `IP-RISK-REVIEW.md` for mappings and limits. The old journal is removed, including its build inputs. The comparison wiki intentionally identifies older names for context. The notice includes `unimatrix@dunerealms.com`; no email was sent.

## Validation

- Website build succeeds: 146 embedded assets.
- All 47 website tests pass, including the new removal, notice, compatibility-label and command-copy checks.
- Twenty web HTML pages have no broken local file/asset links. The sign-in route is an application route, not a static file.
- Twenty web pages plus the separately served login page each receive one visible independence notice.
- Retired index, essay, script, stylesheet and data URLs return 404 in the rebuilt Worker for GET and HEAD.
- New public planet names work in market search; legacy live feed values remain accepted and display the new labels.
- Local browser checks covered the wiki layout, renamed branding and factions. Saved wiki preview: `output/dunerealms-wiki-preview.png` in the parent workspace.
- Repeated build was checked for stable tracked output.
- Plugin binaries were not changed or built; this is a website and supporting-story update.

## Applying the prepared source

Review the patch against the stated base revision. If the server repository has advanced, merge the patch and resolve any overlap before deployment. The patch records deleted files; copying only changed files over an old checkout is insufficient.

Use a fresh release directory for the website. Install the existing exact locked dependencies and build with the existing website scripts. The source supports both a Worker bundle and the repository's custom Node/MySQL host. Preserve the current private environment, database, login configuration, monitor configuration and Forgejo reverse proxy; no credentials are included in this handoff.

Use the operator's existing release-switch procedure for the actual host. No deployment target or credentials have been inferred. Recheck health, sign-in, forums, tickets, exchange, world status and `/git/` after the release. Confirm that old journal routes are absent and purge any cached copies at the serving layer. A successful local build is not deployment confirmation.

The existing game commands, plugin filenames, world IDs, feed contracts and saved data retain their old identifiers. Renaming those requires a separate game-side compatibility migration, not a website-only release. Historical PDFs and releases remain in repository history and external distribution; direct legacy PDF links have been replaced with the current web manual in this website.

## Published follow-up

The comparison/disclaimer is the first manual chapter, `world-and-disclaimer`, with 58 chapters total. Its standalone page remains at `/wiki.html`, linked from the site-wide disclaimer notice; the former World wiki main-menu entry is removed.

Operator email is absent from public HTML and static browser assets. The contact button uses self-hosted ALTCHA proof-of-work verification and a Node endpoint. Only a valid, unexpired, single-use proof returns the address. Origin checks, bounded challenge storage and bounded payloads are enforced. This raises scraping effort; it does not guarantee freedom from spam. The contact endpoint is part of the custom Node host, not the standalone Worker deployment.

Verified live: homepage, wiki, manual, manual data, login, forums, tickets, exchange, world status, diplomacy, database health and Git API; old journal route returns 404. Valid CAPTCHA reveal returns 200 and replay returns 403. No production test accounts, messages, tickets or market orders were created. Existing schema, authentication code and Git proxy matched the deployed baseline. The temporary loopback preflight process was stopped.
