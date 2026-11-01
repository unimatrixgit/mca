-- Explicit migration, not startup DDL. Native game writers get only EXECUTE.
CREATE TABLE IF NOT EXISTS website.dune_game_ticket_context (
 ticket_id BIGINT UNSIGNED PRIMARY KEY, version BIGINT UNSIGNED NOT NULL DEFAULT 1,
 suspect_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
 last_action VARCHAR(24) DEFAULT NULL,last_actor_uuid CHAR(36) DEFAULT NULL,
 last_from_version BIGINT UNSIGNED DEFAULT NULL,updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS website.dune_game_ticket_context_events (
 id CHAR(36) PRIMARY KEY,ticket_id BIGINT UNSIGNED NOT NULL,actor_user_id CHAR(36) NOT NULL,
 actor_uuid CHAR(36) NOT NULL,action VARCHAR(24) NOT NULL,from_version BIGINT UNSIGNED NOT NULL,
 to_version BIGINT UNSIGNED NOT NULL,details_json TEXT NOT NULL,created_at BIGINT NOT NULL,
 KEY ticket_context_history(ticket_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Native assignment/status/department/owner edits invalidate old game screens.
USE website;
DROP TRIGGER IF EXISTS dune_ticket_context_native_change;
DELIMITER //
CREATE DEFINER=`website`@`%` TRIGGER dune_ticket_context_native_change
 AFTER UPDATE ON drsupport_ticket FOR EACH ROW
BEGIN
 IF NOT (OLD.staff_id <=> NEW.staff_id) OR NOT (OLD.dept_id <=> NEW.dept_id)
 OR NOT (OLD.status_id <=> NEW.status_id) OR NOT (OLD.user_id <=> NEW.user_id)
 OR NOT (OLD.user_email_id <=> NEW.user_email_id) THEN
  UPDATE dune_game_ticket_context SET version=version+1,last_action=NULL,last_actor_uuid=NULL,
   last_from_version=NULL,updated_at=CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED)
  WHERE ticket_id=NEW.ticket_id;
 END IF;
END//
DELIMITER ;

USE dunegameplay;
DROP PROCEDURE IF EXISTS dune_case_action_lock;
DELIMITER //
CREATE DEFINER=`website`@`%` PROCEDURE dune_case_action_lock(
 IN p_actor_uuid CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
 IN p_ticket_id BIGINT UNSIGNED,IN p_expected_version BIGINT UNSIGNED,
 IN p_action VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
) SQL SECURITY DEFINER READS SQL DATA
main: BEGIN
 DECLARE v_account CHAR(36) DEFAULT NULL;
 DECLARE v_role VARCHAR(40);DECLARE v_actor_linked BIGINT;
 DECLARE v_staff,v_staff_dept,v_assigned,v_team,v_dept,v_owner,v_topic BIGINT UNSIGNED DEFAULT 0;
 DECLARE v_assigned_only,v_vacation,v_dept_flags INT DEFAULT 0;
 DECLARE v_state VARCHAR(16);DECLARE v_dept_name VARCHAR(128);
 DECLARE v_web_staff,v_web_manage,v_dept_allowed,v_game_admin INT DEFAULT 0;
 DECLARE v_support,v_appeals,v_forum,v_legal,v_sales,v_network INT DEFAULT 0;
 DECLARE v_extended,v_team_member INT DEFAULT 0;
 DECLARE v_context_version BIGINT UNSIGNED DEFAULT 0;
 DECLARE v_suspect,v_creator,v_assigned_uuid,v_target CHAR(36) DEFAULT NULL;
 DECLARE v_last_action VARCHAR(24);DECLARE v_last_actor CHAR(36);DECLARE v_last_from BIGINT UNSIGNED;
 DECLARE v_can_take,v_can_set,v_can_act INT DEFAULT 0;
 IF p_actor_uuid IS NULL OR NOT REGEXP_LIKE(p_actor_uuid,'^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$','c')
 OR p_actor_uuid='00000000-0000-0000-0000-000000000000' OR p_ticket_id IS NULL OR p_ticket_id=0
 OR p_action IS NULL OR p_action NOT IN ('read','teleport_creator','teleport_suspect','inspect_inventory','edit_inventory','adjust_economy','edit_faction')
 OR (p_action<>'read' AND p_expected_version IS NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='case_invalid';
 END IF;
 BEGIN
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_account=NULL;
  SELECT a.id,IF(a.role='player','user',a.role),l.linked_at INTO v_account,v_role,v_actor_linked
  FROM website.site_game_links l JOIN website.site_accounts a ON a.id=l.user_id
  WHERE BINARY l.minecraft_uuid=BINARY p_actor_uuid FOR SHARE;
 END;
 IF v_account IS NULL OR v_actor_linked IS NULL OR v_actor_linked<=0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='case_denied';END IF;
 -- Lock memberships including the empty indexed range; then current rank rows.
 BEGIN
  DECLARE done INT DEFAULT 0;DECLARE discarded VARCHAR(40);
  DECLARE members CURSOR FOR SELECT rank_key FROM website.cp_account_ranks WHERE user_id=v_account FOR SHARE;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
  OPEN members;member_loop: LOOP FETCH members INTO discarded;IF done THEN LEAVE member_loop;END IF;END LOOP;CLOSE members;
 END;
 BEGIN
  DECLARE done INT DEFAULT 0;DECLARE rank_key_value VARCHAR(40);DECLARE permissions_value LONGTEXT;
  DECLARE level_value INT;DECLARE kind_value VARCHAR(16);DECLARE game_permissions LONGTEXT;
  DECLARE ranks CURSOR FOR SELECT rank_key,permissions_json,rank_level,kind FROM website.cp_ranks
   WHERE rank_key=v_role OR rank_key IN (SELECT rank_key FROM website.cp_account_ranks WHERE user_id=v_account)
   ORDER BY rank_key FOR SHARE;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
  OPEN ranks;rank_loop: LOOP
   FETCH ranks INTO rank_key_value,permissions_value,level_value,kind_value;IF done THEN LEAVE rank_loop;END IF;
   IF kind_value='access' AND level_value>0 AND JSON_VALID(permissions_value) AND JSON_TYPE(CAST(permissions_value AS JSON))='ARRAY' THEN
    IF rank_key_value='owners' THEN
     SET v_web_staff=1,v_web_manage=1,v_support=1,v_appeals=1,v_forum=1,v_legal=1,v_sales=1,v_network=1,v_game_admin=1;
    ELSE
     SET v_web_staff=GREATEST(v_web_staff,JSON_CONTAINS(permissions_value,JSON_QUOTE('web.tickets.staff')));
     SET v_web_manage=GREATEST(v_web_manage,JSON_CONTAINS(permissions_value,JSON_QUOTE('web.tickets.manage')));
     SET v_support=GREATEST(v_support,JSON_CONTAINS(permissions_value,JSON_QUOTE('web.tickets.support')));
     SET v_appeals=GREATEST(v_appeals,JSON_CONTAINS(permissions_value,JSON_QUOTE('web.tickets.appeals')));
     SET v_forum=GREATEST(v_forum,JSON_CONTAINS(permissions_value,JSON_QUOTE('web.forum.moderate')));
     SET v_legal=GREATEST(v_legal,JSON_CONTAINS(permissions_value,JSON_QUOTE('web.tickets.legal')));
     SET v_sales=GREATEST(v_sales,JSON_CONTAINS(permissions_value,JSON_QUOTE('web.tickets.sales')));
     SET v_network=GREATEST(v_network,JSON_CONTAINS(permissions_value,JSON_QUOTE('web.tickets.network')));
     SET game_permissions=NULL;
     BEGIN
      DECLARE CONTINUE HANDLER FOR NOT FOUND SET game_permissions=NULL;
      SELECT permissions_json INTO game_permissions FROM website.cp_rank_game_permissions WHERE rank_key=rank_key_value FOR SHARE;
     END;
     IF JSON_VALID(game_permissions) AND JSON_TYPE(CAST(game_permissions AS JSON))='ARRAY' THEN
      SET v_game_admin=GREATEST(v_game_admin,JSON_CONTAINS(game_permissions,JSON_QUOTE('dune.admin')));
     END IF;
    END IF;
   END IF;
  END LOOP;CLOSE ranks;
 END;
 IF v_web_staff<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='case_denied';END IF;
 BEGIN
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_staff=0;
  SELECT staff_id,dept_id,assigned_only,onvacation INTO v_staff,v_staff_dept,v_assigned_only,v_vacation
  FROM website.drsupport_staff WHERE username=REPLACE(v_account,'-','') AND isactive=1 FOR SHARE;
 END;
 IF v_staff=0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='case_denied';END IF;
 BEGIN
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_dept=0;
  SELECT t.staff_id,t.team_id,t.dept_id,t.user_id,t.topic_id,s.state,d.name,d.flags
   INTO v_assigned,v_team,v_dept,v_owner,v_topic,v_state,v_dept_name,v_dept_flags
  FROM website.drsupport_ticket t JOIN website.drsupport_ticket_status s ON s.id=t.status_id
   JOIN website.drsupport_department d ON d.id=t.dept_id WHERE t.ticket_id=p_ticket_id FOR UPDATE;
 END;
 IF v_dept=0 OR v_state NOT IN ('open','closed') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='case_denied';END IF;
 SET v_dept_allowed=CASE v_dept_name WHEN 'Support' THEN v_support WHEN 'Ban appeals' THEN v_appeals
  WHEN 'Forum support' THEN v_forum WHEN 'Legal' THEN v_legal WHEN 'Sales' THEN v_sales WHEN 'Network staff' THEN v_network ELSE 0 END;
 IF v_dept_allowed<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='case_denied';END IF;
 BEGIN
  DECLARE done INT DEFAULT 0;DECLARE discarded BIGINT;
  DECLARE access_rows CURSOR FOR SELECT dept_id FROM website.drsupport_staff_dept_access WHERE staff_id=v_staff FOR SHARE;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
  OPEN access_rows;access_loop: LOOP FETCH access_rows INTO discarded;IF done THEN LEAVE access_loop;END IF;
   IF discarded=v_dept THEN SET v_extended=1;END IF;
  END LOOP;CLOSE access_rows;
 END;
 BEGIN
  DECLARE done INT DEFAULT 0;DECLARE discarded BIGINT;
  DECLARE team_rows CURSOR FOR SELECT team_id FROM website.drsupport_team_member WHERE staff_id=v_staff FOR SHARE;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done=1;
  OPEN team_rows;team_loop: LOOP FETCH team_rows INTO discarded;IF done THEN LEAVE team_loop;END IF;
   IF discarded=v_team AND v_team<>0 THEN SET v_team_member=1;END IF;
  END LOOP;CLOSE team_rows;
 END;
 IF NOT ((v_assigned_only=0 AND (v_staff_dept=v_dept OR v_extended=1)) OR v_assigned=v_staff OR v_team_member=1)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='case_denied';END IF;
 BEGIN
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_context_version=0;
  SELECT version,suspect_uuid,last_action,last_actor_uuid,last_from_version
   INTO v_context_version,v_suspect,v_last_action,v_last_actor,v_last_from
  FROM website.dune_game_ticket_context WHERE ticket_id=p_ticket_id FOR UPDATE;
 END;
 IF p_expected_version IS NOT NULL AND p_expected_version<>v_context_version
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='case_conflict';END IF;
 -- Owner identity always follows the current verified link, never a cached UUID.
 BEGIN
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_creator=NULL;
  SELECT l.minecraft_uuid INTO v_creator FROM website.drsupport_user_email e
   JOIN website.site_accounts a ON e.address=CONCAT(a.id,'@accounts.dunerealms.invalid')
   JOIN website.site_game_links l ON l.user_id=a.id WHERE e.user_id=v_owner AND l.linked_at>0 FOR SHARE;
 END;
 BEGIN
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_assigned_uuid=NULL;
  SELECT l.minecraft_uuid INTO v_assigned_uuid FROM website.drsupport_staff a
   JOIN website.site_accounts u ON a.username=REPLACE(u.id,'-','')
   JOIN website.site_game_links l ON l.user_id=u.id WHERE a.staff_id=v_assigned AND l.linked_at>0 FOR SHARE;
 END;
 IF v_suspect IS NOT NULL THEN
  BEGIN
   DECLARE verified CHAR(36) DEFAULT NULL;DECLARE CONTINUE HANDLER FOR NOT FOUND SET verified=NULL;
   SELECT minecraft_uuid INTO verified FROM website.site_game_identities WHERE BINARY minecraft_uuid=BINARY v_suspect AND first_linked_at>0 FOR SHARE;
   IF verified IS NULL THEN SET v_suspect=NULL;END IF;
  END;
 END IF;
 IF v_creator IS NOT NULL AND (v_creator='00000000-0000-0000-0000-000000000000' OR NOT REGEXP_LIKE(v_creator,'^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$','c')) THEN SET v_creator=NULL;END IF;
 IF v_assigned_uuid IS NOT NULL AND (v_assigned_uuid='00000000-0000-0000-0000-000000000000' OR NOT REGEXP_LIKE(v_assigned_uuid,'^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$','c')) THEN SET v_assigned_uuid=NULL;END IF;
 IF v_suspect IS NOT NULL AND (v_suspect='00000000-0000-0000-0000-000000000000' OR NOT REGEXP_LIKE(v_suspect,'^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$','c')) THEN SET v_suspect=NULL;END IF;
 SET v_can_take=(v_state='open' AND v_web_manage=1 AND v_vacation=0 AND v_assigned IN (0,v_staff)
  AND ((v_dept_flags & 16)=0 OR v_staff_dept=v_dept)
  AND ((v_dept_flags & 1)=0 OR v_staff_dept=v_dept OR v_extended=1));
 SET v_can_set=(v_state='open' AND v_web_manage=1 AND v_assigned=v_staff);
 SET v_can_act=(v_state='open' AND v_assigned=v_staff AND v_game_admin=1);
 SET v_target=CASE p_action WHEN 'teleport_creator' THEN v_creator WHEN 'read' THEN NULL ELSE v_suspect END;
 IF p_action<>'read' AND (v_can_act<>1 OR v_target IS NULL)
 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='case_denied';END IF;
 SELECT p_ticket_id AS ticket_id,v_context_version AS context_version,p_actor_uuid AS actor_uuid,
  v_assigned_uuid AS assigned_agent_uuid,v_creator AS creator_uuid,v_suspect AS suspect_uuid,v_target AS target_uuid,
  v_can_take AS can_take,v_can_set AS can_set_suspect,
  (v_can_act AND (v_creator IS NOT NULL OR v_suspect IS NOT NULL)) AS can_teleport,
  (v_can_act AND v_suspect IS NOT NULL) AS can_inspect_inventory,
  (v_can_act AND v_suspect IS NOT NULL) AS can_edit_inventory,
  (v_can_act AND v_suspect IS NOT NULL) AS can_adjust_economy,
  (v_can_act AND v_suspect IS NOT NULL) AS can_edit_faction,
  v_staff AS agent_id,v_assigned AS assigned_staff_id,v_state AS ticket_state,
  v_account AS actor_account_id,v_last_action AS last_action,v_last_actor AS last_actor_uuid,v_last_from AS last_from_version;
END//
DELIMITER ;
GRANT EXECUTE ON PROCEDURE dunegameplay.dune_case_action_lock TO `website`@`%`,`dunegameplay`@`127.0.0.1`;
