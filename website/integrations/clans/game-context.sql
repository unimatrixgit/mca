-- Run with the provisioning administrator after initializeClanShop.
-- Game transactions CALL this routine and keep the transaction open through
-- debit/delivery. No direct access to the website authentication schema is granted.
USE dunegameplay;
DROP PROCEDURE IF EXISTS dune_clan_context_lock;
DELIMITER //
CREATE DEFINER=`website`@`%` PROCEDURE dune_clan_context_lock(
 IN player_uuid CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
) SQL SECURITY DEFINER READS SQL DATA
BEGIN
 SELECT m.minecraft_uuid,c.id AS clan_id,m.clan_role,c.name AS clan_name,
 c.tag AS clan_tag,c.version AS clan_version,k.id AS blueprint_id,
 k.version AS blueprint_version,k.name AS kit_name,k.color AS kit_color,
 k.lore_json AS kit_lore_json,
 CASE WHEN p.setting_value REGEXP '^[0-9]{1,7}$'
 AND CAST(p.setting_value AS UNSIGNED) BETWEEN 1 AND 1000000
 THEN CAST(p.setting_value AS UNSIGNED) ELSE 100 END AS price_dunemarks,
 'UNCOMMON' AS quality,4 AS pieces
 FROM website.site_clan_members m
 JOIN website.site_clans c ON c.id=m.clan_id AND c.active=1
 JOIN website.site_game_links l ON l.user_id=m.user_id AND l.minecraft_uuid=m.minecraft_uuid
 LEFT JOIN website.site_clan_kits k ON k.clan_id=c.id
 LEFT JOIN website.website_settings p ON p.setting_key='clan-kit-price-dunemarks'
 WHERE m.minecraft_uuid=player_uuid FOR SHARE;
END//
DELIMITER ;
GRANT EXECUTE ON PROCEDURE dune_clan_context_lock TO `website`@`%`;
GRANT EXECUTE ON PROCEDURE dune_clan_context_lock TO `dunegameplay`@`127.0.0.1`;
