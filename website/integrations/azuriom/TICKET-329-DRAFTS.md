# Ticket329 — supporter catalogue slice (4 November 2026)

Status: partial; task remains open. No production rollout.

The draft catalogue defines Caravan Supporter (EUR10 proposed), Guild Patron (EUR25 proposed), VIP — Imperial Patron (existing EUR50 reference) and Grand Benefactor (EUR90 proposed). Prices/rewards are unconfirmed. No checkout imports this module and no native package is created or updated.

Only deterministic website decoration is currently specified: seals, profile themes, frames, banners and optional acknowledgement. Lobby particles, armour appearances and vouchers are deferred until their actual implementation is reviewed; a label alone cannot establish cosmetic safety. No paid enchantment, progression, currency, staff role, political office, cape or random reward is included.

The contract rejects enabled/confirmed drafts, rank grants, commands, unknown reward identifiers, extra fields, duplicate keys/rewards and invalid monetary values. It is a design-time check, NOT a payment/receipt authorization boundary. Current Member/VIP checkout allowlists remain unchanged. Existing receipts, manual rank grants, earned progression and current VIP package are untouched.

Official policy checked 4 November 2026: https://www.minecraft.net/en-us/usage-guidelines (Servers and hosting). Cosmetics excluding capes/lookalikes are allowed; gameplay entitlements cannot confer competitive advantage or damage others' experience. DuneRealms uses the stricter cosmetic-only policy. Payment history and upfront pricing remain required. This is a bounded design review, not a blanket compliance certification.

Validation: node --test website/tests/supporter-drafts.test.mjs — 26 tests passed, including missing fields, unsafe roles/commands, paid power/random rewards, draft activation and price bounds. No financial transaction or production write.

Remaining acceptance: original artwork and preview review; confirmed pricing/rewards; additive receipt/identity/revocation integration; disabled native package drafts; bounded Java/Bedrock delivery; real client acceptance and backed-up rollout. Do not close329 from this slice. New packages stay disabled pending confirmation; do not silently replace purchased benefits.

Update4November2026: Owner approved displayed prices/design/rewards in the local preview. See SUPPORTER-ART-APPROVAL.md. Original unconfirmed snapshot above is historical; catalogue now approved-design but disabled. No native package/receipt/entitlement change.
