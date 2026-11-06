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

The default database fixture is H2 in MySQL compatibility mode. It proves this JDBC/receipt contract. The separate opt-in MySQL profile below adds actual InnoDB row-lock, transaction and JVM-crash checks. Neither profile proves Bukkit inventory behavior or cross-server transfer. The existing Core owns the real inventory checkpoint and quarantines an uncertain commit; no stronger distributed exactly-once guarantee is asserted here.

## Isolated MySQL and abrupt JVM exit

`receipt-qa/run_isolated_mysql.py` runs six focused checks against both source and the selected installed plugin's actual `DeliveryStore` class. The test asserts the class's code-source path so compiled source cannot silently shadow the installed JAR. This is a separate profile (`-PqaMySql=true`); the ordinary ten H2 tests remain the default.

The launcher requires Linux root only to initialise a new private data directory and run a separate process as the existing `mysql` OS user. It uses existing MySQL binaries, Java 21 and Gradle; it does not read application credentials, use the production MySQL process or install software. Its temporary server binds **only 127.0.0.1 on a random free port from 49152–65535**, with MySQLX disabled. A private marker, strict URL/schema, actual `@@datadir`, port and bind-address checks run before any fixture DDL. No public port, game server, real purchase or production row is used. The launcher always shuts down only its own process and retains private QA logs/results for inspection.

```sh
JAVA_HOME=/path/to/jdk-21 python3 DuneAtelier/receipt-qa/run_isolated_mysql.py \
  --gradle /path/to/gradle-8.14.3/bin/gradle \
  --installed-artifact /review/installed-DuneAtelier.jar \
  --installed-sha256 b0e640901419eb3e544ae0e60b52867f927341246990a2f5cc67bc1a4dc86bf8
```

Fresh read-only verification found the installed Arrakis artifact still has that SHA256. Six checks pass against source and six against that installed JAR, with zero failures/skips:

- Two independent real JDBC connections: the second receipt claimant waits for the first row lock, then rejects the committed consumed receipt.
- A real duplicate insertion failure followed by rollback restores both receipt and the synthetic four-piece checkpoint; one retry commits both.
- Closing an uncommitted connection releases its receipt and restores the checkpoint.
- A separate child JVM executes the receipt/checkpoint updates and calls `Runtime.halt(97)` **before commit**; MySQL rolls back and one retry succeeds.
- A child JVM halts **after commit**; reconnect observes the committed checkpoint and cannot consume the receipt again.
- Wrong-owner denial, explicit redemption queue and byte-exact original paid name/lore/quality survive the native SQL round trip.

That is four actual child JVM exits across the two artifact modes. The checkpoint is an intentionally synthetic SQL `pieces` row, not a real Bukkit inventory or the whole Core checkpoint implementation. The MySQL process was stopped after verification; production data, receipts, tokens, inventories and services were unchanged.

## Live/source difference and remaining work

The active Arrakis plugin is `DuneAtelier-1.0.0.jar`. Its factory preserves the faction kit's combat properties and decorates cosmetics. The current checkpoint source has a separate `KitEnchantments` quality policy absent from that installed JAR. Do not replace the whole live plugin to obtain a receipt-only change: that would also ship untested menus and combat changes.

Keep historical paid snapshots and earned progression intact. The superseded paid-power Legendary+ proposal is not permission to introduce paid combat bonuses. No token consumption or existing purchase migration has been performed.

Full acceptance still needs controlled actual Java and Bedrock sessions, full/partially full inventory behavior, reconnect/server transfer, permission revocation and real Core/Bukkit failure recovery, plus an explicit policy-compatible decision on the older consolidated upgrade requirements. Minecraft Bedrock was present on the available Windows PC, but launching it exposed no targetable window; no client acceptance is claimed.
