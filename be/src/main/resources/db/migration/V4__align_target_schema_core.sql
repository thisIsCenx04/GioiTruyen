-- Align additive schema gaps from db/entity_mysql_final.sql while preserving the
-- current VARCHAR(36) UUID storage used by the Spring Data JDBC entities.

CREATE TABLE IF NOT EXISTS `refresh_tokens` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    revoked_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user_active (user_id, revoked_at, expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `email_verification_tokens` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    used_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_email_verification_token_hash (token_hash),
    KEY idx_email_verification_user (user_id),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `team_daily_stats` (
    team_id VARCHAR(36) NOT NULL,
    stat_date DATE NOT NULL,
    views BIGINT UNSIGNED NOT NULL DEFAULT 0,
    new_follows BIGINT UNSIGNED NOT NULL DEFAULT 0,
    published_stories BIGINT UNSIGNED NOT NULL DEFAULT 0,
    published_chapters BIGINT UNSIGNED NOT NULL DEFAULT 0,
    gross_coin BIGINT UNSIGNED NOT NULL DEFAULT 0,
    net_coin BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (team_id, stat_date),
    CONSTRAINT fk_team_daily_stats_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `teams`
    ADD COLUMN follower_count_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN story_count_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN view_count_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN revenue_coin_cache BIGINT UNSIGNED NOT NULL DEFAULT 0;

ALTER TABLE `wallet_transactions`
    ADD COLUMN idempotency_key VARCHAR(120) NULL;

ALTER TABLE `ad_events`
    ADD COLUMN page_url VARCHAR(1000) NULL,
    ADD COLUMN ip_hash VARCHAR(255) NULL;

ALTER TABLE `ranking_snapshots`
    ADD COLUMN rank_position INT UNSIGNED NULL;

UPDATE `ranking_snapshots`
SET rank_position = `rank`
WHERE rank_position IS NULL;

ALTER TABLE `ranking_snapshots`
    MODIFY COLUMN rank_position INT UNSIGNED NOT NULL;

ALTER TABLE `site_settings`
    ADD COLUMN setting_key VARCHAR(150) NULL;

UPDATE `site_settings`
SET setting_key = `key`
WHERE setting_key IS NULL;

ALTER TABLE `team_ledger`
    MODIFY COLUMN `type` ENUM('STORY_PURCHASE', 'DONATION', 'ADJUSTMENT', 'WITHDRAWAL', 'CHAPTER_UNLOCK', 'PLATFORM_FEE') NOT NULL;
