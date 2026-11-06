# Store payment form audit — task 342

6 November 2026. This is a staged, bounded correction for two reproduced input defects, not acceptance of the entire payment lifecycle. All thirteen archived forms were read and mapped below. Parent 342 remains open.

## Confirmed defects and changes

**Bug 386:** installed CartController.update silently turns `2oops` into 2, `2.75` into 2 and a nested array into 1. Top-level scalar quantities trigger a foreach warning. Zero leaves the old quantity unchanged despite the served input allowing zero. Reproduced by invoking the actual installed controller with real Laravel Request and a synthetic cart; no production POST.

The candidate validates the entire quantities map before changing any item. Malformed scalars, negative/overflow values, missing items and current per-item limits produce validation errors. Valid values update normally; zero explicitly uses native cart removal, including fixed-quantity items whose native setter would otherwise turn zero into one. A mixed valid/invalid submission makes no partial changes. Escaped inline errors are associated with the field using aria-invalid/aria-describedby; a summary also covers obsolete item keys. Native pricing, discounts, giftcards and checkout are untouched.

**Bug 387:** actual installed PaymentRequest rules accept missing nested package IDs, negative/nested quantities and a price of 1000000000000000000, beyond the served price maximum. Reproduced with installed Laravel validation and a synthetic presence verifier. No receipt/controller delivery/provider was invoked.

The candidate validates scalar unsigned IDs, distinct existing native package IDs, allowed row keys, optional positive integral quantities and a price between zero and the form's 999999 maximum with at most two decimal places. Missing/null quantity retains the existing default of one for disabled fixed-package inputs. The unsigned quantity bound comes from the installed unsignedInteger migration; it is a storage bound, not a new purchase allowance or safe bulk-delivery guarantee. Existing administrative product-limit semantics are not expanded or certified here.

The manual form shows an escaped error summary and tolerates malformed old scalar/nested values. Only numeric package IDs/quantities enter its initial JavaScript data; valid draft rows survive. This input correction does not provide receipt idempotency, transactional delivery or provider failure recovery.

Both Bugs retain normal protected Owner review. Changes are within approved parent 342; neither Bug is approved/closed by this work.

## Per-form checklist

| Original | Exact source form | Current mapping and actual result |
| --- | --- | --- |
| 269 | payments/pay, form1 line42 | Historical empty JS submitForm is replaced by one POST/CSRF/target_top form per payment gateway. Existing error summary is escaped. Source verified; no real provider-start POST. Full duplicate/network/provider failure coverage remains pending. |
| 276 | admin/payments/create, form1 line8 | Actual authenticated Dev GET200 and field metadata confirm the served form. Bug387 boundary corrected; 78 actual-request-rule checks plus Blade redisplay checks pass. No manual receipt/delivery created. Full transaction/idempotency/product-policy acceptance pending. |
| 277 | admin/payments/index, form1 line6 | Actual authenticated Dev GET200 confirms GET search. Source escapes search/transaction/user display; controller search type/bounds and query regression are not certified here. |
| 286 | admin/gateways/create, form1 line8 | Exact Dev GET create/stripe returns403. Native route requires shop.gateways; method-specific rules remain in GatewayController. No credentials/configuration submitted, no wider access granted. Authorized-role fixture/provider-specific coverage pending. |
| 287 | admin/gateways/edit, form1 line8 | Source PUT plus shared _form/method-specific rules mapped; no existing gateway credentials fetched or edit submitted. Same authorization/provider-specific coverage pending. |
| 301 | cart/index, form1 line23 | Bug386 correction. 138 controller/validator checks, 22 actual Cart/CartItem/in-memory session checks, rendered field/summary/escaping checks. No production basket changed. |
| 302 | cart/index, form2 line66 | Clear is POST/CSRF. Actual installed Cart plus candidate controller clears synthetic session; repeated clear succeeds without new contents. Included in 22 native checks. No production clear. |
| 303 | cart/index, form3 line83 | Actual empty Dev cart GET200 serves coupon-add POST/CSRF. Native add controller's required/code/limit/cumulation branches read. Database/native coupon type and duplicate/failure fixtures remain pending; no real coupon applied. |
| 304 | cart/index, form4 line120 | Conditional coupon-removal POST/CSRF; absent from actual empty cart as expected. Native route and removeCoupon mapping read; active-coupon fixture pending. |
| 305 | cart/index, form5 line145 | Actual cart GET serves giftcard-add POST/CSRF. Native add may refresh balance before active-state check; no real card/code/provider was used. Full isolated balance/duplicate/failure coverage pending. |
| 306 | cart/index, form6 line182 | Conditional giftcard-removal POST/CSRF; absent from actual empty cart. Native route/removeGiftcard mapping read; active-card fixture pending. |
| 307 | cart/index, form7 line203 | Actual cart GET serves read-stage checkout GET with no email field for this central SSO alias. Source free-payment/email branches read but not invoked on production. Free-order mutation/idempotency and email boundary require isolated lifecycle coverage. |
| 308 | cart/index, form8 line255 | Conditional site-money confirmation POST/CSRF; not served for the actual external-money configuration. Do not enable site money just to exercise this form. Debit/refund/concurrency fixtures pending; no balance changed. |

