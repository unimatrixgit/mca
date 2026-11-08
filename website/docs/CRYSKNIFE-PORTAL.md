# Crysknife award portal — Owner execution brief
Objective: deliver a User Panel page under DuneRealms for the Holy Order of the Crysknife.
Authorized issuers: unimatrix999, grmacken, and current crysknife rank holders only. Other staff/Owner roles alone do not qualify.
Scope: existing seal artwork; eligible non-recipient dropdown; required multiline honour citation; explicit confirmation checkbox; server authority checks; atomic rank/citation/audit; native projection outbox. Generic rank assignment and new-account registration cannot bypass the portal. Existing roles remain intact.
Acceptance: approved issuers can award once; invalid or stale requests fail without partial writes; existing recipients and inactive/service accounts are excluded; menu hidden for ineligible users. No test awards on production.
Validation: focused permission, recipient, citation, confirmation, duplicate, preservation, menu and legacy honours tests; live browser form and assets verification; read-only/denied endpoint checks.
Delivery: Dev fork PR, backup, scoped deployment, live checks; then publish community post explaining the rule and route.
Follow-up Owner scope: Landsraad voting design, and Imperial Command under Politics & Gameplay with Water and Spice subpages for the Emperor. Website placeholders only; no in-game rights or economy changes.

## Default for all new CP pages (Owner, 8 November)
Use the shared Dev design: external CP stylesheet, wide artwork banner, bounded seal artwork, green cards with gold borders, clear labels, and gold action buttons. Never inject inline style elements (CP CSP blocks them). Register every new module in server static allowlist and verify the rendered page after deployment before reporting it ready.

Stilgar artwork: original generated Minecraft-style portrait, 8 November 2026, stored as website/web/assets/stilgar-portrait-v1.png. Quote: “As written!” — Stilgar, Dune: Part Two; screenplay reference https://www.duneinfo.com/Content/files/dune-part-two-denis-villeneuve-jon-spaihts.pdf . Surrounding Order prose is original DuneRealms lore, not a quotation. Add framed portrait beside recipient profile citations without modifying their text.
