-- Apply once to an existing website DB after backup; new installs use CLAN_KIT_SCHEMA.
-- This does not change the separate native clan crafting fee or enable game fulfillment.
ALTER TABLE website.site_clan_kits ADD COLUMN shop_price_cents BIGINT NOT NULL DEFAULT 20000;