Recurring subscription cancellation is original 300 under parent 338: the installed controller's owner check has ten synthetic-model checks; actual provider cancellation replay/network failure is still a dependency here. It is not an extra completed payment form.

No upload field exists in these exact forms. File size/type testing is therefore not applicable to this slice. Required/blank/wrong types/Unicode/unsafe strings/overflow/unknown and duplicate IDs are covered for the two corrected inputs. Native HTTP CSRF/origin, actual role/session revocation, full desktop/mobile/keyboard and network/non-JSON/provider behaviours are not inferred from these CLI fixtures. The installed global CSRF exclusion list is empty; route declarations retain their existing web/auth/can middleware. This source inspection is not an HTTP penetration-test result.

## Reproduction and validation

Use installed PHP/Laravel with an explicit patched candidate controller/request. None of these scripts boots the application, reads application credentials, creates a production receipt, invokes a provider, executes game delivery or runs migrations.

- cart-quantity-test.php: **138** checks through actual candidate controller and installed validator, synthetic cart spies, including no partial save/removal on rejection.
- cart-native-test.php: **22** checks through actual installed Cart/CartItem and candidate controller, in-memory Laravel session and synthetic Buyable; actual totals/limits/fixed-item removal/repeated clear retained.
- manual-payment-validation-test.php: **78** checks of actual candidate PaymentRequest rules with installed validator and synthetic users/packages presence verifier. No receipt processing occurs.
- payment-forms-view-test.php: **37** actual Blade compile/render checks with native includes, isolated layout/route/old/CSRF helpers. Escaping, field error association, safe scalar redisplay and numeric JavaScript rows verified. This is not full native browser acceptance.
- Exact four installed baselines match the prepared package; unified patch dry-run applies with zero fuzz. Other native customizations are preserved.

The patch must be applied to an isolated copy first. Example: `patch --dry-run --batch --fuzz=0 -p1 -d <native-copy> < native-payment-validation-342.patch`, then apply there and pass explicit candidate paths to tests. Never use production payment submissions as a validation substitute.

## Review and rollout

Only four runtime targets: CartController, cart Blade, PaymentRequest and manual-payment Blade. Existing central identity middleware and provider configuration are unchanged. No database migration, financial record rewrite, game/PM2 restart, entitlement or permission change. Staged only.

Normal source review/merge and native form acceptance are required before rollout. Check exact baselines again, back up these four files, preserve native owner/modes and let the application user compile views. Read-only smoke must confirm served fields/errors and working history without creating payments. Code-only rollback restores the four original files. Parent 342 remains open for the explicit lifecycle, authority, browser and deployment gaps above; do not advertise the payment system as fully audited.
