# DuneRealms shop integration checkpoint

Installed native Azuriom 1.2.13 + Shop 1.2.27 at /home/dunerealms/apps/azuriom, served under CP /store. No credentials belong in this repository.

DuneShopAccount is installed as web middleware; DuneShopServiceProvider registers central SSO and rank hooks. DuneShopRanks uses central website rank entitlements, preserving protected/staff roles and existing VIP memberships. DuneShopRankHooks records durable paid receipt jobs and retries only queued deliveries. Canonical purchasable/required ranks are Member and VIP. Website support can read purchases and manage coupons/discounts; cannot alter gateways, products or payments.

Deployment also patches native Package saving/eligibility, PaymentItem snapshot/revoke, package form and native admin layout. These upstream-specific changes must be preserved when updating Azuriom/Shop; these helper files alone are not an installer. Private backups are on the web host. Re-check integration after upstream upgrades.

Current verified limitations: no live PayPal payment tested; owner configures PayPal and products. Native server command delivery is NOT connected to the CP agent or game servers. The Servers link opens CP operations but does not itself connect automatic fulfillment. Do not advertise Minecraft rank delivery. VIP future generator/three codes remain next-Alpha backlog.

PHP policy and transaction tests use fictional identities and connection-local temporary tables. Never create real payments to test this bridge.
