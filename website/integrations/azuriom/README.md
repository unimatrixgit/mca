# DuneRealms shop integration checkpoint

Installed native Azuriom 1.2.13 + Shop 1.2.27 at /home/dunerealms/apps/azuriom, served under CP /store. No credentials belong in this repository.

DuneShopAccount is installed as web middleware; DuneShopServiceProvider registers central SSO and rank hooks. DuneShopRanks uses central website rank entitlements, preserving protected/staff roles and existing VIP memberships. DuneShopRankHooks records durable paid receipt jobs and retries only queued deliveries. Canonical purchasable/required ranks are Member and VIP. Website support can read purchases and manage coupons/discounts; cannot alter gateways, products or payments.

Deployment also patches native Package saving/eligibility, PaymentItem snapshot/revoke, package form and native admin layout. These upstream-specific changes must be preserved when updating Azuriom/Shop; these helper files alone are not an installer. Private backups are on the web host. Re-check integration after upstream upgrades.

Current verified limitations: no live PayPal payment tested; owner configures PayPal and products. Native server command delivery now uses the official AzLink 1.3.13 queue on six game servers (proxy excluded). DuneShopMinecraftGame selects the guarded bridge. Payment items snapshot a verified central Minecraft UUID/name; queued commands retain that recipient, including Bedrock identities. Online-only commands require the matching UUID. Native API access is limited to authenticated status and polling; registration/account/balance endpoints are blocked. Command configuration requires central server-console permissions. Native upstream edits are captured in native-azlink.patch against the prior Dune-patched installation. VIP future generator/three codes remain next-Alpha backlog.

PHP policy and transaction tests use fictional identities and connection-local temporary tables. Never create real payments to test this bridge.


2026-11-02 rollout: six native executable server records and matching private AzLink config files installed, instantCommands=false (outbound HTTPS polling only), no RCON or new game-host listener. Official jar SHA256: 4d51f7fbcfaa0b9e61c2caf8ee2be193cb93c80fb1f4b4a01052b4841f3381de. Private keys are NOT included here. All six services restarted sequentially and confirmed connected. Read-only version commands drained and were observed in all six logs. Native AzLink queue semantics remain upstream; HTTP polling confirmation is not a financial end-to-end test or a guarantee of exactly-once execution during network failure. No existing pending payment was changed.

13 game-bridge checks run with fictional identities and a connection-local temporary queue. Run the QA script as the application owner, not root: Laravel runtime cache/log ownership must remain dunerealms. Shared header JS/CSS are proxied from the canonical website asset on native hosts with no-store; native application styling remains separate. The owner explicitly enabled the configured PayPal Standard gateway; no successful live PayPal capture was tested.
