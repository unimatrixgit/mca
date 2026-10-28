CREATE TABLE IF NOT EXISTS site_accounts (
 id CHAR(36) PRIMARY KEY, username VARCHAR(32) NOT NULL UNIQUE, display_name VARCHAR(40) NOT NULL,
 password_salt CHAR(32) NOT NULL, password_hash CHAR(128) NOT NULL,
 role VARCHAR(16) NOT NULL DEFAULT 'player', created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS site_sessions (
 token_hash CHAR(64) PRIMARY KEY, user_id CHAR(36) NOT NULL, expires_at BIGINT NOT NULL,
 INDEX site_session_expiry(expires_at), FOREIGN KEY(user_id) REFERENCES site_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS forum_topics (
 id CHAR(36) PRIMARY KEY, category VARCHAR(24) NOT NULL, title VARCHAR(120) NOT NULL, body TEXT NOT NULL,
 author_id VARCHAR(200) NOT NULL, author_name VARCHAR(40) NOT NULL, created_at BIGINT NOT NULL,
 last_activity_at BIGINT NOT NULL, closed TINYINT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0,
 INDEX forum_topics_activity(deleted,last_activity_at), INDEX forum_topics_author(author_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS forum_posts (
 id CHAR(36) PRIMARY KEY, topic_id CHAR(36) NOT NULL, body TEXT NOT NULL, author_id VARCHAR(200) NOT NULL,
 author_name VARCHAR(40) NOT NULL, created_at BIGINT NOT NULL, deleted TINYINT NOT NULL DEFAULT 0,
 INDEX forum_posts_topic(topic_id,deleted,created_at), INDEX forum_posts_author(author_id,created_at),
 FOREIGN KEY(topic_id) REFERENCES forum_topics(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS forum_reports (
 id CHAR(36) PRIMARY KEY, target_type VARCHAR(16) NOT NULL, target_id CHAR(36) NOT NULL,
 reporter_id VARCHAR(200) NOT NULL, reason TEXT NOT NULL, created_at BIGINT NOT NULL,
 resolved TINYINT NOT NULL DEFAULT 0, UNIQUE(target_type,target_id,reporter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS player_profiles (
 user_id VARCHAR(200) PRIMARY KEY, display_name VARCHAR(40) NOT NULL,
 minecraft_name VARCHAR(32) NOT NULL DEFAULT '', discord_name VARCHAR(50) NOT NULL DEFAULT '',
 created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS support_tickets (
 id CHAR(36) PRIMARY KEY, user_id VARCHAR(200) NOT NULL, category VARCHAR(24) NOT NULL,
 subject VARCHAR(120) NOT NULL, body TEXT NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'open',
 created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL,
 INDEX support_tickets_owner(user_id,updated_at), INDEX support_tickets_status(status,updated_at),
 FOREIGN KEY(user_id) REFERENCES player_profiles(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS support_messages (
 id CHAR(36) PRIMARY KEY, ticket_id CHAR(36) NOT NULL, author_id VARCHAR(200) NOT NULL,
 author_name VARCHAR(40) NOT NULL, author_role VARCHAR(16) NOT NULL, body TEXT NOT NULL, created_at BIGINT NOT NULL,
 INDEX support_messages_ticket(ticket_id,created_at), INDEX support_messages_author(author_id,created_at),
 FOREIGN KEY(ticket_id) REFERENCES support_tickets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS site_game_links (
 user_id CHAR(36) PRIMARY KEY,
 minecraft_uuid CHAR(36) UNIQUE DEFAULT NULL,
 minecraft_name VARCHAR(32) DEFAULT NULL,
 token_hash CHAR(64) UNIQUE DEFAULT NULL,
 expires_at BIGINT NOT NULL DEFAULT 0,
 linked_at BIGINT DEFAULT NULL,
 FOREIGN KEY(user_id) REFERENCES site_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS site_game_identities (
 minecraft_uuid CHAR(36) PRIMARY KEY, first_linked_at BIGINT NOT NULL, last_name VARCHAR(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS site_link_challenges (
 user_id CHAR(36) PRIMARY KEY, token_hash CHAR(64) NOT NULL UNIQUE, expires_at BIGINT NOT NULL,
 FOREIGN KEY(user_id) REFERENCES site_accounts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS community_campaigns (
 month CHAR(7) PRIMARY KEY, target_cents BIGINT NOT NULL, received_cents BIGINT NOT NULL DEFAULT 0,
 reward_amount INT NOT NULL, public_code VARCHAR(48) NOT NULL UNIQUE,
 reached_at BIGINT DEFAULT NULL, created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS community_rewards (
 minecraft_uuid CHAR(36) NOT NULL, month CHAR(7) NOT NULL, personal_code VARCHAR(48) NOT NULL UNIQUE,
 issued_at BIGINT NOT NULL, used_at BIGINT DEFAULT NULL,
 PRIMARY KEY(minecraft_uuid,month), FOREIGN KEY(month) REFERENCES community_campaigns(month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS community_claims (
 minecraft_uuid CHAR(36) NOT NULL, month CHAR(7) NOT NULL, receipt CHAR(64) NOT NULL UNIQUE,
 amount INT NOT NULL, prepared_at BIGINT NOT NULL, delivered_at BIGINT DEFAULT NULL,
 PRIMARY KEY(minecraft_uuid,month), FOREIGN KEY(month) REFERENCES community_campaigns(month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS account_audit (
 id CHAR(36) PRIMARY KEY, actor_id CHAR(36) NOT NULL, action VARCHAR(40) NOT NULL,
 details VARCHAR(500) NOT NULL, created_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS cp_ranks (
 rank_key VARCHAR(40) PRIMARY KEY, label VARCHAR(40) NOT NULL, rank_level INTEGER NOT NULL,
 kind VARCHAR(16) NOT NULL, permissions_json TEXT NOT NULL, version INTEGER NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
