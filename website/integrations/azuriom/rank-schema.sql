CREATE TABLE IF NOT EXISTS shop_rank_entitlements (
 receipt_key VARCHAR(96) PRIMARY KEY, account_id CHAR(36) NOT NULL,
 rank_key VARCHAR(16) NOT NULL, active BOOLEAN NOT NULL,
 expires_at BIGINT NULL, created_at BIGINT NOT NULL, revoked_at BIGINT NULL,
 KEY(account_id,rank_key)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS shop_rank_memberships (
 account_id CHAR(36) NOT NULL, rank_key VARCHAR(16) NOT NULL,
 grant_owned BOOLEAN NOT NULL, created_at BIGINT NOT NULL,
 PRIMARY KEY(account_id,rank_key)
) ENGINE=InnoDB;
