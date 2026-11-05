# Stripe Managed Payments — ticket 363

Owner chose to retain Managed Payments on 5 November 2026. Apply native-stripe-managed.patch against the existing Dune-patched StripeMethod, not a clean upstream installation. Backup: /root/ticket363-managed-before-20261105/StripeMethod.php.

Inline products for reviewed automated cosmetic supporter ranks use txcd_10000000 (general electronically supplied services), explicitly enable Managed Payments, and set inclusive tax behavior to preserve the advertised total. Stripe handles invoices; unsupported invoice_creation is removed. Unknown products and hosting are deliberately unclassified and reject with validation rather than an invented tax category. Council is classified solely as automated online community access/Dev-form submission, without a guaranteed human service, staff or gameplay power. Existing administrator catalogue enablement is preserved. Hosting previews are not classified or provisioned. Re-review classification before expanding the catalogue.

Official reference: https://docs.stripe.com/payments/managed-payments/update-checkout and eligibility.

Run: php stripe-managed-test.php /path/to/patched/StripeMethod.php
21 actual-method payload assertions use isolated doubles; no database/provider/charge. Cover payment/subscription tax code, managed flag, inclusive amount, quantity, references, recurring interval, ownership metadata and rejected unclassified products. These are not a successful Stripe payment or lifecycle test. Live signed webhook delivery remains separately required.

