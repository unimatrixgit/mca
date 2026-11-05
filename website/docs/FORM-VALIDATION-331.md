# Cross-site forms register — ticket331, slice1

This is an authored-source discovery checkpoint, not a completed cross-site validation audit. Ticket331 stays approved and open. Archived source137 remains consolidated; it must not be reopened. Existing domain tickets332–344 provide the consolidated QA ownership, superseding one new ticket per discovered markup form.

## Reproducible inventory

Run `node website/scripts/form-audit-register.mjs` to generate `website/docs/form-audit-register.json`; use `--check` to detect drift. The generator scans allowlisted authored HTML/PHP/JavaScript roots, finds literal form markup and dynamic form construction candidates, records source locations and stable keys, and maps each to its existing QA domain ticket. Test fixtures, build output, downloads, synced references and symlinks are excluded. Input values, hidden tokens, submitted data, query strings and URL credentials are not recorded.

The first register has33candidates:18literal forms and15dynamic constructors. These are source candidates, not33confirmed distinct live forms. Every validation dimension remains`not-run`; runtime status remains`unverified`. No passed test is inferred from the existence of validation code, form attributes or an old report.

## Evidence and limits

Three behavioural generator tests passed: comments/literal/dynamic discovery, privacy-safe metadata, stable IDs, repeated-ID disambiguation, domain mapping, traversal exclusions and deterministic regeneration. `--check` passed after a second run. Existing Dev344 has separate real policy/route/HTTP/browser evidence in `website/integrations/osticket/TICKET-344-VALIDATION.md`; the generated register does not automatically mark all native views passed because not every native served view is stored in this repository.

Static scanning is deliberately a lower bound. Concatenated template fragments, event-bound forms without an explicit constructor, Blade includes and installed native osTicket/MyBB/Azuriom templates require live/native reconciliation. Redirected legacy authored pages must be marked retired only after served-route verification. JavaScript constructors may duplicate a literal page form; do not create tickets for them until reconciled. Existing domain mappings are review targets, not proof that all handlers in a mixed account page share one permission boundary.

## Next bounded step

Reconcile the authored candidates against read-only served routes and installed native templates, especially public Support forms, MyBB forms and Azuriom product/admin/checkout forms. Capture only form identity, field schema and handler routes, never credentials or user submissions. Separate distinct form/actions, link current domain tickets, attach real required/type/Unicode/authorization/CSRF/upload/replay/accessibility evidence, and keep pending dimensions explicit. Test actual handlers in isolated fixtures; never submit a live payment or destructive request. Confirmed new bugs need their own human-approved Bug decision before repair. Close331only when every reconciled form has a current outcome and evidence.

No production files, database records, permissions or financial state were changed by this slice. Source-only review/merge has no deployment step.

## 5 November native-template discovery checkpoint

The read-only collector `website/scripts/native-form-inventory.py --output FILE` was executed against installed Support client templates and Azuriom core/shop Blade templates. Its allowlisted roots are fixed in code. It exports logical paths, template hashes, stable candidate keys, literal field schema and normalized path/route identifiers; it never exports input values, raw template bodies, URL queries, credentials or submissions. Symlinks, fixtures, cache, vendor, email and compiled templates are excluded. Required/CSRF markers are source candidates, not verified enforcement. Quoted PHP `->` in Blade action attributes is handled without truncating tags.

`website/docs/native-form-audit-register.json` contains **153 candidates in 97 installed templates**: 15 Support client candidates and 138 Azuriom candidates. No root was missing. Each remains runtime-unverified and every validation dimension remains not-run. These counts must not be added to the 33 authored candidates as distinct live forms: overlap, inactive native features and dynamic branches are not reconciled yet.

| Existing QA ticket | Native candidates |
| --- | ---: |
| #337 | 15 |
| #338 | 21 |
| #339 | 31 |
| #340 | 53 |
| #341 | 13 |
| #342 | 14 |
| #343 | 6 |

Snapshot SHA256: `d97ec022dd7005fd131ccee17cce42b363db9d08a9ec90ea562a1a2e012f4d14`.

Three Python behavioural tests passed on the host: privacy/value and unsafe URL exclusion, Blade route/comment/stable-ID behavior, allowlist/symlink exclusions/missing roots/deterministic traversal. Two actual captures were byte-identical. The three existing Node discovery tests also passed. Initial authored `--check` after Windows checkout reported drift; regenerating retained the same33 records and produced no Git content diff, then `--check` passed (checkout line endings, not a claimed validation defect). No production file, form, account, permission or payment was modified.

Still required: MyBB database template discovery, Support staff templates, runtime dynamic-form reconciliation, verifying which routes are actually served, and actual per-form handler/browser evidence in the existing QA child tickets. This checkpoint does not complete331 and does not reopen archived source137 or duplicate the child tasks. The installed-template JSON is a dated source snapshot; rerun and review differences when native software changes.
