CREATE TABLE IF NOT EXISTS website.site_kit_blueprints (
 id CHAR(36) PRIMARY KEY,owner_user_id CHAR(36) NOT NULL,quality VARCHAR(16) NOT NULL,
 name VARCHAR(64) NOT NULL,color CHAR(7) NOT NULL,lore_json TEXT NOT NULL,
 version INTEGER NOT NULL DEFAULT 1,active INTEGER NOT NULL DEFAULT 1,
 created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL,
 UNIQUE KEY kit_owner_quality(owner_user_id,quality),
 FOREIGN KEY(owner_user_id) REFERENCES website.site_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS website.site_kit_listings (
 id CHAR(36) PRIMARY KEY,blueprint_id CHAR(36) NOT NULL,blueprint_version INTEGER NOT NULL,
 clan_id CHAR(36) DEFAULT NULL,shop_type VARCHAR(8) NOT NULL,price_cents BIGINT NOT NULL,
 version INTEGER NOT NULL DEFAULT 1,published INTEGER NOT NULL DEFAULT 0,
 active INTEGER NOT NULL DEFAULT 1,created_at BIGINT NOT NULL,updated_at BIGINT NOT NULL,
 FOREIGN KEY(blueprint_id) REFERENCES website.site_kit_blueprints(id),
 FOREIGN KEY(clan_id) REFERENCES website.site_clans(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
USE dunegameplay;
DROP PROCEDURE IF EXISTS dune_verified_player_lock;
DROP PROCEDURE IF EXISTS dune_kit_listing_lock;
DROP PROCEDURE IF EXISTS dune_kit_catalogue;
DELIMITER //
CREATE DEFINER=`website`@`%` PROCEDURE dune_verified_player_lock(
 IN actual_uuid CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
) SQL SECURITY DEFINER READS SQL DATA
BEGIN
 SELECT l.minecraft_uuid AS player_uuid,l.user_id AS account_id,l.linked_at
 FROM website.site_game_links l JOIN website.site_accounts a ON a.id=l.user_id
 WHERE l.minecraft_uuid=actual_uuid FOR SHARE;
END//
CREATE DEFINER=`website`@`%` PROCEDURE dune_kit_listing_lock(
 IN actual_buyer_uuid CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
 IN chosen_listing CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
) SQL SECURITY DEFINER READS SQL DATA
BEGIN
 SELECT l.id AS listing_id,l.version AS listing_version,b.id AS blueprint_id,
 b.version AS blueprint_version,'personal_sale' AS commerce_kind,
 seller.minecraft_uuid AS seller_uuid,a.username AS seller_name,l.clan_id,l.shop_type,
 l.price_cents,CAST(l.price_cents/100 AS DECIMAL(20,2)) AS price_dunemarks,
 b.name AS kit_name,b.color AS kit_color,b.lore_json AS kit_lore_json,b.quality,
 buyer.minecraft_uuid AS buyer_uuid,buyer.linked_at AS buyer_linked_at,
 seller.linked_at AS seller_linked_at
 FROM website.site_kit_listings l
 JOIN website.site_kit_blueprints b ON b.id=l.blueprint_id AND b.version=l.blueprint_version AND b.active=1
 JOIN website.site_accounts a ON a.id=b.owner_user_id
 JOIN website.site_game_links seller ON seller.user_id=b.owner_user_id
 JOIN website.site_game_links buyer ON buyer.minecraft_uuid=actual_buyer_uuid
 LEFT JOIN website.site_clans c ON c.id=l.clan_id AND c.active=1
 LEFT JOIN website.site_clan_members m ON m.clan_id=c.id AND m.user_id=b.owner_user_id AND m.minecraft_uuid=seller.minecraft_uuid
 WHERE l.id=chosen_listing AND l.active=1 AND l.published=1
 AND l.price_cents BETWEEN CASE b.quality WHEN 'common' THEN 10000 WHEN 'uncommon' THEN 20000 WHEN 'rare' THEN 40000 WHEN 'very_rare' THEN 80000 WHEN 'legendary' THEN 160000 ELSE 100000001 END AND 100000000
 AND ((l.shop_type='player' AND l.clan_id IS NULL) OR (l.shop_type='clan' AND m.clan_role IN ('owner','officer')))
 FOR SHARE;
END//
CREATE DEFINER=`website`@`%` PROCEDURE dune_kit_catalogue()
 SQL SECURITY DEFINER READS SQL DATA
BEGIN
 SELECT l.id AS listing_id,l.version AS listing_version,b.id AS blueprint_id,
 b.version AS blueprint_version,'personal_sale' AS commerce_kind,
 seller.minecraft_uuid AS seller_uuid,a.username AS seller_name,l.clan_id,l.shop_type,
 l.price_cents,CAST(l.price_cents/100 AS DECIMAL(20,2)) AS price_dunemarks,
 b.name AS kit_name,b.color AS kit_color,b.lore_json AS kit_lore_json,b.quality,l.created_at
 FROM website.site_kit_listings l
 JOIN website.site_kit_blueprints b ON b.id=l.blueprint_id AND b.version=l.blueprint_version AND b.active=1
 JOIN website.site_accounts a ON a.id=b.owner_user_id
 JOIN website.site_game_links seller ON seller.user_id=b.owner_user_id
 LEFT JOIN website.site_clans c ON c.id=l.clan_id AND c.active=1
 LEFT JOIN website.site_clan_members m ON m.clan_id=c.id AND m.user_id=b.owner_user_id AND m.minecraft_uuid=seller.minecraft_uuid
 WHERE l.active=1 AND l.published=1
 AND l.price_cents BETWEEN CASE b.quality WHEN 'common' THEN 10000 WHEN 'uncommon' THEN 20000 WHEN 'rare' THEN 40000 WHEN 'very_rare' THEN 80000 WHEN 'legendary' THEN 160000 ELSE 100000001 END AND 100000000
 AND ((l.shop_type='player' AND l.clan_id IS NULL) OR (l.shop_type='clan' AND m.clan_role IN ('owner','officer')))
 ORDER BY l.created_at DESC,l.id LIMIT 500;
END//
DELIMITER ;
GRANT EXECUTE ON PROCEDURE dune_verified_player_lock TO `website`@`%`,`dunegameplay`@`127.0.0.1`;
GRANT EXECUTE ON PROCEDURE dune_kit_listing_lock TO `website`@`%`,`dunegameplay`@`127.0.0.1`;
GRANT EXECUTE ON PROCEDURE dune_kit_catalogue TO `website`@`%`,`dunegameplay`@`127.0.0.1`;
