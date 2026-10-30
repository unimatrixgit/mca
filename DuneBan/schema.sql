CREATE TABLE IF NOT EXISTS dune_network_bans (
 id VARCHAR(36) PRIMARY KEY,
 player_uuid VARCHAR(36) NOT NULL,
 player_name VARCHAR(40) NOT NULL,
 actor_uuid VARCHAR(36) NOT NULL,
 actor_name VARCHAR(40) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 created_at BIGINT NOT NULL,
 expires_at BIGINT,
 source VARCHAR(32) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 revoked_at BIGINT,
 revoked_by TEXT,
 revoked_reason TEXT,
 active_player_uuid VARCHAR(36) GENERATED ALWAYS AS (IF(active,player_uuid,NULL)) STORED,
 UNIQUE KEY dune_network_bans_one_active(active_player_uuid)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS dune_network_ban_locks(player_uuid VARCHAR(36) PRIMARY KEY) ENGINE=InnoDB;
