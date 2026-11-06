# Native admin modal correction — 340 / Bug 388

PR55/source `dde7f47ca752d91ee7971e41c59d4b599cd3e6f6` was merged as
`b079e82650a8981f7df5d52e941579b59e4c7c74` and deployed on 6 November 2026.
The actual installed bundle passes 39 isolated checks before and after deployment.
Actual authenticated native Shop Packages acceptance also passes X, Cancel, Escape
and reopening, with the backdrop removed and body unlocked afterward. The confirmation
was never accepted and no record was deleted. Bug 388 is completed; parent 340 stays open.

The shared native admin delete-confirmation header has `data-bs-dismiss="alert"` on its close X. It belongs to a Bootstrap modal, so clicking it can leave the backdrop and body lock in place. The patch changes that one target to `modal`. It does not submit or change the existing DELETE form, CSRF marker, method override, route or permission checks.

`admin-modal-test.cjs` loads the actual installed Vite admin bundle and the candidate's exact native modal fragment in isolated jsdom. It checks X, double X, Cancel, Escape and reopening. Every case must remove the backdrop and body lock without DOM errors, network calls or form submissions. The original wrong attribute reproduces the failure with the same installed bundle.

Run against a candidate native layout and a separate QA dependency prefix containing jsdom:

```sh
node admin-modal-test.cjs /path/to/native /path/to/candidate.blade.php /path/to/qa-prefix
```

The fixture is not a full native application/browser acceptance test. It never bootstraps the application or exercises a real deletion. Apply the patch only to the inspected baseline, preserving ownership/mode and taking a code backup. Verify the installed layout, PHP syntax and actual-bundle fixture after deployment. Roll back that file only if needed.

This is one correction within approved parent 340 and approved Bug 388. The broader 53-form platform audit remains open. It does not enable centrally blocked core-admin routes, alter credentials or roles, change payment/provider configuration, or certify every admin form.
