-- Candidate only: NOT installed. No existing receipt, role or earned-unlock table altered.
CREATE TABLE supporter_receipts (
 receipt_key VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 account_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 package_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 rewards_json JSON NOT NULL,
 state ENUM('active','revoked') NOT NULL,
 INDEX supporter_account_state(account_id,state)
) ENGINE=InnoDB;
CREATE TABLE supporter_cosmetics (
 receipt_key VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 reward_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(receipt_key,reward_key),
 FOREIGN KEY(receipt_key) REFERENCES supporter_receipts(receipt_key)
) ENGINE=InnoDB;
