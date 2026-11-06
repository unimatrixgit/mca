# Moderation Operations form validation

Parent 332 / public 216396 consolidates archived originals 138, 146, 147 and 148.
This checklist follows the served replacement for each original inventory entry.
No real ban, console action, evidence upload, deletion, payment or game-state change
is part of the test workflow.

| Original form | Actual served route and endpoint | Automated and live read-only result | Remaining acceptance |
| --- | --- | --- | --- |
| 138: `web/bans.html`, `ban-search` | `https://www.dunerealms.com/bans.html`; GET `/api/public/ban-list` through `web/public-bans.js` | Both served form and script verified. Search is public/read-only; no CSRF token or privileged role is required. Parameter binding, whitespace, 80-character cap, Unicode, literal markup/injection-like text, invalid/extreme pages, 50-row bound and private-field exclusion pass. Actual GET responses for invalid pages and Unicode/markup stay bounded and expose public fields only. No public source change is needed. | Browser keyboard, pagination, mobile and network/error presentation |
| 146: `servers.html`, `command-form` | `https://cp.dunerealms.com/servers.html`; POST `/api/servers/action` through `servers.js` | Served source verified; anonymous endpoint 401. Empty/whitespace, ASCII 1–240, slash/control/Unicode rejection, actor derivation, exact origin, fixed targets, operator permissions and provider errors checked. Cancel, HTTP/non-JSON/network/revoked/missing-ack failures retain input. Accepted simulated response clears it; busy form freezes command/server and blocks duplicates. | Browser keyboard/mobile and visible errors after merge/rollout |
| 147: `ban-evidence.mjs`, dynamic form | CP `/?banId=<UUID>#ban-evidence`; `/api/ban-evidence` and private file endpoint | Served module verified; anonymous API 401. Title/notes/URL/file bounds, whitespace, Unicode, HTTPS/no credentials, magic-byte type, filenames, exact 5 MB, 7 MB body bound, private downloads, authorisation/origin, retained-ban append-only writes and actor attribution checked. Retries retain the same submission ID. Audit failure rolls back both tables in real InnoDB temporary-table QA. Old account/bootstrap/file-read/results cannot restore or submit old content. | Browser keyboard/mobile and visible upload/error feedback after merge/rollout |
| 148: `ban-register.mjs`, unban form | CP `/#bans`, Details / Unban; POST `/api/ban-register` | Served module verified; anonymous API 401. Trimmed single-line 5–500 reason, IDs, Unicode/literal markup, current permission/origin and exact native confirmation checked. Stale/replaced ban and incomplete/wrong acknowledgements never record success. HTML/non-JSON/network/rejected/missing-ack errors retain the reason. Busy guards freeze the reason and prevent duplicate submits; old session responses cannot restore controls or success. | Browser keyboard/mobile and visible errors after merge/rollout |

## Confirmed findings and fixes

* **Bug 377:** the command form unconditionally cleared its field after `act()`, including
  cancellation and failed/unconfirmed requests. Reproduced against the original script
  in an isolated runtime (`list` + cancelled confirmation became an empty field).
  It now clears only after explicit `ok:true` acceptance. It does not automatically retry.
* **Bug 378:** a late initial auth/session response restored privileged moderation UI
  after a newer logout/session event. Reproduced for both original modules. The bootstrap
  now respects the session/request generation; pending saves and file reads retain their
  own attempt token so an old completion cannot re-enable a newer account's form.
  Backend permissions remain authoritative and unchanged.
* **Bug 379:** the unban form exposed a raw JSON parser exception for a gateway HTML
  response. Reproduced against the original module using a synthetic HTTP 502 response.
  A shared response reader now provides a safe service message and preserves the form.

The native Bug records retain their normal protected Owner approval state. The fixes
are implemented under the already-approved parent 332 scope; no approval flags or
reviewer identity are rewritten. Track their eventual review and closure separately.

* **Bug 380:** Caladan and Sigma Draconis IV were supported by the backend but absent
  from the served console selector and frontend player-count mapping. Both existing
  targets now appear with their canonical IDs and status keys. No target or permission
  is added to the backend.
* **Bug 381:** an older Arrakis log response could overwrite a newly selected proxy log
  and restore the proxy command form. Reproduced with responses deliberately reversed.
  Log requests now have a sequence/current-selection fence, clear the previous log while
  loading and apply the proxy restriction immediately, even when its logs fail. Old
  successes and errors cannot replace the selected server's log/help/error state.

## Tests and limits

Fifty-three focused Node checks pass across:

* `server-console-form.test.mjs`, `moderation-forms.test.mjs` and their isolated DOM fixture;
* `server-control.test.mjs`, `ban-register-unban.test.mjs`, `ban-evidence.test.mjs`;
* `evidence-media.test.mjs` and `public-ban-list.test.mjs`.

The opt-in `ban-evidence-mysql.integration.mjs` additionally passes 26 checks on real
InnoDB transactions with connection-local temporary copies of the three moderation
tables. It checks replay, changed-payload/actor conflicts, origin/current writer denial,
rollback after the evidence insert but before its audit insert, retry, exact 5 MB storage,
filename/type/hash and private download size/headers. It changes zero production bans,
accounts or evidence rows. Temporary tables do not exercise foreign-key installation;
this change introduces no schema migration.

Browser acceptance is still pending: the browser tool became stuck on the original
local preview's native confirmation dialog. Automated DOM acceptance is not a substitute
for the remaining visible desktop/mobile/keyboard check. Do not close parent 332 or
claim deployment solely from these tests.

## Scoped rollout and rollback

After merge and browser acceptance, baseline-check and back up the changed CP public
files and `control-panel/server.mjs`. Its only change is allowlisting the new public
`moderation-form-response.mjs` module. Restart the account panel for that allowlist;
verify the new module and served changed files, anonymous denials and online service.
Do not rebuild the website asset bundle, change approved portal art or restart game
services. The public ban search code and all backend authorisation/game-relay code stay
unchanged. Rollback restores those code files and restarts the account panel; no player,
ban, evidence, payment, receipt or other data rollback is required.
