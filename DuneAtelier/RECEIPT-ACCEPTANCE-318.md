# Kit delivery: verified receipt boundary (6 November 2026)

Native ticket 318 / public 968896 remains open. This slice verifies the installed receipt code; it does not deploy the checkpoint's newer enchantment policy or complete Java/Bedrock client acceptance.

## Reproduce

With Java 21 and Gradle 8.14.3, run from `DuneAtelier/receipt-qa`:

```sh
gradle test --no-daemon
gradle test --no-daemon --rerun-tasks -PinstalledArtifact=/absolute/path/to/installed-DuneAtelier.jar
```

The first command compiles only the receipt store from source. The second compiles **no production source** and tests the store loaded from the selected installed JAR. Neither opens the game database or creates a real purchase, code, entitlement, player or inventory item.

Ten tests cover wrong-owner denial, fenced transactions, explicit redemption queues, repeat consumption, malformed cosmetics, concurrent receipt locking, receipt/checkpoint rollback and preservation of the original paid snapshot. The concurrent test uses two independent JDBC connections: the second claimant waits while the first owns the row, then rejects the consumed receipt after the first commits. A separate rollback case restores both a synthetic four-piece inventory checkpoint and its receipt before a successful retry.

The database fixture is H2 in MySQL compatibility mode. It proves this JDBC/receipt contract, **not** production MySQL isolation, Bukkit inventory behavior, cross-server transfer or recovery after a process crash. The existing Core owns the real inventory checkpoint and quarantines an uncertain commit; no stronger distributed exactly-once guarantee is asserted here.

## Live/source difference and remaining work

The active Arrakis plugin is `DuneAtelier-1.0.0.jar`. Its factory preserves the faction kit's combat properties and decorates cosmetics. The current checkpoint source has a separate `KitEnchantments` quality policy absent from that installed JAR. Do not replace the whole live plugin to obtain a receipt-only change: that would also ship untested menus and combat changes.

Keep historical paid snapshots and earned progression intact. The superseded paid-power Legendary+ proposal is not permission to introduce paid combat bonuses. No token consumption or existing purchase migration has been performed.

Full acceptance still needs controlled actual Java and Bedrock sessions, full/partially full inventory behavior, reconnect/server transfer, permission revocation and failure/crash recovery, plus an explicit policy-compatible decision on the older consolidated upgrade requirements. Minecraft Bedrock was present on the available Windows PC, but launching it exposed no targetable window; no client acceptance is claimed.
