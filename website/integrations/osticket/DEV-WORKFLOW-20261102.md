# Private Dev workflow

The native osTicket Dev department/team and non-public help topic route development requests into real support tickets. `/dev-tasks.php` is an authenticated Dev Center with separate Overview, Development Queue, Approvals, New Request and Archive views. Task 347 / #712117 requested the dedicated submenu and professional layout.

## Access and decisions

- Ordinary members are denied server-side. Staff and VIP accounts may submit and see their own requests. The Dev team may read the full register.
- Dev membership follows `web_dev` or protected leadership (`owners`, `corestaff`, `web_net_admin`, `network_admin`). The private department additionally requires `web.tickets.dev`; it is not granted by generic support-administrator access.
- Review authority requires the verified central username AND current rank: `unimatrix999` / `owners`, or `grmacken` / `web_net_admin`. Their submissions are automatically approved. Dev cannot approve its own new requests.
- Reviewers and the central `dev` / `web_dev` account may change Low, Medium and High priorities and edit approved execution briefs. Approved Bugs precede normal work; approval is still required.
- Denial closes the native ticket. Approval keeps/reopens it. Completion requires an approved task, a completion report and a successful native close before the registry is marked completed.
- Original `prompt_json` is retained; `execution_spec` is the separate implementation brief. Decisions, priority changes and brief edits are recorded in `dune_dev_task_events`.
- Submission tokens and per-request locks prevent repeated normal submissions; identity/ranks never come from form fields. SQL values are bound and output is escaped. Native public-topic submission redirects to this gate.

## Backlog consolidation

The owner explicitly requested immediate import, pre-approval of the existing backlog, and then reduction of the number of tickets. 248 source requests became 35 coherent work packages. 246 originals were closed as **consolidated**, not completed; two obsolete proposals were closed as **superseded** (the paid-power VIP proposal and the already-delivered Account/Staff split). Original prompts and native history remain intact. `dune_dev_task_links` links each merged original to its work package. Later user submissions remain independent and are not silently merged or approved.

The 172 individual form inventory entries remain covered through linked test packages, not discarded. Test inventory includes code-defined form construction sites and native view templates; resolving the actual served route and confirming whether a template is used is part of each validation task. No test pass is inferred from inventory discovery.

## Accounts and source workflow

The owner subsequently reset website access assignments: unimatrix999=Owner, grmacken=Main Network Administrator, richwjr/dunerealms=Network Staff, Dev=Dev Team; other accounts=User. Secondary website-access assignments were removed after a private backup. Game/political metadata and paid receipts were preserved. Leadership inherits operational permissions instead of carrying every lower website rank separately.

Dev has a reserved service UUID in `site_account_policies` with a website link exemption. There is deliberately **no** `site_game_links` row for Dev: no player inventory, balance or reward identity is fabricated. Account credentials and the Git token exist only in private operator storage.

New source work uses the `dev` DuneGit account and fork `https://www.dunerealms.com/git/dev/MC`, task branches and merge requests to `unimatrix/MC`. The external AI/model project remains cancelled. Both existing daily runs now inspect the current queue and discussion before selecting 1–3 approved tickets, carry unfinished work, prepare an execution brief, and close only after real evidence. Times remain 09:00/19:00 Europe/Brussels; Sunday releases use the evening run.

## Operations and verification

Installers are CLI-only. The account provisioning and one-off leadership reset scripts require explicit operator flags; the reset has a completion marker to prevent later staff decisions being overwritten. Do not add them to startup or daily automation.

Private recovery material: `/root/dev-team-before-20261102` (source/bundle), `/home/dunerealms/.config/dev-team-20261102` (original rows, manifests, ticket maps and consolidation history). Keep these out of Git. Restore individual rows/files after review, never overwrite concurrent ticket activity with a blanket old dump.

Checked: 17 focused Node tests; PHP role/decision/validation tests; authenticated live page separation, CSRF rejection, empty-field errors, Dev approval denial, priority update and execution brief save. Browser checks cover live navigation and mobile 390px without horizontal overflow or missing artwork. Member/VIP policy is covered by isolated role tests; no real payment or destructive production form submission was performed.

Original imperial seal artwork and prompt provenance are in `website/artwork/dev-team/README.md`; matching sizes are in `website/web/assets/seals/`. The overview reuses this artwork in a themed header, submenu and status panels.
