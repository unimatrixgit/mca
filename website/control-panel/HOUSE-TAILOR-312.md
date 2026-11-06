# House Tailor: five independent clan designs

Ticket 312 / public 500696 consolidates the clan-quality design and catalogue work.
This slice implements the website design workflow, not game delivery or progression activation.

* Each clan can save Common, Uncommon, Rare, Very Rare and Legendary separately.
  Every design has its own UUID, optimistic version, public price, colour and lore.
  Rare and higher also retain optional names for all four armour pieces.
* Prices use the existing Exchange quality minima: 100 / 200 / 400 / 800 / 1,600 DM.
  These are public design prices, separate from the existing in-game member price.
* Common is available; existing Uncommon access remains available. Higher qualities
  require a durable unlock belonging to the clan. Personal progress does not transfer.
  The existing explicit Owner quality grants work only with a current Owner access rank
  and do not bypass verified linkage, active-clan membership or owner/officer editing rights.
* No progression receipts or clan unlocks are fabricated by this migration. Ticket 321
  must implement trusted group receipts before ordinary clans earn the higher qualities.
* Five keyboard-accessible tabs retain independent drafts. Saving disables the selected
  form until completion, preserves other drafts and discards results after account changes.
  Locked qualities explain the restriction. Enchantments remain a fixed, labelled preview.
  Clan designs never show Player Upgrade Token or reroll controls.

## Additive storage and native compatibility

Startup creates `site_clan_kit_designs` and `site_clan_kit_quality_unlocks`.
The idempotent copy preserves every original Uncommon ID, version, author, timestamp,
price and design. It does not update or delete an existing design row.

The original `site_clan_kits` row remains authoritative for Uncommon and the exact
existing private game-context response. Saving Uncommon updates both representations
and the audit atomically. Other qualities never modify that row or its member price.
Each write checks the original verified UUID, current role, clan and quality grant again
inside its conditional SQL. Optimistic conflicts return 409; an audit failure rolls back
the design changes. No currency, inventory, purchase, receipt or permission writes occur.

The public catalogue combines authoritative legacy Uncommon with the other four
quality rows, avoiding duplicate Uncommon listings. Prices and versions reflect saves
automatically; archived clans are excluded. **All clan listings retain
`purchaseAvailable: false`** until the native purchase and delivery implementation is ready.

## Validation and rollout

Run focused clan-shop and catalogue tests. The opt-in integration harness is:

```sh
DUNE_KIT_TEST_CONFIG=/existing/private/database-config.json node tests/house-tailor-mysql.integration.mjs
```

It exercises real InnoDB transactions against connection-local temporary tables only;
it never writes production accounts, designs or payments. MySQL temporary tables cannot
carry foreign keys, so the FK installation must additionally be checked during rollout.
Browser acceptance covers independent drafts, busy forms, account switches, quality locks,
price bounds, keyboard tabs, no Player token controls and 390px layout.

Deploy only the changed CP backend/public files and native catalogue module after
baseline-hash verification and backup. Restart the account-panel first to create the
additive tables, verify the unchanged original row and its copied metadata, then restart
the web catalogue worker. Do not rebuild the public website asset bundle or change the
approved portal art. Verify that all services stay online and no public purchase is enabled.

Rollback restores the previous code and restarts those services. Retain both additive
tables and all later user designs; do not drop tables or overwrite records from snapshots.
The original Uncommon table remains usable by the previous code throughout.

Remaining parent-ticket scope: select and consume all five qualities in the native game,
preserve purchased snapshots, enable delivery only after receipt/client acceptance, and
activate real clan progression through ticket 321. Website completion alone does not
close the consolidated parent ticket.
