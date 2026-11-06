# Store identity form audit — native task 338

6 November 2026. All 21 archived source forms (171–177, 253–264, 270 and 300) were reconciled with installed Azuriom/Shop routes, middleware and Blade files. Unused forms must be mapped to their served replacement rather than re-enabling an independent identity system.

## Confirmed defect and staged correction

Normal authenticated Dev SSO GET /store/profile actually returns the upstream profile, including native email/current-password and password/current-password/confirmation forms. Exact installed DuneShopAccount rejects both mutation paths with 403 before their controllers. No production credential submission was used: the live GET supplied field metadata only, and an isolated invocation proved the denial. This is native **Bug 385**, pending normal protected Owner review. The fix is within approved parent 338; no approval state was bypassed.

The staged middleware serves a native account landing view for exact GET/HEAD profile, after existing current-account checks. It contains no editable credential fields or mutation forms. Fixed links open central site profile, security/email/password and verified game identity at the top level; purchase/subscription history stays in Shop. Explicit _top links require the user's click and prevent a full CP recursively loading into embedded Shop. An automatic HTTP redirect would create that nested layout. The Bug's proposed routing approach is refined here to a native landing with working central links; it is not claimed closed.

Only exact HEAD profile gains the same read view as GET; no other native mutation or HEAD route is broadened. Native password/email/name/avatar/Discord/2FA/delete/money changes stay denied. Theme switching, history, login redirection, cookie comparison, basket invalidation, rank policy, internal native email aliases and paid records are unchanged.

## Per-form checklist

Central/inactive means no valid native mutation is allowed by design. Required/blank/type/bounds/Unicode/upload variations must not reach these native handlers. Do not enable an old form merely to test it. Central credential/profile validation belongs to its CP forms and the Account Security audit.

| Original | Native form / source line | Served boundary and outcome |
| --- | --- | --- |
| 171 | Register, auth/register, 12 | Native register GET/POST blocked; use CP registration /?mode=register. No native account created. |
| 172 | Login 2FA, 12 | Anonymous challenge entry leads to central sign-in; authenticated GET returns Shop. No independent native password login is available; valid native TOTP enrollment not claimed. |
| 173 | Verification resend, 21 | Native verification writes blocked. SSO native email is an internal alias, not a second editable account email. Anonymous protected GET may first go to sign-in. |
| 174 | Native password login, 12 | GET uses central SSO; native POST/non-GET login rejected. Independent Blade credentials are not served as the login authority. |
| 175 | Password confirm, 14 | Authenticated native password confirm GET and writes blocked. Do not ask for the generated internal native password. |
| 176 | Password reset, 12 | Native reset GET/write blocked; central account boundary preserved. No native reset token/mail issued. |
| 177 | Forgot-password email, 18 | Native request blocked. No real mail sent in this audit. |
| 253 | Profile 1: Discord unlink, 59 | Native write denied; conditional old UI replaced by account landing. Central Discord feature remains its own dependency, not native enrollment. |
| 254 | Profile 2: verification resend, 90 | Native verification write denied; not shown for verified SSO alias in actual Dev GET. |
| 255 | Profile 3: email/current password, 107 | Actually shown but always blocked — Bug 385. Staged landing links to CP security. |
| 256 | Profile 4: password/current password/confirmation, 146 | Actually shown but always blocked — Bug 385. Staged landing links to CP security. |
| 257 | Profile 5: name, 188 | Native write blocked. Site profile/biography uses #site-profile; game identity remains verified-link operation. |
| 258 | Profile 6: avatar upload, 218 | Native write blocked before controller. No file uploaded; no duplicate avatar store introduced. |
| 259 | Profile 7: avatar deletion, 240 | Native mutation blocked, including overridden DELETE. No avatar/file removed. |
| 260 | Profile 8: money transfer, 262 | Native account money transfer denied. No funds/balances/ledger changed. |
| 261 | Delete 1: send email, 13 | Authenticated native delete GET and writes blocked. Anonymous protected GET can redirect to sign-in. No deletion mail issued. |
| 262 | Delete 2: signed confirmation, 27 | Native confirmation writes blocked regardless of submitted ID/token shape. No production signed link/deletion used. |
| 263 | Profile 2FA disable, 38 | Native password-confirm/enrollment boundary unavailable under central credentials; writes denied. No 2FA state changed or central TOTP feature invented. |
| 264 | Profile 2FA enable, 24 | Same native-write boundary; no OTP/recovery codes generated/exposed. |
| 270 | Shop guest-name login, 16 | Actual anonymous entry reaches sign-in; authenticated entry returns Shop. Do not enable guest purchase/name impersonation to exercise dormant form. |
| 300 | Shop subscription cancellation, 155 | Purchase history GET preserved. No active test subscription created. Actual controller owner boundary passes synthetic-model checks; provider replay/network failure remains pending payment audit. No real cancellation. |

Profile form ordinals are conditional; actual Dev HTML showed only two credential forms plus normal logout. Source presence does not prove every form renders. Anonymous redirects can occur before integration middleware, so the report records final sign-in rather than claiming every GET returns 403.

## Actual checks

- **114** isolated candidate middleware checks: GET/HEAD landing, fixed view/no-store/no-referrer, mutation denial with blank/wrong-type/Unicode/HTML/ID inputs, legacy login rejection, theme/history preservation and stale-session invalidation. No DB, bridge or provider calls.
- Existing **24** cookie/session-binding checks and role/route policy suite pass. Stale native basket discarded; purchase POST not replayed after account change.
- **13** checks compile/render the real Blade fragment with installed Laravel, isolated layout/cache and route stub: no forms/inputs, exact central links/_top targets, history link and artwork. Not browser layout or application bootstrap.
- **10** checks invoke installed subscription controller owner boundary with synthetic models: missing/different owner cannot call cancellation; matching owner dispatches synthetic model once and returns to history. Provider replay/idempotency is not proved.
- Authenticated/anonymous HTTP GETs resolve actual legacy routes. Retained only field names/types/required flags and public destinations, never passwords/CSRF/cookies/OAuth codes/private HTML. Reused artwork returns HTTP 200 image/webp.

Reproduce with PHP CLI: store-identity-test.php <candidate-middleware>, session-binding-test.php <candidate-middleware>, existing policy-test.php, store-identity-view-test.php <installed-azuriom-root> <candidate-blade> and subscription-owner-test.php <installed-subscription-controller>. Do not substitute production credential/cancellation/deletion requests for fixtures. No application bootstrap or production credentials required by fixtures.

## Review, deployment and remaining limits

Staged only: reviewed middleware maps to app/Http/Middleware/DuneShopAccount.php and new view to resources/views/dune-account.blade.php. Guard exact baselines and backups; preserve native ownership/modes and let the normal application user compile the new view. Verify actually served landing, current-session expiry, central links and unchanged history using read-only requests. No DB migration, game/PM2 restart, new rank/password/payment/provider setting. Code-only rollback restores exact middleware and removes only this view if previously absent.

The isolated rendered Blade preview uses the installed public native stylesheets and a minimal layout. Desktop and 390px mobile screenshots show the account card and all four links; mobile document width is 390px with no horizontal overflow. This does not exercise the complete native layout, an embedded live session or keyboard activation. The preview contains no mutation forms or account data.

Parent 338 remains open for full native desktop/mobile/keyboard acceptance, reviewed merge, scoped rollout/live smoke and real provider replay/failure boundary for form 300. Browser input is blocked by an earlier preview confirmation; no live acceptance or deployment claimed. Bug 385 retains pending review.
