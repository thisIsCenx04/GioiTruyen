-- ============================================================
-- Web Truyen - MySQL Database Schema (Auto Migration)
-- Generated from entity_real.sql
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(100) UNIQUE,
    password_hash TEXT,
    display_name VARCHAR(150),
    avatar_url TEXT,
    role ENUM('READER', 'ADMIN') NOT NULL DEFAULT 'READER',
    status ENUM('ACTIVE', 'BANNED', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    email_verified_at TIMESTAMP,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `auth_accounts`;
CREATE TABLE `auth_accounts` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider ENUM('LOCAL', 'GOOGLE') NOT NULL,
    provider_account_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `password_reset_tokens`;
CREATE TABLE `password_reset_tokens` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_profiles`;
CREATE TABLE `user_profiles` (
    user_id VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    bio TEXT,
    cover_url TEXT,
    gender VARCHAR(30),
    birthday DATE,
    website_url TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_settings`;
CREATE TABLE `user_settings` (
    user_id VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    theme ENUM('LIGHT', 'DARK', 'SYSTEM') NOT NULL DEFAULT 'SYSTEM',
    reader_font_size SMALLINT NOT NULL DEFAULT 18 CHECK (reader_font_size BETWEEN 10 AND 40),
    reader_font_family VARCHAR(100),
    reader_line_height NUMERIC(4,2) NOT NULL DEFAULT 1.70 CHECK (reader_line_height BETWEEN 1.00 AND 3.00),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `teams`;
CREATE TABLE `teams` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(200) NOT NULL UNIQUE,
    avatar_url TEXT,
    cover_url TEXT,
    description TEXT,
    status ENUM('ACTIVE', 'SUSPENDED', 'DISABLED') NOT NULL DEFAULT 'ACTIVE',
    created_by VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `team_members`;
CREATE TABLE `team_members` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    team_id VARCHAR(36) NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    member_role ENUM('OWNER', 'MANAGER', 'EDITOR', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
    status ENUM('ACTIVE', 'REMOVED') NOT NULL DEFAULT 'ACTIVE',
    added_by VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    removed_at TIMESTAMP,
    UNIQUE(team_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `team_follows`;
CREATE TABLE `team_follows` (
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id VARCHAR(36) NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY(user_id, team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `genres`;
CREATE TABLE `genres` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    name VARCHAR(120) NOT NULL UNIQUE,
    slug VARCHAR(140) NOT NULL UNIQUE,
    description TEXT,
    icon_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `stories`;
CREATE TABLE `stories` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    team_id VARCHAR(36) NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
    created_by VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(280) NOT NULL UNIQUE,
    original_title VARCHAR(255),
    original_author VARCHAR(255),
    short_description VARCHAR(500),
    description TEXT,
    cover_url TEXT,
    banner_url TEXT,
    content_type ENUM('TEXT', 'AUDIO', 'TEXT_AUDIO') NOT NULL DEFAULT 'TEXT',
    status ENUM('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'HIDDEN') NOT NULL DEFAULT 'DRAFT',
    progress_status ENUM('ONGOING', 'COMPLETED', 'PAUSED') NOT NULL DEFAULT 'ONGOING',
    age_rating VARCHAR(30),
    published_at TIMESTAMP,
    last_chapter_at TIMESTAMP,
    view_count_cache BIGINT NOT NULL DEFAULT 0 CHECK (view_count_cache >= 0),
    follow_count_cache BIGINT NOT NULL DEFAULT 0 CHECK (follow_count_cache >= 0),
    favorite_count_cache BIGINT NOT NULL DEFAULT 0 CHECK (favorite_count_cache >= 0),
    recommendation_gem_cache BIGINT NOT NULL DEFAULT 0 CHECK (recommendation_gem_cache >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `story_genres`;
CREATE TABLE `story_genres` (
    story_id VARCHAR(36) NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    genre_id VARCHAR(36) NOT NULL REFERENCES genres(id) ON DELETE RESTRICT,
    PRIMARY KEY(story_id, genre_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `story_reviews`;
CREATE TABLE `story_reviews` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    story_id VARCHAR(36) NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    submitted_by VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_by VARCHAR(36) REFERENCES users(id) ON DELETE RESTRICT,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    admin_note TEXT,
    submitted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `chapters`;
CREATE TABLE `chapters` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    story_id VARCHAR(36) NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    chapter_number NUMERIC(10,2) NOT NULL,
    title VARCHAR(255),
    slug VARCHAR(280) NOT NULL,
    content TEXT,
    short_description VARCHAR(500),
    access_type ENUM('FREE', 'PAID') NOT NULL DEFAULT 'FREE',
    coin_price BIGINT NOT NULL DEFAULT 0 CHECK (coin_price >= 0),
    status ENUM('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'HIDDEN') NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP,
    created_by VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(story_id, chapter_number),
    UNIQUE(story_id, slug),
    CONSTRAINT chapter_access_price_check CHECK (
    (access_type = 'FREE' AND coin_price = 0)
    OR
    (access_type = 'PAID' AND coin_price > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `chapter_audios`;
CREATE TABLE `chapter_audios` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    chapter_id VARCHAR(36) NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    audio_url TEXT NOT NULL,
    duration_seconds INTEGER CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    file_size BIGINT CHECK (file_size IS NULL OR file_size >= 0),
    narrator VARCHAR(255),
    status ENUM('VISIBLE', 'HIDDEN', 'DELETED') NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `audio_listens`;
CREATE TABLE `audio_listens` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    chapter_audio_id VARCHAR(36) NOT NULL REFERENCES chapter_audios(id) ON DELETE CASCADE,
    user_id VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    session_id VARCHAR(255),
    listened_seconds INTEGER NOT NULL DEFAULT 0 CHECK (listened_seconds >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `story_views`;
CREATE TABLE `story_views` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    story_id VARCHAR(36) NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    chapter_id VARCHAR(36) REFERENCES chapters(id) ON DELETE SET NULL,
    user_id VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    session_id VARCHAR(255),
    ip_hash TEXT,
    viewed_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `story_daily_stats`;
CREATE TABLE `story_daily_stats` (
    story_id VARCHAR(36) NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    stat_date DATE NOT NULL,
    views BIGINT NOT NULL DEFAULT 0 CHECK (views >= 0),
    unique_views BIGINT NOT NULL DEFAULT 0 CHECK (unique_views >= 0),
    audio_listens BIGINT NOT NULL DEFAULT 0 CHECK (audio_listens >= 0),
    favorites BIGINT NOT NULL DEFAULT 0 CHECK (favorites >= 0),
    follows BIGINT NOT NULL DEFAULT 0 CHECK (follows >= 0),
    recommendations BIGINT NOT NULL DEFAULT 0 CHECK (recommendations >= 0),
    coin_revenue BIGINT NOT NULL DEFAULT 0 CHECK (coin_revenue >= 0),
    PRIMARY KEY(story_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `library_items`;
CREATE TABLE `library_items` (
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id VARCHAR(36) NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY(user_id, story_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `story_follows`;
CREATE TABLE `story_follows` (
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id VARCHAR(36) NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    notify_new_chapter BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY(user_id, story_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `comments`;
CREATE TABLE `comments` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id VARCHAR(36) NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    chapter_id VARCHAR(36) REFERENCES chapters(id) ON DELETE CASCADE,
    parent_id VARCHAR(36) REFERENCES comments(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    status ENUM('VISIBLE', 'HIDDEN', 'DELETED') NOT NULL DEFAULT 'VISIBLE',
    like_count_cache BIGINT NOT NULL DEFAULT 0 CHECK (like_count_cache >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `comment_likes`;
CREATE TABLE `comment_likes` (
    comment_id VARCHAR(36) NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY(comment_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `reports`;
CREATE TABLE `reports` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    reporter_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type ENUM('STORY', 'CHAPTER', 'COMMENT') NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    report_type VARCHAR(100) NOT NULL,
    description TEXT,
    status ENUM('OPEN', 'PROCESSING', 'RESOLVED', 'REJECTED') NOT NULL DEFAULT 'OPEN',
    handled_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    admin_note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `currencies`;
CREATE TABLE `currencies` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    code ENUM('COIN', 'GEM') NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wallets`;
CREATE TABLE `wallets` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    coin_balance BIGINT NOT NULL DEFAULT 0 CHECK (coin_balance >= 0),
    gem_balance BIGINT NOT NULL DEFAULT 0 CHECK (gem_balance >= 0),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `wallet_transactions`;
CREATE TABLE `wallet_transactions` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    currency ENUM('COIN', 'GEM') NOT NULL,
    type ENUM('DEPOSIT',
    'PURCHASE',
    'DONATION',
    'RECOMMENDATION',
    'DAILY_REWARD',
    'REFERRAL_REWARD',
    'REFUND',
    'ADMIN_ADJUSTMENT') NOT NULL,
    amount BIGINT NOT NULL CHECK (amount <> 0),
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    reference_type VARCHAR(80),
    reference_id VARCHAR(36),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `payment_methods`;
CREATE TABLE `payment_methods` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    name VARCHAR(150) NOT NULL,
    type ENUM('BANK_TRANSFER',
    'QR',
    'PAYPAL',
    'OTHER') NOT NULL,
    config JSON NOT NULL DEFAULT '{}'::JSON,
    instructions TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `deposit_packages`;
CREATE TABLE `deposit_packages` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    name VARCHAR(150) NOT NULL,
    price_vnd BIGINT NOT NULL CHECK (price_vnd > 0),
    coin_amount BIGINT NOT NULL CHECK (coin_amount >= 0),
    gem_amount BIGINT NOT NULL CHECK (gem_amount >= 0),
    bonus_coin BIGINT NOT NULL DEFAULT 0 CHECK (bonus_coin >= 0),
    bonus_gem BIGINT NOT NULL DEFAULT 0 CHECK (bonus_gem >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `payments`;
CREATE TABLE `payments` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    payment_method_id VARCHAR(36) NOT NULL REFERENCES payment_methods(id) ON DELETE RESTRICT,
    deposit_package_id VARCHAR(36) REFERENCES deposit_packages(id) ON DELETE SET NULL,
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    coin_received BIGINT NOT NULL DEFAULT 0 CHECK (coin_received >= 0),
    gem_received BIGINT NOT NULL DEFAULT 0 CHECK (gem_received >= 0),
    transaction_code VARCHAR(255),
    external_transaction_id VARCHAR(255),
    status ENUM('PENDING',
    'PAID',
    'FAILED',
    'CANCELLED',
    'REFUNDED') NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `purchase_orders`;
CREATE TABLE `purchase_orders` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    story_id VARCHAR(36) NOT NULL REFERENCES stories(id) ON DELETE RESTRICT,
    ENUM('SINGLE_CHAPTER',
    'CHAPTER_RANGE',
    'FULL_STORY') ENUM('SINGLE_CHAPTER',
    'CHAPTER_RANGE',
    'FULL_STORY') NOT NULL,
    total_coin BIGINT NOT NULL CHECK (total_coin >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `purchase_order_items`;
CREATE TABLE `purchase_order_items` (
    order_id VARCHAR(36) NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    chapter_id VARCHAR(36) NOT NULL REFERENCES chapters(id) ON DELETE RESTRICT,
    coin_price BIGINT NOT NULL CHECK (coin_price >= 0),
    PRIMARY KEY(order_id, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `chapter_unlocks`;
CREATE TABLE `chapter_unlocks` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chapter_id VARCHAR(36) NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    purchase_order_id VARCHAR(36) REFERENCES purchase_orders(id) ON DELETE SET NULL,
    coin_paid BIGINT NOT NULL CHECK (coin_paid >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, chapter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `donations`;
CREATE TABLE `donations` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    team_id VARCHAR(36) NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
    story_id VARCHAR(36) REFERENCES stories(id) ON DELETE SET NULL,
    gross_coin BIGINT NOT NULL CHECK (gross_coin > 0),
    commission_rate NUMERIC(5,2) NOT NULL CHECK (commission_rate BETWEEN 0 AND 100),
    commission_coin BIGINT NOT NULL CHECK (commission_coin >= 0),
    team_net_coin BIGINT NOT NULL CHECK (team_net_coin >= 0),
    message VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT donation_sum_check CHECK (commission_coin + team_net_coin = gross_coin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `team_ledger`;
CREATE TABLE `team_ledger` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    team_id VARCHAR(36) NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    type ENUM('DONATION', 'CHAPTER_UNLOCK', 'WITHDRAWAL', 'PLATFORM_FEE') NOT NULL,
    gross_coin BIGINT NOT NULL DEFAULT 0 CHECK (gross_coin >= 0),
    platform_fee_coin BIGINT NOT NULL DEFAULT 0 CHECK (platform_fee_coin >= 0),
    net_coin BIGINT NOT NULL DEFAULT 0 CHECK (net_coin >= 0),
    reference_type VARCHAR(80),
    reference_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `story_recommendations`;
CREATE TABLE `story_recommendations` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    story_id VARCHAR(36) NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    gem_amount BIGINT NOT NULL CHECK (gem_amount > 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `ranking_snapshots`;
CREATE TABLE `ranking_snapshots` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    ENUM('GEM_RECOMMENDATION',
    'COIN_REVENUE',
    'VIEWS') ENUM('GEM_RECOMMENDATION',
    'COIN_REVENUE',
    'VIEWS') NOT NULL,
    story_id VARCHAR(36) NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    score BIGINT NOT NULL DEFAULT 0,
    rank INTEGER NOT NULL CHECK (rank > 0),
    period ENUM('DAILY',
    'WEEKLY',
    'MONTHLY',
    'ALL_TIME') NOT NULL,
    snapshot_date DATE NOT NULL,
    UNIQUE(ENUM('GEM_RECOMMENDATION',
    'COIN_REVENUE',
    'VIEWS'), story_id, period, snapshot_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `notifications`;
CREATE TABLE `notifications` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type ENUM('SYSTEM',
    'ACCOUNT',
    'ADMIN',
    'STORY_PUBLISHED',
    'NEW_CHAPTER',
    'TEAM_UPDATE',
    'PAYMENT',
    'PURCHASE',
    'REWARD') NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    target_type VARCHAR(80),
    target_id VARCHAR(36),
    target_url TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `notification_preferences`;
CREATE TABLE `notification_preferences` (
    user_id VARCHAR(36) PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    story_updates BOOLEAN NOT NULL DEFAULT TRUE,
    team_updates BOOLEAN NOT NULL DEFAULT TRUE,
    system_updates BOOLEAN NOT NULL DEFAULT TRUE,
    payment_updates BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `missions`;
CREATE TABLE `missions` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    description TEXT,
    ENUM('LOGIN',
    'READ_CHAPTER',
    'VIEW_STORY',
    'FAVORITE_STORY',
    'COMMENT',
    'OTHER') ENUM('LOGIN',
    'READ_CHAPTER',
    'VIEW_STORY',
    'FAVORITE_STORY',
    'COMMENT',
    'OTHER') NOT NULL,
    target_count INTEGER NOT NULL DEFAULT 1 CHECK (target_count > 0),
    reward_coin BIGINT NOT NULL DEFAULT 0 CHECK (reward_coin >= 0),
    reward_gem BIGINT NOT NULL DEFAULT 0 CHECK (reward_gem >= 0),
    daily_limit INTEGER NOT NULL DEFAULT 1 CHECK (daily_limit > 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `user_mission_progress`;
CREATE TABLE `user_mission_progress` (
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mission_id VARCHAR(36) NOT NULL REFERENCES missions(id) ON DELETE CASCADE,
    progress_date DATE NOT NULL DEFAULT CURRENT_DATE,
    progress INTEGER NOT NULL DEFAULT 0 CHECK (progress >= 0),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    claimed BOOLEAN NOT NULL DEFAULT FALSE,
    claimed_at TIMESTAMP,
    PRIMARY KEY(user_id, mission_id, progress_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `referral_codes`;
CREATE TABLE `referral_codes` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `referrals`;
CREATE TABLE `referrals` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    referrer_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    referred_user_id VARCHAR(36) NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    qualified_at TIMESTAMP,
    reward_coin BIGINT NOT NULL DEFAULT 0 CHECK (reward_coin >= 0),
    reward_gem BIGINT NOT NULL DEFAULT 0 CHECK (reward_gem >= 0),
    rewarded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT referral_self_check CHECK (referrer_id <> referred_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `community_rooms`;
CREATE TABLE `community_rooms` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    name VARCHAR(150) NOT NULL,
    status ENUM('VISIBLE', 'HIDDEN', 'DELETED') NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `community_messages`;
CREATE TABLE `community_messages` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    room_id VARCHAR(36) NOT NULL REFERENCES community_rooms(id) ON DELETE CASCADE,
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    reply_to_id VARCHAR(36) REFERENCES community_messages(id) ON DELETE SET NULL,
    status ENUM('VISIBLE', 'HIDDEN', 'DELETED') NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `site_settings`;
CREATE TABLE `site_settings` (
    key VARCHAR(150) PRIMARY KEY,
    value JSON NOT NULL,
    updated_by VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `site_documents`;
CREATE TABLE `site_documents` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    type ENUM('TERMS',
    'PRIVACY',
    'COMMUNITY_RULES',
    'TEAM_RULES',
    'COPYRIGHT') NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(type, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `advertisements`;
CREATE TABLE `advertisements` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    name VARCHAR(180) NOT NULL,
    type ENUM('BANNER',
    'POPUP',
    'AFFILIATE_REDIRECT') NOT NULL,
    image_url TEXT,
    target_url TEXT NOT NULL,
    placement ENUM('STORY_OPEN',
    'STORY_DETAIL',
    'READER',
    'HOME',
    'SIDEBAR') NOT NULL,
    trigger_every_n_views INTEGER CHECK (
    trigger_every_n_views IS NULL OR trigger_every_n_views > 0
    ),
    start_at TIMESTAMP,
    end_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ad_date_check CHECK (
    end_at IS NULL OR start_at IS NULL OR end_at > start_at
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `ad_events`;
CREATE TABLE `ad_events` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    advertisement_id VARCHAR(36) NOT NULL REFERENCES advertisements(id) ON DELETE CASCADE,
    user_id VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    session_id VARCHAR(255),
    story_id VARCHAR(36) REFERENCES stories(id) ON DELETE SET NULL,
    event_type ENUM('IMPRESSION',
    'CLICK',
    'REDIRECT') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TABLE IF EXISTS `admin_audit_logs`;
CREATE TABLE `admin_audit_logs` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    admin_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(120) NOT NULL,
    entity_id VARCHAR(36),
    old_data JSON,
    new_data JSON,
    ip_hash TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
