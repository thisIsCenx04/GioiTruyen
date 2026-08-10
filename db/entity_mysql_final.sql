-- ============================================================================
-- Gioitruyen / Web Truyen - Canonical MySQL Schema Reference
-- Target: MySQL 8.0.16+ / InnoDB / utf8mb4
-- Intended stack: Spring Boot 3 + Java 21 + JPA/Hibernate + Flyway
-- IMPORTANT: For an existing database, do not replace applied Flyway migrations.
--            Use this file as the canonical model/reference and create forward-only
--            migrations for differences.
-- ============================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ============================================================================
-- AUTH / USER
-- ============================================================================

CREATE TABLE users (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    email VARCHAR(255) NOT NULL,
    username VARCHAR(100) NULL,
    password_hash VARCHAR(255) NULL,
    display_name VARCHAR(150) NULL,
    avatar_url VARCHAR(2048) NULL,
    role ENUM('READER','ADMIN') NOT NULL DEFAULT 'READER',
    status ENUM('ACTIVE','BANNED','SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    email_verified_at TIMESTAMP(3) NULL,
    last_login_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_username (username),
    KEY idx_users_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_accounts (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    provider ENUM('LOCAL','GOOGLE') NOT NULL,
    provider_account_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_provider_account (provider, provider_account_id),
    KEY idx_auth_accounts_user (user_id),
    CONSTRAINT fk_auth_accounts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    revoked_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user_active (user_id, revoked_at, expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_tokens (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    used_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_token_hash (token_hash),
    KEY idx_password_reset_user (user_id),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE email_verification_tokens (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    used_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_token_hash (token_hash),
    KEY idx_email_verification_user (user_id),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_profiles (
    user_id BINARY(16) NOT NULL,
    bio TEXT NULL,
    cover_url VARCHAR(2048) NULL,
    gender VARCHAR(30) NULL,
    birthday DATE NULL,
    website_url VARCHAR(2048) NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_settings (
    user_id BINARY(16) NOT NULL,
    theme ENUM('LIGHT','DARK','SYSTEM') NOT NULL DEFAULT 'SYSTEM',
    reader_font_size SMALLINT NOT NULL DEFAULT 18,
    reader_font_family VARCHAR(100) NULL,
    reader_line_height DECIMAL(4,2) NOT NULL DEFAULT 1.70,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT chk_user_settings_font_size CHECK (reader_font_size BETWEEN 10 AND 40),
    CONSTRAINT chk_user_settings_line_height CHECK (reader_line_height BETWEEN 1.00 AND 3.00),
    CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- TEAM
-- Team membership is relational; do not duplicate team_id/is_team_member on users.
-- ============================================================================

CREATE TABLE teams (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    avatar_url VARCHAR(2048) NULL,
    cover_url VARCHAR(2048) NULL,
    description TEXT NULL,
    status ENUM('ACTIVE','SUSPENDED','DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_by BINARY(16) NOT NULL,
    follower_count_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    story_count_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    view_count_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    revenue_coin_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_teams_slug (slug),
    KEY idx_teams_status (status),
    KEY idx_teams_created_by (created_by),
    CONSTRAINT fk_teams_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE team_members (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    team_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    member_role ENUM('OWNER','MANAGER','EDITOR','MEMBER') NOT NULL DEFAULT 'MEMBER',
    status ENUM('ACTIVE','REMOVED') NOT NULL DEFAULT 'ACTIVE',
    added_by BINARY(16) NOT NULL,
    joined_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    removed_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_members_team_user (team_id, user_id),
    KEY idx_team_members_user_status (user_id, status),
    KEY idx_team_members_team_status (team_id, status),
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_added_by FOREIGN KEY (added_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE team_follows (
    user_id BINARY(16) NOT NULL,
    team_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, team_id),
    KEY idx_team_follows_team (team_id),
    CONSTRAINT fk_team_follows_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_follows_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE team_daily_stats (
    team_id BINARY(16) NOT NULL,
    stat_date DATE NOT NULL,
    views BIGINT UNSIGNED NOT NULL DEFAULT 0,
    new_follows BIGINT UNSIGNED NOT NULL DEFAULT 0,
    published_stories BIGINT UNSIGNED NOT NULL DEFAULT 0,
    published_chapters BIGINT UNSIGNED NOT NULL DEFAULT 0,
    gross_coin BIGINT UNSIGNED NOT NULL DEFAULT 0,
    net_coin BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (team_id, stat_date),
    CONSTRAINT fk_team_daily_stats_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- STORY / GENRE / MODERATION
-- ============================================================================

CREATE TABLE genres (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL,
    description TEXT NULL,
    icon_url VARCHAR(2048) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_genres_name (name),
    UNIQUE KEY uk_genres_slug (slug),
    KEY idx_genres_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stories (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    team_id BINARY(16) NOT NULL,
    created_by BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(280) NOT NULL,
    original_title VARCHAR(255) NULL,
    original_author VARCHAR(255) NULL,
    short_description VARCHAR(500) NULL,
    description LONGTEXT NULL,
    cover_url VARCHAR(2048) NULL,
    banner_url VARCHAR(2048) NULL,
    content_type ENUM('TEXT','AUDIO','TEXT_AUDIO') NOT NULL DEFAULT 'TEXT',
    status ENUM('DRAFT','PENDING_REVIEW','PUBLISHED','REJECTED','HIDDEN') NOT NULL DEFAULT 'DRAFT',
    progress_status ENUM('ONGOING','COMPLETED','PAUSED') NOT NULL DEFAULT 'ONGOING',
    age_rating VARCHAR(30) NULL,
    published_at TIMESTAMP(3) NULL,
    last_chapter_at TIMESTAMP(3) NULL,
    view_count_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    follow_count_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    favorite_count_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    recommendation_gem_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stories_slug (slug),
    KEY idx_stories_team (team_id),
    KEY idx_stories_status_published (status, published_at DESC),
    KEY idx_stories_progress (progress_status),
    KEY idx_stories_view_cache (view_count_cache DESC),
    KEY idx_stories_recommendation_cache (recommendation_gem_cache DESC),
    CONSTRAINT fk_stories_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE RESTRICT,
    CONSTRAINT fk_stories_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE story_genres (
    story_id BINARY(16) NOT NULL,
    genre_id BINARY(16) NOT NULL,
    PRIMARY KEY (story_id, genre_id),
    KEY idx_story_genres_genre (genre_id),
    CONSTRAINT fk_story_genres_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_genres_genre FOREIGN KEY (genre_id) REFERENCES genres(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE story_reviews (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    story_id BINARY(16) NOT NULL,
    submitted_by BINARY(16) NOT NULL,
    reviewed_by BINARY(16) NULL,
    status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    admin_note TEXT NULL,
    submitted_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reviewed_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    KEY idx_story_reviews_story_status (story_id, status),
    KEY idx_story_reviews_reviewer (reviewed_by),
    CONSTRAINT fk_story_reviews_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_reviews_submitted_by FOREIGN KEY (submitted_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_story_reviews_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chapters (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    story_id BINARY(16) NOT NULL,
    chapter_number DECIMAL(10,2) NOT NULL,
    title VARCHAR(255) NULL,
    slug VARCHAR(280) NOT NULL,
    content LONGTEXT NULL,
    short_description VARCHAR(500) NULL,
    access_type ENUM('FREE','PAID') NOT NULL DEFAULT 'FREE',
    coin_price BIGINT UNSIGNED NOT NULL DEFAULT 0,
    status ENUM('DRAFT','PENDING_REVIEW','PUBLISHED','HIDDEN') NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP(3) NULL,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chapters_story_number (story_id, chapter_number),
    UNIQUE KEY uk_chapters_story_slug (story_id, slug),
    KEY idx_chapters_story_status_number (story_id, status, chapter_number),
    CONSTRAINT chk_chapter_access_price CHECK (
        (access_type = 'FREE' AND coin_price = 0) OR
        (access_type = 'PAID' AND coin_price > 0)
    ),
    CONSTRAINT fk_chapters_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_chapters_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chapter_audios (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    chapter_id BINARY(16) NOT NULL,
    audio_url VARCHAR(2048) NOT NULL,
    duration_seconds INT UNSIGNED NULL,
    file_size BIGINT UNSIGNED NULL,
    narrator VARCHAR(255) NULL,
    status ENUM('VISIBLE','HIDDEN','DELETED') NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_chapter_audios_chapter (chapter_id),
    CONSTRAINT fk_chapter_audios_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audio_listens (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    chapter_audio_id BINARY(16) NOT NULL,
    user_id BINARY(16) NULL,
    session_id VARCHAR(255) NULL,
    listened_seconds INT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audio_listens_audio_date (chapter_audio_id, created_at DESC),
    KEY idx_audio_listens_user (user_id),
    CONSTRAINT fk_audio_listens_audio FOREIGN KEY (chapter_audio_id) REFERENCES chapter_audios(id) ON DELETE CASCADE,
    CONSTRAINT fk_audio_listens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- ENGAGEMENT / VIEW / LIBRARY
-- ============================================================================

CREATE TABLE story_views (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    story_id BINARY(16) NOT NULL,
    chapter_id BINARY(16) NULL,
    user_id BINARY(16) NULL,
    session_id VARCHAR(255) NULL,
    ip_hash VARCHAR(255) NULL,
    viewed_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_story_views_story_date (story_id, viewed_at DESC),
    KEY idx_story_views_user (user_id),
    KEY idx_story_views_session (session_id),
    CONSTRAINT fk_story_views_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_views_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE SET NULL,
    CONSTRAINT fk_story_views_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE story_daily_stats (
    story_id BINARY(16) NOT NULL,
    stat_date DATE NOT NULL,
    views BIGINT UNSIGNED NOT NULL DEFAULT 0,
    unique_views BIGINT UNSIGNED NOT NULL DEFAULT 0,
    audio_listens BIGINT UNSIGNED NOT NULL DEFAULT 0,
    favorites BIGINT UNSIGNED NOT NULL DEFAULT 0,
    follows BIGINT UNSIGNED NOT NULL DEFAULT 0,
    recommendations BIGINT UNSIGNED NOT NULL DEFAULT 0,
    coin_revenue BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (story_id, stat_date),
    CONSTRAINT fk_story_daily_stats_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE library_items (
    user_id BINARY(16) NOT NULL,
    story_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, story_id),
    KEY idx_library_story (story_id),
    CONSTRAINT fk_library_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_library_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE story_follows (
    user_id BINARY(16) NOT NULL,
    story_id BINARY(16) NOT NULL,
    notify_new_chapter BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, story_id),
    KEY idx_story_follows_story (story_id),
    CONSTRAINT fk_story_follows_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_follows_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- COMMENTS / REPORTS
-- ============================================================================

CREATE TABLE comments (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    story_id BINARY(16) NOT NULL,
    chapter_id BINARY(16) NULL,
    parent_id BINARY(16) NULL,
    content TEXT NOT NULL,
    status ENUM('VISIBLE','HIDDEN','DELETED') NOT NULL DEFAULT 'VISIBLE',
    like_count_cache BIGINT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_comments_story_date (story_id, created_at DESC),
    KEY idx_comments_chapter_date (chapter_id, created_at DESC),
    KEY idx_comments_parent (parent_id),
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE comment_likes (
    comment_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (comment_id, user_id),
    KEY idx_comment_likes_user (user_id),
    CONSTRAINT fk_comment_likes_comment FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_likes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reports (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    reporter_id BINARY(16) NOT NULL,
    target_type ENUM('STORY','CHAPTER','COMMENT') NOT NULL,
    target_id BINARY(16) NOT NULL,
    report_type VARCHAR(100) NOT NULL,
    description TEXT NULL,
    status ENUM('OPEN','PROCESSING','RESOLVED','REJECTED') NOT NULL DEFAULT 'OPEN',
    handled_by BINARY(16) NULL,
    admin_note TEXT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    KEY idx_reports_status_date (status, created_at DESC),
    KEY idx_reports_reporter (reporter_id),
    KEY idx_reports_target (target_type, target_id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_handled_by FOREIGN KEY (handled_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- WALLET / PAYMENT
-- ============================================================================

CREATE TABLE currencies (
    code ENUM('COIN','GEM') NOT NULL,
    name VARCHAR(80) NOT NULL,
    PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO currencies(code, name) VALUES
    ('COIN', 'Xu'),
    ('GEM', 'Ngọc');

CREATE TABLE wallets (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    coin_balance BIGINT UNSIGNED NOT NULL DEFAULT 0,
    gem_balance BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallets_user (user_id),
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_transactions (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    currency ENUM('COIN','GEM') NOT NULL,
    type ENUM('DEPOSIT','PURCHASE','DONATION','RECOMMENDATION','DAILY_REWARD','REFERRAL_REWARD','REFUND','ADMIN_ADJUSTMENT') NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT UNSIGNED NOT NULL,
    reference_type VARCHAR(80) NULL,
    reference_id BINARY(16) NULL,
    idempotency_key VARCHAR(120) NULL,
    description TEXT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_transactions_idempotency (idempotency_key),
    KEY idx_wallet_transactions_user_date (user_id, created_at DESC),
    KEY idx_wallet_transactions_reference (reference_type, reference_id),
    CONSTRAINT chk_wallet_transactions_amount CHECK (amount <> 0),
    CONSTRAINT fk_wallet_transactions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_methods (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    name VARCHAR(150) NOT NULL,
    type ENUM('BANK_TRANSFER','QR','PAYPAL','OTHER') NOT NULL,
    config JSON NOT NULL DEFAULT (JSON_OBJECT()),
    instructions TEXT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_by BINARY(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_payment_methods_active (is_active, sort_order),
    CONSTRAINT fk_payment_methods_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE deposit_packages (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    name VARCHAR(150) NOT NULL,
    price_vnd BIGINT UNSIGNED NOT NULL,
    coin_amount BIGINT UNSIGNED NOT NULL,
    gem_amount BIGINT UNSIGNED NOT NULL,
    bonus_coin BIGINT UNSIGNED NOT NULL DEFAULT 0,
    bonus_gem BIGINT UNSIGNED NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT chk_deposit_packages_price CHECK (price_vnd > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payments (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    payment_method_id BINARY(16) NOT NULL,
    deposit_package_id BINARY(16) NULL,
    amount_vnd BIGINT UNSIGNED NOT NULL,
    coin_received BIGINT UNSIGNED NOT NULL DEFAULT 0,
    gem_received BIGINT UNSIGNED NOT NULL DEFAULT 0,
    transaction_code VARCHAR(255) NULL,
    external_transaction_id VARCHAR(255) NULL,
    status ENUM('PENDING','PAID','FAILED','CANCELLED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_external_transaction (external_transaction_id),
    KEY idx_payments_user_date (user_id, created_at DESC),
    KEY idx_payments_status (status, created_at DESC),
    CONSTRAINT chk_payments_amount CHECK (amount_vnd > 0),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_package FOREIGN KEY (deposit_package_id) REFERENCES deposit_packages(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- PURCHASE / UNLOCK
-- ============================================================================

CREATE TABLE purchase_orders (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    story_id BINARY(16) NOT NULL,
    purchase_type ENUM('SINGLE_CHAPTER','CHAPTER_RANGE','FULL_STORY') NOT NULL,
    total_coin BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_purchase_orders_user_date (user_id, created_at DESC),
    KEY idx_purchase_orders_story (story_id),
    CONSTRAINT fk_purchase_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_orders_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_order_items (
    order_id BINARY(16) NOT NULL,
    chapter_id BINARY(16) NOT NULL,
    coin_price BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (order_id, chapter_id),
    KEY idx_purchase_order_items_chapter (chapter_id),
    CONSTRAINT fk_purchase_order_items_order FOREIGN KEY (order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_order_items_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chapter_unlocks (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    chapter_id BINARY(16) NOT NULL,
    purchase_order_id BINARY(16) NULL,
    coin_paid BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chapter_unlocks_user_chapter (user_id, chapter_id),
    KEY idx_chapter_unlocks_chapter (chapter_id),
    CONSTRAINT fk_chapter_unlocks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_chapter_unlocks_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
    CONSTRAINT fk_chapter_unlocks_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- DONATION / TEAM REVENUE
-- ============================================================================

CREATE TABLE donations (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    team_id BINARY(16) NOT NULL,
    story_id BINARY(16) NULL,
    gross_coin BIGINT UNSIGNED NOT NULL,
    commission_rate DECIMAL(5,2) NOT NULL,
    commission_coin BIGINT UNSIGNED NOT NULL,
    team_net_coin BIGINT UNSIGNED NOT NULL,
    message VARCHAR(500) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_donations_team_date (team_id, created_at DESC),
    KEY idx_donations_story_date (story_id, created_at DESC),
    CONSTRAINT chk_donations_gross CHECK (gross_coin > 0),
    CONSTRAINT chk_donations_commission_rate CHECK (commission_rate BETWEEN 0 AND 100),
    CONSTRAINT chk_donations_sum CHECK (commission_coin + team_net_coin = gross_coin),
    CONSTRAINT fk_donations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_donations_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE RESTRICT,
    CONSTRAINT fk_donations_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE team_ledger (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    team_id BINARY(16) NOT NULL,
    type ENUM('STORY_PURCHASE','DONATION','ADJUSTMENT','WITHDRAWAL') NOT NULL,
    gross_coin BIGINT UNSIGNED NOT NULL DEFAULT 0,
    platform_fee_coin BIGINT UNSIGNED NOT NULL DEFAULT 0,
    net_coin BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reference_type VARCHAR(80) NULL,
    reference_id BINARY(16) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_team_ledger_team_date (team_id, created_at DESC),
    KEY idx_team_ledger_reference (reference_type, reference_id),
    CONSTRAINT fk_team_ledger_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- GEM RECOMMENDATION / RANKING
-- ============================================================================

CREATE TABLE story_recommendations (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    story_id BINARY(16) NOT NULL,
    gem_amount BIGINT UNSIGNED NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_story_recommendations_story_date (story_id, created_at DESC),
    KEY idx_story_recommendations_user (user_id),
    CONSTRAINT chk_story_recommendations_amount CHECK (gem_amount > 0),
    CONSTRAINT fk_story_recommendations_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_story_recommendations_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ranking_snapshots (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    ranking_type ENUM('GEM_RECOMMENDATION','COIN_REVENUE','VIEWS') NOT NULL,
    story_id BINARY(16) NOT NULL,
    score BIGINT NOT NULL DEFAULT 0,
    rank_position INT UNSIGNED NOT NULL,
    period ENUM('DAILY','WEEKLY','MONTHLY','ALL_TIME') NOT NULL,
    snapshot_date DATE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ranking_story_period (ranking_type, story_id, period, snapshot_date),
    KEY idx_ranking_lookup (ranking_type, period, snapshot_date, rank_position),
    CONSTRAINT chk_ranking_position CHECK (rank_position > 0),
    CONSTRAINT fk_ranking_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- NOTIFICATIONS
-- ============================================================================

CREATE TABLE notifications (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    type ENUM('SYSTEM','ACCOUNT','ADMIN','STORY_PUBLISHED','NEW_CHAPTER','TEAM_UPDATE','PAYMENT','PURCHASE','REWARD') NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    target_type VARCHAR(80) NULL,
    target_id BINARY(16) NULL,
    target_url VARCHAR(2048) NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_notifications_user_read_date (user_id, is_read, created_at DESC),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_preferences (
    user_id BINARY(16) NOT NULL,
    story_updates BOOLEAN NOT NULL DEFAULT TRUE,
    team_updates BOOLEAN NOT NULL DEFAULT TRUE,
    system_updates BOOLEAN NOT NULL DEFAULT TRUE,
    payment_updates BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- DAILY MISSIONS / REFERRAL
-- ============================================================================

CREATE TABLE missions (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(180) NOT NULL,
    description TEXT NULL,
    mission_type ENUM('LOGIN','READ_CHAPTER','VIEW_STORY','FAVORITE_STORY','COMMENT','OTHER') NOT NULL,
    target_count INT UNSIGNED NOT NULL DEFAULT 1,
    reward_coin BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reward_gem BIGINT UNSIGNED NOT NULL DEFAULT 0,
    daily_limit INT UNSIGNED NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_missions_code (code),
    CONSTRAINT chk_missions_target_count CHECK (target_count > 0),
    CONSTRAINT chk_missions_daily_limit CHECK (daily_limit > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_mission_progress (
    user_id BINARY(16) NOT NULL,
    mission_id BINARY(16) NOT NULL,
    progress_date DATE NOT NULL DEFAULT (CURRENT_DATE),
    progress INT UNSIGNED NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    claimed BOOLEAN NOT NULL DEFAULT FALSE,
    claimed_at TIMESTAMP(3) NULL,
    PRIMARY KEY (user_id, mission_id, progress_date),
    KEY idx_mission_progress_user_date (user_id, progress_date DESC),
    CONSTRAINT fk_mission_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_mission_progress_mission FOREIGN KEY (mission_id) REFERENCES missions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE referral_codes (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    user_id BINARY(16) NOT NULL,
    code VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_referral_codes_user (user_id),
    UNIQUE KEY uk_referral_codes_code (code),
    CONSTRAINT fk_referral_codes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE referrals (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    referrer_id BINARY(16) NOT NULL,
    referred_user_id BINARY(16) NOT NULL,
    status ENUM('PENDING','QUALIFIED','REWARDED','REJECTED') NOT NULL DEFAULT 'PENDING',
    qualified_at TIMESTAMP(3) NULL,
    reward_coin BIGINT UNSIGNED NOT NULL DEFAULT 0,
    reward_gem BIGINT UNSIGNED NOT NULL DEFAULT 0,
    rewarded_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_referrals_referred_user (referred_user_id),
    KEY idx_referrals_referrer (referrer_id),
    CONSTRAINT chk_referral_self CHECK (referrer_id <> referred_user_id),
    CONSTRAINT fk_referrals_referrer FOREIGN KEY (referrer_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_referrals_referred FOREIGN KEY (referred_user_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- COMMUNITY
-- ============================================================================

CREATE TABLE community_rooms (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    name VARCHAR(150) NOT NULL,
    status ENUM('VISIBLE','HIDDEN','DELETED') NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE community_messages (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    room_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    content TEXT NOT NULL,
    reply_to_id BINARY(16) NULL,
    status ENUM('VISIBLE','HIDDEN','DELETED') NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_community_messages_room_date (room_id, created_at DESC),
    KEY idx_community_messages_user (user_id),
    KEY idx_community_messages_reply (reply_to_id),
    CONSTRAINT fk_community_messages_room FOREIGN KEY (room_id) REFERENCES community_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_messages_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_community_messages_reply FOREIGN KEY (reply_to_id) REFERENCES community_messages(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- SITE SETTINGS / DOCUMENTS
-- ============================================================================

CREATE TABLE site_settings (
    setting_key VARCHAR(150) NOT NULL,
    value JSON NOT NULL,
    updated_by BINARY(16) NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (setting_key),
    CONSTRAINT fk_site_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE site_documents (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    type ENUM('TERMS','PRIVACY','COMMUNITY_RULES','TEAM_RULES','COPYRIGHT') NOT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 1,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BINARY(16) NOT NULL,
    published_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_site_documents_type_version (type, version),
    CONSTRAINT chk_site_documents_version CHECK (version > 0),
    CONSTRAINT fk_site_documents_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- ADVERTISEMENT / AFFILIATE
-- Global click flow: first eligible click -> open ad -> cooldown -> next eligible click.
-- Client-side cooldown is UX only; ad_events is analytics, not security enforcement.
-- ============================================================================

CREATE TABLE advertisements (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    name VARCHAR(180) NOT NULL,
    type ENUM('BANNER','POPUP','AFFILIATE_REDIRECT') NOT NULL DEFAULT 'AFFILIATE_REDIRECT',
    image_url VARCHAR(2048) NULL,
    target_url VARCHAR(2048) NOT NULL,
    placement ENUM('GLOBAL_CLICK','STORY_OPEN','STORY_DETAIL','READER','HOME','SIDEBAR') NOT NULL DEFAULT 'GLOBAL_CLICK',
    cooldown_seconds INT UNSIGNED NOT NULL DEFAULT 600,
    max_clicks_per_day INT UNSIGNED NOT NULL DEFAULT 5,
    priority INT NOT NULL DEFAULT 0,
    start_at TIMESTAMP(3) NULL,
    end_at TIMESTAMP(3) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ads_active_placement_priority (is_active, placement, priority DESC),
    CONSTRAINT chk_ads_cooldown CHECK (cooldown_seconds <= 86400),
    CONSTRAINT chk_ads_max_clicks CHECK (max_clicks_per_day <= 100),
    CONSTRAINT chk_ads_date CHECK (end_at IS NULL OR start_at IS NULL OR end_at > start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ad_events (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    advertisement_id BINARY(16) NOT NULL,
    user_id BINARY(16) NULL,
    session_id VARCHAR(255) NULL,
    story_id BINARY(16) NULL,
    page_url VARCHAR(1000) NULL,
    ip_hash VARCHAR(255) NULL,
    event_type ENUM('IMPRESSION','CLICK','REDIRECT') NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ad_events_ad_date (advertisement_id, created_at DESC),
    KEY idx_ad_events_user_date (user_id, created_at DESC),
    CONSTRAINT fk_ad_events_ad FOREIGN KEY (advertisement_id) REFERENCES advertisements(id) ON DELETE CASCADE,
    CONSTRAINT fk_ad_events_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_ad_events_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- ADMIN AUDIT
-- ============================================================================

CREATE TABLE admin_audit_logs (
    id BINARY(16) NOT NULL DEFAULT (UUID_TO_BIN(UUID())),
    admin_id BINARY(16) NOT NULL,
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(120) NOT NULL,
    entity_id BINARY(16) NULL,
    old_data JSON NULL,
    new_data JSON NULL,
    ip_hash VARCHAR(255) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_admin_audit_admin_date (admin_id, created_at DESC),
    KEY idx_admin_audit_entity (entity_type, entity_id),
    CONSTRAINT fk_admin_audit_admin FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================================
-- DEFAULT DATA
-- ============================================================================

INSERT IGNORE INTO site_settings(setting_key, value) VALUES
    ('donation_commission_percent', JSON_OBJECT('value', 10)),
    ('registration_enabled', JSON_OBJECT('value', TRUE)),
    ('maintenance_mode', JSON_OBJECT('value', FALSE)),
    ('daily_free_coin_limit', JSON_OBJECT('value', 1000)),
    ('default_theme', JSON_OBJECT('value', 'SYSTEM')),
    ('referral_reward', JSON_OBJECT('coin', 0, 'gem', 0));

INSERT INTO deposit_packages(
    name, price_vnd, coin_amount, gem_amount, bonus_coin, bonus_gem, is_active
) VALUES (
    '100K', 100000, 90000, 90000, 0, 0, TRUE
);

-- ============================================================================
-- APPLICATION INVARIANTS (enforce primarily in Spring service/transaction layer)
-- ============================================================================
-- 1. TEAM is not a users.role. Team permissions come from ACTIVE team_members.
-- 2. Only ADMIN creates teams and adds/removes team members unless policy changes.
-- 3. Only ACTIVE team members may create/submit stories for that team.
-- 4. Stories must pass moderation before status=PUBLISHED.
-- 5. Genres referenced by story_genres cannot be deleted (ON DELETE RESTRICT).
-- 6. Wallet mutations must use DB transactions + row locking and append ledger rows.
-- 7. Client-provided balances, prices, roles, ownership, and ad limits are untrusted.
-- 8. reports.target_id and reference_id fields are polymorphic; validate in service.
-- 9. Cached counters are derived data; update transactionally/eventually and reconcile.
-- 10. For existing production DBs, represent changes as new forward-only Flyway files.
