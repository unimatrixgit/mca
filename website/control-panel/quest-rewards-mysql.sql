-- Run as the gameplay schema writer in the selected MySQL 8 gameplay database.
-- Installs no winner and credits no wallet. Never clear this ledger on rerun.
-- Grant the CP reader EXECUTE on these three procedures only, not ledger SELECT.
CREATE TABLE IF NOT EXISTS dune_quest_first_finder (
 quest_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 account_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 minecraft_uuid VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 proof_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 discovered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 redeemed_at DATETIME(6) NULL,
 CONSTRAINT dune_quest_fixed_id CHECK (quest_id='imperial-portal-eye-ra-v1'),
 CONSTRAINT dune_quest_code_hash CHECK (REGEXP_LIKE(code_hash,'^[a-f0-9]{64}$','c')),
 CONSTRAINT dune_quest_proof_hash CHECK (REGEXP_LIKE(proof_hash,'^[a-f0-9]{64}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
DROP PROCEDURE IF EXISTS dune_quest_register$$
CREATE DEFINER=CURRENT_USER PROCEDURE dune_quest_register(IN p_account TEXT,IN p_minecraft TEXT,IN p_code_hash TEXT,IN p_proof_hash TEXT)
SQL SECURITY DEFINER
MODIFIES SQL DATA
BEGIN
 DECLARE v_account VARCHAR(36);
 DECLARE v_minecraft VARCHAR(36);
 DECLARE v_code_hash CHAR(64);
 DECLARE v_proof_hash CHAR(64);
 DECLARE v_redeemed DATETIME(6);
 DECLARE v_outcome VARCHAR(24);
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF p_account IS NULL OR p_minecraft IS NULL OR p_code_hash IS NULL OR p_proof_hash IS NULL
 OR NOT REGEXP_LIKE(p_account,'^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$','c')
 OR NOT REGEXP_LIKE(p_minecraft,'^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$','c')
 OR NOT REGEXP_LIKE(p_code_hash,'^[a-f0-9]{64}$','c') OR NOT REGEXP_LIKE(p_proof_hash,'^[a-f0-9]{64}$','c') THEN
  SIGNAL SQLSTATE '22023' SET MESSAGE_TEXT='Invalid discovery';
 END IF;
 START TRANSACTION;
 INSERT INTO dune_quest_first_finder(quest_id,account_id,minecraft_uuid,code_hash,proof_hash)
 VALUES('imperial-portal-eye-ra-v1',p_account,p_minecraft,p_code_hash,p_proof_hash)
 ON DUPLICATE KEY UPDATE quest_id=quest_id;
 SELECT account_id,minecraft_uuid,code_hash,proof_hash,redeemed_at
 INTO v_account,v_minecraft,v_code_hash,v_proof_hash,v_redeemed
 FROM dune_quest_first_finder WHERE quest_id='imperial-portal-eye-ra-v1' FOR UPDATE;
 IF BINARY v_account<>BINARY p_account OR BINARY v_minecraft<>BINARY p_minecraft THEN SET v_outcome='taken';
 ELSEIF v_redeemed IS NOT NULL THEN SET v_outcome='redeemed';
 ELSEIF BINARY v_code_hash=BINARY p_code_hash AND BINARY v_proof_hash=BINARY p_proof_hash THEN SET v_outcome='issued';
 ELSE
  UPDATE dune_quest_first_finder SET code_hash=p_code_hash,proof_hash=p_proof_hash WHERE quest_id='imperial-portal-eye-ra-v1';
  SET v_outcome='reissued';
 END IF;
 COMMIT;
 SELECT v_outcome AS outcome;
END$$

DROP PROCEDURE IF EXISTS dune_quest_claim$$
CREATE DEFINER=CURRENT_USER PROCEDURE dune_quest_claim(IN p_account TEXT,IN p_minecraft TEXT,IN p_code_hash TEXT)
SQL SECURITY DEFINER
MODIFIES SQL DATA
BEGIN
 DECLARE v_account VARCHAR(36) DEFAULT NULL;
 DECLARE v_minecraft VARCHAR(36);
 DECLARE v_code_hash CHAR(64);
 DECLARE v_redeemed DATETIME(6);
 DECLARE v_found BOOLEAN DEFAULT TRUE;
 DECLARE v_outcome VARCHAR(24);
 DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_found=FALSE;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 IF p_account IS NULL OR p_minecraft IS NULL OR p_code_hash IS NULL
 OR NOT REGEXP_LIKE(p_account,'^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$','c')
 OR NOT REGEXP_LIKE(p_minecraft,'^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$','c')
 OR NOT REGEXP_LIKE(p_code_hash,'^[a-f0-9]{64}$','c') THEN
  SIGNAL SQLSTATE '22023' SET MESSAGE_TEXT='Invalid redemption';
 END IF;
 START TRANSACTION;
 SELECT account_id,minecraft_uuid,code_hash,redeemed_at INTO v_account,v_minecraft,v_code_hash,v_redeemed
 FROM dune_quest_first_finder WHERE quest_id='imperial-portal-eye-ra-v1' FOR UPDATE;
 IF NOT v_found OR BINARY v_account<>BINARY p_account OR BINARY v_minecraft<>BINARY p_minecraft OR BINARY v_code_hash<>BINARY p_code_hash THEN SET v_outcome='invalid';
 ELSEIF v_redeemed IS NOT NULL THEN SET v_outcome='already_redeemed';
 ELSE
  INSERT INTO dune_economy_balances(uuid,balance) VALUES(p_minecraft,1000.00)
  ON DUPLICATE KEY UPDATE balance=balance+1000.00;
  UPDATE dune_quest_first_finder SET redeemed_at=CURRENT_TIMESTAMP(6) WHERE quest_id='imperial-portal-eye-ra-v1';
  SET v_outcome='credited';
 END IF;
 COMMIT;
 SELECT v_outcome AS outcome;
END$$

DROP PROCEDURE IF EXISTS dune_quest_status$$
CREATE DEFINER=CURRENT_USER PROCEDURE dune_quest_status(IN p_account TEXT,IN p_minecraft TEXT)
SQL SECURITY DEFINER
READS SQL DATA
BEGIN
 SELECT NOT EXISTS(SELECT 1 FROM dune_quest_first_finder) AS available,
 EXISTS(SELECT 1 FROM dune_quest_first_finder WHERE BINARY account_id=BINARY p_account AND BINARY minecraft_uuid=BINARY p_minecraft) AS discovered,
 EXISTS(SELECT 1 FROM dune_quest_first_finder WHERE BINARY account_id=BINARY p_account AND BINARY minecraft_uuid=BINARY p_minecraft AND redeemed_at IS NOT NULL) AS redeemed;
END$$
DELIMITER ;
