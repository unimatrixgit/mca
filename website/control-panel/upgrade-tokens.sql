CREATE TABLE IF NOT EXISTS website.site_upgrade_tokens(
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_user_id CHAR(36) NOT NULL,minecraft_uuid CHAR(36) NOT NULL,
 private_code VARCHAR(52) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 price_dunemarks DECIMAL(20,2) NOT NULL,created_at BIGINT NOT NULL,
 used_at BIGINT NULL,blueprint_id CHAR(36) NULL,result_json JSON NULL,
 KEY upgrade_owner(owner_user_id,created_at),UNIQUE KEY upgrade_private_code(private_code)
) ENGINE=InnoDB;
DELIMITER //
CREATE PROCEDURE website.dune_upgrade_purchase(IN p_user CHAR(36),IN p_uuid CHAR(36),IN p_request CHAR(36),IN p_code VARCHAR(52),IN p_hash CHAR(64),IN p_now BIGINT)
SQL SECURITY DEFINER
BEGIN
 DECLARE v_link CHAR(36);DECLARE v_owner CHAR(36);DECLARE v_identity CHAR(36);DECLARE v_code VARCHAR(52);DECLARE v_balance DECIMAL(20,2);DECLARE v_exists INT DEFAULT 0;
 IF NOT REGEXP_LIKE(p_request,'^[a-f0-9-]{36}$','i') OR NOT REGEXP_LIKE(p_code,'^DRU-[A-F0-9]{48}$','c') OR p_hash<>LOWER(SHA2(p_code,256)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Invalid purchase';END IF;
 SELECT minecraft_uuid INTO v_link FROM website.site_game_links WHERE user_id=p_user FOR SHARE;
 IF v_link IS NULL OR LOWER(v_link)<>LOWER(p_uuid) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Identity changed';END IF;
 SELECT COUNT(*) INTO v_exists FROM website.site_upgrade_tokens WHERE id=p_request;
 IF v_exists=1 THEN
  SELECT owner_user_id,minecraft_uuid,private_code INTO v_owner,v_identity,v_code FROM website.site_upgrade_tokens WHERE id=p_request FOR UPDATE;
  IF v_owner<>p_user OR LOWER(v_identity)<>LOWER(p_uuid) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Purchase conflict';END IF;
 ELSE
  INSERT INTO website.site_upgrade_tokens(id,owner_user_id,minecraft_uuid,private_code,code_hash,price_dunemarks,created_at) VALUES(p_request,p_user,p_uuid,p_code,p_hash,5000,p_now);
  UPDATE dunegameplay.dune_economy_balances SET balance=balance-5000 WHERE uuid=p_uuid AND balance>=5000;
  IF ROW_COUNT()<>1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Insufficient Dunemarks';END IF;
  INSERT INTO dunegameplay.dune_staff_bank_wallet(account_key,balance,updated_at) VALUES('network',0,p_now) ON DUPLICATE KEY UPDATE account_key=account_key;
  SELECT balance INTO v_balance FROM dunegameplay.dune_staff_bank_wallet WHERE account_key='network' FOR UPDATE;
  IF v_balance<0 OR v_balance+5000>1000000000000 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Treasury limit';END IF;
  UPDATE dunegameplay.dune_staff_bank_wallet SET balance=balance+5000,updated_at=p_now WHERE account_key='network';
  INSERT INTO dunegameplay.dune_staff_bank_ledger(id,player_uuid,action,delta,balance_after,created_at,reference) VALUES(UUID(),p_uuid,'upgrade-token',5000,v_balance+5000,p_now,CONCAT('upgrade-token:',p_request));
  INSERT INTO website.account_audit(id,actor_id,action,details,created_at) VALUES(UUID(),p_user,'upgrade-token-purchase',JSON_OBJECT('purchaseId',p_request,'priceDunemarks',5000),p_now);
  SET v_code=p_code;
 END IF;
 SELECT p_request AS id,v_code AS private_code;
END//
DELIMITER ;
GRANT EXECUTE ON PROCEDURE website.dune_upgrade_purchase TO 'website'@'%';
