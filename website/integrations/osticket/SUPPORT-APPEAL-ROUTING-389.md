# Appeal helper duplicate-load correction — Bug 389

The actual native Dev Center page includes `dune-appeal-routing.js` twice. Its
top-level `const topic`/`const nav` declarations collide on the second execution,
which the browser reports as a lexical redeclaration error. Executing the actual
installed source twice in a shared VM reproduces the error without any network or
navigation. The first copy can still work; this is a duplicate-load defect.

Keep the existing helper private inside a function scope and mark its topic element
after binding. Repeated loads then create one existing appeal link and one change
listener per element. A newly rendered topic select can bind normally. Topic `2`
still routes to `/appeal.php`; other topics and existing appeal links are unchanged.
The shared presentation header also skips injection when the authentication footer
has already included this script. This removes the duplicate in actual page output,
including for browsers still holding a cached older helper. The authentication plugin
itself is unchanged. No authentication, permission, help-topic configuration or backend
intake logic changes.

`appeal-routing-check.cjs` uses real jsdom elements/events and a shared VM scope with
an isolated navigation sink. Supply an existing QA-only jsdom prefix through
`DUNE_SUPPORT_DOM_QA` and run `node --test appeal-routing-check.cjs`. Optionally set
`DUNE_APPEAL_ROUTING_SOURCE` to the inspected installed helper to verify that artifact.
The suite covers normal/other topics, repeated loads, existing lexical globals,
missing nodes, preserving a link and re-rendering a select. It never submits a form,
creates a ticket or contacts a provider.

`shared-header-routing-check.php` includes the candidate's function definitions without
running the native application. Fifteen cases cover relative/absolute/versioned script
tags, quote/spacing variations and rejected lookalikes, escaped text and `data-src`.

Rollout affects the inspected public helper and the one shared presentation condition.
Verify their baselines, take code
backup, preserve owner/mode, merge through the Dev fork and verify served bytes plus
a fresh browser page. No service restart or database mutation is required. Rollback
restores the affected code files. Do not change SSO/plugin bootstrap or appeal policy as part of
this fix.
