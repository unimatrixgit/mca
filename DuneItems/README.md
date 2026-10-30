# DuneItems water vials



Independent Paper1.21.4/Java21 plugin. No Core source edits, economy effects, extra abilities or automatic grants. Both items use water-potion consumption and stack to16.



One canonical shapeless recipe: one full WATER_BUCKET plus one GLASS block produces two vials. Server-native crafting owns bucket consumption/return and shift-crafting. No custom inventory mutation in crafting listeners.



Craft authorization is mandatory and fails closed if DuneCore is absent, disabled or its authoritative adapter fails. Expected public adapter: `String waterVialCraftingRole(UUID)` returning exactly `official`, `smuggler` or `none`. No OP, wildcard or unrelated permission bypass. Official license takes precedence in the authoritative Core adapter. Core must persist the real license/career state; this plugin does not infer a role from faction names or player permissions.



PrepareItemCraftEvent selects the authorized result. CraftItemEvent rechecks the actual actor for all click types. Missing authorization or a changed/stale variant cancels the craft and refreshes its preview, requiring a new click. Ingredients and bucket remainders remain server-owned.



Official: Vial of Water; Common; PDC/item_model dunerealms:water_vial; CMD13101. Smuggler: Smugglers Vial of Water; Uncommon; PDC/item_model dunerealms:smugglers_water_vial; CMD13102. Both retain exact first lore line Water is the gift of life, then a quality line and variant flavor. Quality enum reserves Common, Uncommon, Rare, Very Rare and Legendary.



Administrative command: `/duneitems give <online-player> <water_vial|smugglers_water_vial> [1..1024]`. Requires duneitems.admin (defaultOP). No items granted during build/tests. Insufficient storage capacity refuses before a grant; no world drops.



Console-only `/duneitems verifyrecipe` invokes actual Paper craftItemResult with synthetic matrices and does not touch player inventories. It checks18 placements, output2, empty bucket return, glass64→63, inability to craft again from empty bucket, rejects lava/empty buckets and glass panes. This must pass on a running staged Paper server before rollout. It is a server recipe-engine simulation; real client shift-click acceptance remains a separate smoke test.



Deployment is ON HOLD pending the final authoritative Core API contract and successful staged Paper verification. Never deploy an unguarded recipe as a temporary workaround.



## Political regalia (DuneItems1.0.2)



Public native menu: `/duneitems offices` (ordinary players can open it; administrative give/verification remains permission-gated). Java uses the existing private-callback book renderer; Bedrock uses native forms with published faction crest art. Root must link this command into the main navigator.



Requires Core24 `currentPoliticalOffices(UUID)` and `transactNetworkInventory(Player,Consumer<Connection>)`. Only current elected emperor and canonical faction house-leader offices qualify; personal appointments and OP status do not. Item metadata uses dunerealms:political_office, political_term, political_owner and quality (all STRING; quality is lowercase uncommon). Exact owner AND current term must match. Four armor pieces clone Core.createFactionKit, retaining faction stats, trims and enchantments. Both offices clone the actual player's authoritative faction kit. Emperor uses imperial purple while retaining that faction's stats and trims; house leaders keep their factory faction colors. Common quality lore is replaced with Uncommon, rather than duplicated.



Claims use central InnoDB dune_office_kit_claims with unique(office_key,term_id). Inside Core's fenced shared-inventory transaction, the authority row is held FOR SHARE, current authority is rechecked, the receipt is inserted and exactly four pieces are added. Core commits the inventory checkpoint in that same transaction. Four storage slots are required; failures/duplicate claims do not mint another kit. The schema was verified on actual central MySQL; synthetic constraint probes rolled back and left no test receipts. No real player's kit was minted during tests.



Equip/interact/dispenser/click/login gates and a five-second worn-office-gear eligibility audit remove revoked or foreign gear. Core24 separately calls isOfficeGearEligible for political armor before any faction full-set bonus, failing closed if Items is absent or errors. Optional onPoliticalAuthorityChanged callback is exposed; Core24 currently relies on fresh bonus checks plus the eligibility audit rather than installing a broad election listener. Unequipped pieces go to storage or owner-bound persistent drops if storage is full; foreign pickup, mob pickup, hopper pickup, despawn and damage to the dropped item are prevented. Historical items are retained as keepsakes, not deleted. Regular items do not trigger periodic SQL lookups.



Validation:19 focused tests pass, including the original9 water tests, exact owner/term authorization, malformed/expired metadata, SQL receipt uniqueness/rollback retry, owner-bound drops and pickup/despawn guards. Actual client equip/combat and staged Paper recipe-engine verification remain rollout checks; no claim of completed live acceptance is made here.


Core24 also grants office eligibility to the currently elected leader of an actively imperial-appointed water/spice house. Water Steward and Spice Steward gear use cyanblue and amber dye respectively, while preserving the actual faction kit attributes and trims. The office term includes both elected leader and current Emperor appointment identity; revocation, expiry or a changed Emperor invalidates the old gear. Every office card uses the same validated title formatter with ASCII separators and public faction names.


## Paper1.21.4 QA helper correction (1.0.3)

Actual staging diagnostic confirmed bucket0/glass4 returned the native empty bucket correctly, but Server.craftItemResult left glass64 because its cropped-input remaining-items indexes do not map back to the full3x3 matrix. Official CraftServer source and the actual server ResultSlot bytecode confirmed normal crafting uses positioned input offsets correctly. No production crafting or ingredient remainder behavior was changed. Final console QA checks18 placement matches/output2 using getCraftingRecipe and4 full-bounding-box corner cases for native bucket return/glass64-to63. This exercises the remainder invariant without the defective cropped helper path.19 unit tests remain passing. Root owns final staging restart and actual console PASS before network rollout.


## Final authorization guard (1.0.4)

Automated Crafter blocks have no licensed player actor. CrafterCraftEvent cancels only the canonical water-vial recipe, leaving unrelated recipes untouched. This closes an automation bypass without modifying normal crafting or native bucket returns. PlayerArmorChangeEvent now immediately checks changed office armor; the same-tick authority snapshot is reused across four-piece changes. Dispenser checks retain exact owner/current-term validation.21 tests pass, including automated-crafter denial/unrelated-recipe preservation and dispenser foreign/expired denial with current-owner acceptance. Root recorded actual Paper recipe QA PASS on1.0.3; that recipe/QA code is unchanged in1.0.4.
