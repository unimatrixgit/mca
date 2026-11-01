USE dunegameplay;
DROP PROCEDURE IF EXISTS dune_staff_bank_access_lock;
DELIMITER //
CREATE DEFINER=`website`@`%` PROCEDURE dune_staff_bank_access_lock(
 IN player_uuid CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
) SQL SECURITY DEFINER READS SQL DATA
main: BEGIN
 DECLARE account_id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL;
 DECLARE primary_role VARCHAR(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL;
 DECLARE allowed_view,allowed_deposit,allowed_manage INT DEFAULT 0;
 DECLARE linked_timestamp BIGINT;
 BEGIN
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET account_id=NULL;
  SELECT a.id,IF(a.role='player','user',a.role),l.linked_at INTO account_id,primary_role,linked_timestamp
  FROM website.site_game_links l JOIN website.site_accounts a ON a.id=l.user_id
  WHERE l.minecraft_uuid=player_uuid FOR SHARE;
 END;
 IF account_id IS NULL THEN
  SELECT player_uuid AS player_uuid,0 AS can_view,0 AS can_deposit,0 AS can_withdraw;
  LEAVE main;
 END IF;
 -- Lock role membership, including its empty range, before checking rank authority.
 BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE discarded VARCHAR(40);
  DECLARE assignments CURSOR FOR SELECT rank_key FROM website.cp_account_ranks WHERE user_id=account_id FOR SHARE;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
  OPEN assignments;
  assignment_loop: LOOP
   FETCH assignments INTO discarded;
   IF done=1 THEN LEAVE assignment_loop; END IF;
  END LOOP;
  CLOSE assignments;
 END;
 BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE current_key VARCHAR(40);
  DECLARE current_permissions LONGTEXT;
  DECLARE current_level INT;
  DECLARE current_kind VARCHAR(16);
  DECLARE authority CURSOR FOR
   SELECT rank_key,permissions_json,rank_level,kind FROM website.cp_ranks
   WHERE rank_key=primary_role OR rank_key IN (SELECT rank_key FROM website.cp_account_ranks WHERE user_id=account_id)
   FOR SHARE;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
  OPEN authority;
  authority_loop: LOOP
   FETCH authority INTO current_key,current_permissions,current_level,current_kind;
   IF done=1 THEN LEAVE authority_loop; END IF;
   IF current_kind='access' AND current_level>0 THEN
    IF current_key='owners' THEN SET allowed_view=1,allowed_deposit=1,allowed_manage=1;
    ELSE
     SET allowed_view=GREATEST(allowed_view,JSON_CONTAINS(current_permissions,JSON_QUOTE('web.bank.staff.view')));
     SET allowed_deposit=GREATEST(allowed_deposit,JSON_CONTAINS(current_permissions,JSON_QUOTE('web.bank.staff.deposit')));
     SET allowed_manage=GREATEST(allowed_manage,JSON_CONTAINS(current_permissions,JSON_QUOTE('web.bank.staff.manage')));
    END IF;
   END IF;
  END LOOP;
  CLOSE authority;
 END;
 SELECT player_uuid AS player_uuid,allowed_view AS can_view,allowed_deposit AS can_deposit,allowed_manage AS can_withdraw;
END//
DELIMITER ;
GRANT EXECUTE ON PROCEDURE dune_staff_bank_access_lock TO `website`@`%`;
GRANT EXECUTE ON PROCEDURE dune_staff_bank_access_lock TO `dunegameplay`@`127.0.0.1`;
