-- ============================================================
-- Web Truyen - PostgreSQL Database Schema
-- File: entity.sql
-- Target: PostgreSQL 14+
-- ============================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- ENUM TYPES
-- ============================================================

CREATE TYPE user_role AS ENUM ('READER', 'ADMIN');
CREATE TYPE user_status AS ENUM ('ACTIVE', 'BANNED', 'SUSPENDED');

CREATE TYPE auth_provider AS ENUM ('LOCAL', 'GOOGLE');

CREATE TYPE team_status AS ENUM ('ACTIVE', 'SUSPENDED', 'DISABLED');
CREATE TYPE team_member_role AS ENUM ('OWNER', 'MANAGER', 'EDITOR', 'MEMBER');
CREATE TYPE team_member_status AS ENUM ('ACTIVE', 'REMOVED');

CREATE TYPE story_content_type AS ENUM ('TEXT', 'AUDIO', 'TEXT_AUDIO');
CREATE TYPE story_status AS ENUM ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'HIDDEN');
CREATE TYPE story_progress_status AS ENUM ('ONGOING', 'COMPLETED', 'PAUSED');

CREATE TYPE review_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');

CREATE TYPE chapter_access_type AS ENUM ('FREE', 'PAID');
CREATE TYPE chapter_status AS ENUM ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'HIDDEN');

CREATE TYPE generic_content_status AS ENUM ('VISIBLE', 'HIDDEN', 'DELETED');

CREATE TYPE report_target_type AS ENUM ('STORY', 'CHAPTER', 'COMMENT');
CREATE TYPE report_status AS ENUM ('OPEN', 'PROCESSING', 'RESOLVED', 'REJECTED');

CREATE TYPE currency_code AS ENUM ('COIN', 'GEM');

CREATE TYPE wallet_transaction_type AS ENUM (
    'DEPOSIT',
    'PURCHASE',
    'DONATION',
    'RECOMMENDATION',
    'DAILY_REWARD',
    'REFERRAL_REWARD',
    'REFUND',
    'ADMIN_ADJUSTMENT'
);

CREATE TYPE payment_method_type AS ENUM (
    'BANK_TRANSFER',
    'QR',
    'PAYPAL',
    'OTHER'
);

CREATE TYPE payment_status AS ENUM (
    'PENDING',
    'PAID',
    'FAILED',
    'CANCELLED',
    'REFUNDED'
);

CREATE TYPE purchase_type AS ENUM (
    'SINGLE_CHAPTER',
    'CHAPTER_RANGE',
    'FULL_STORY'
);

CREATE TYPE team_ledger_type AS ENUM (
    'STORY_PURCHASE',
    'DONATION',
    'ADJUSTMENT',
    'WITHDRAWAL'
);

CREATE TYPE ranking_type AS ENUM (
    'GEM_RECOMMENDATION',
    'COIN_REVENUE',
    'VIEWS'
);

CREATE TYPE ranking_period AS ENUM (
    'DAILY',
    'WEEKLY',
    'MONTHLY',
    'ALL_TIME'
);

CREATE TYPE notification_type AS ENUM (
    'SYSTEM',
    'ACCOUNT',
    'ADMIN',
    'STORY_PUBLISHED',
    'NEW_CHAPTER',
    'TEAM_UPDATE',
    'PAYMENT',
    'PURCHASE',
    'REWARD'
);

CREATE TYPE mission_type AS ENUM (
    'LOGIN',
    'READ_CHAPTER',
    'VIEW_STORY',
    'FAVORITE_STORY',
    'COMMENT',
    'OTHER'
);

CREATE TYPE theme_mode AS ENUM ('LIGHT', 'DARK', 'SYSTEM');

CREATE TYPE site_document_type AS ENUM (
    'TERMS',
    'PRIVACY',
    'COMMUNITY_RULES',
    'TEAM_RULES',
    'COPYRIGHT'
);

CREATE TYPE advertisement_type AS ENUM (
    'BANNER',
    'POPUP',
    'AFFILIATE_REDIRECT'
);

CREATE TYPE advertisement_placement AS ENUM (
    'STORY_OPEN',
    'STORY_DETAIL',
    'READER',
    'HOME',
    'SIDEBAR'
);

CREATE TYPE ad_event_type AS ENUM (
    'IMPRESSION',
    'CLICK',
    'REDIRECT'
);

-- ============================================================
-- COMMON TRIGGER
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- AUTH / USER
-- ============================================================

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(100) UNIQUE,
    password_hash TEXT,
    display_name VARCHAR(150),
    avatar_url TEXT,
    role user_role NOT NULL DEFAULT 'READER',
    status user_status NOT NULL DEFAULT 'ACTIVE',
    email_verified_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE auth_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider auth_provider NOT NULL,
    provider_account_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(provider, provider_account_id)
);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_profiles (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    bio TEXT,
    cover_url TEXT,
    gender VARCHAR(30),
    birthday DATE,
    website_url TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_settings (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    theme theme_mode NOT NULL DEFAULT 'SYSTEM',
    reader_font_size SMALLINT NOT NULL DEFAULT 18 CHECK (reader_font_size BETWEEN 10 AND 40),
    reader_font_family VARCHAR(100),
    reader_line_height NUMERIC(4,2) NOT NULL DEFAULT 1.70 CHECK (reader_line_height BETWEEN 1.00 AND 3.00),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- TEAM
-- ============================================================

CREATE TABLE teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(200) NOT NULL UNIQUE,
    avatar_url TEXT,
    cover_url TEXT,
    description TEXT,
    status team_status NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE team_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    member_role team_member_role NOT NULL DEFAULT 'MEMBER',
    status team_member_status NOT NULL DEFAULT 'ACTIVE',
    added_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    removed_at TIMESTAMPTZ,
    UNIQUE(team_id, user_id)
);

CREATE TABLE team_follows (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(user_id, team_id)
);

-- ============================================================
-- STORY / GENRE
-- ============================================================

CREATE TABLE genres (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL UNIQUE,
    slug VARCHAR(140) NOT NULL UNIQUE,
    description TEXT,
    icon_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE stories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,

    title VARCHAR(255) NOT NULL,
    slug VARCHAR(280) NOT NULL UNIQUE,
    original_title VARCHAR(255),
    original_author VARCHAR(255),
    short_description VARCHAR(500),
    description TEXT,

    cover_url TEXT,
    banner_url TEXT,

    content_type story_content_type NOT NULL DEFAULT 'TEXT',
    status story_status NOT NULL DEFAULT 'DRAFT',
    progress_status story_progress_status NOT NULL DEFAULT 'ONGOING',
    age_rating VARCHAR(30),

    published_at TIMESTAMPTZ,
    last_chapter_at TIMESTAMPTZ,

    view_count_cache BIGINT NOT NULL DEFAULT 0 CHECK (view_count_cache >= 0),
    follow_count_cache BIGINT NOT NULL DEFAULT 0 CHECK (follow_count_cache >= 0),
    favorite_count_cache BIGINT NOT NULL DEFAULT 0 CHECK (favorite_count_cache >= 0),
    recommendation_gem_cache BIGINT NOT NULL DEFAULT 0 CHECK (recommendation_gem_cache >= 0),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE story_genres (
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    genre_id UUID NOT NULL REFERENCES genres(id) ON DELETE RESTRICT,
    PRIMARY KEY(story_id, genre_id)
);

CREATE TABLE story_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    submitted_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    status review_status NOT NULL DEFAULT 'PENDING',
    admin_note TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ
);

CREATE TABLE chapters (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    chapter_number NUMERIC(10,2) NOT NULL,
    title VARCHAR(255),
    slug VARCHAR(280) NOT NULL,
    content TEXT,
    short_description VARCHAR(500),
    access_type chapter_access_type NOT NULL DEFAULT 'FREE',
    coin_price BIGINT NOT NULL DEFAULT 0 CHECK (coin_price >= 0),
    status chapter_status NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    UNIQUE(story_id, chapter_number),
    UNIQUE(story_id, slug),

    CONSTRAINT chapter_access_price_check CHECK (
        (access_type = 'FREE' AND coin_price = 0)
        OR
        (access_type = 'PAID' AND coin_price > 0)
    )
);

CREATE TABLE chapter_audios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_id UUID NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    audio_url TEXT NOT NULL,
    duration_seconds INTEGER CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    file_size BIGINT CHECK (file_size IS NULL OR file_size >= 0),
    narrator VARCHAR(255),
    status generic_content_status NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audio_listens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chapter_audio_id UUID NOT NULL REFERENCES chapter_audios(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    session_id VARCHAR(255),
    listened_seconds INTEGER NOT NULL DEFAULT 0 CHECK (listened_seconds >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ENGAGEMENT / VIEW / LIBRARY
-- ============================================================

CREATE TABLE story_views (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    chapter_id UUID REFERENCES chapters(id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    session_id VARCHAR(255),
    ip_hash TEXT,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE story_daily_stats (
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    stat_date DATE NOT NULL,
    views BIGINT NOT NULL DEFAULT 0 CHECK (views >= 0),
    unique_views BIGINT NOT NULL DEFAULT 0 CHECK (unique_views >= 0),
    audio_listens BIGINT NOT NULL DEFAULT 0 CHECK (audio_listens >= 0),
    favorites BIGINT NOT NULL DEFAULT 0 CHECK (favorites >= 0),
    follows BIGINT NOT NULL DEFAULT 0 CHECK (follows >= 0),
    recommendations BIGINT NOT NULL DEFAULT 0 CHECK (recommendations >= 0),
    coin_revenue BIGINT NOT NULL DEFAULT 0 CHECK (coin_revenue >= 0),
    PRIMARY KEY(story_id, stat_date)
);

CREATE TABLE library_items (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(user_id, story_id)
);

CREATE TABLE story_follows (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    notify_new_chapter BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(user_id, story_id)
);

-- ============================================================
-- COMMENTS / REPORTS
-- ============================================================

CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    chapter_id UUID REFERENCES chapters(id) ON DELETE CASCADE,
    parent_id UUID REFERENCES comments(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    status generic_content_status NOT NULL DEFAULT 'VISIBLE',
    like_count_cache BIGINT NOT NULL DEFAULT 0 CHECK (like_count_cache >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE comment_likes (
    comment_id UUID NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(comment_id, user_id)
);

CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_type report_target_type NOT NULL,
    target_id UUID NOT NULL,
    report_type VARCHAR(100) NOT NULL,
    description TEXT,
    status report_status NOT NULL DEFAULT 'OPEN',
    handled_by UUID REFERENCES users(id) ON DELETE SET NULL,
    admin_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

-- ============================================================
-- WALLET / PAYMENT
-- ============================================================

CREATE TABLE currencies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code currency_code NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL
);

INSERT INTO currencies(code, name)
VALUES
    ('COIN', 'Xu'),
    ('GEM', 'Ngọc')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    coin_balance BIGINT NOT NULL DEFAULT 0 CHECK (coin_balance >= 0),
    gem_balance BIGINT NOT NULL DEFAULT 0 CHECK (gem_balance >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    currency currency_code NOT NULL,
    type wallet_transaction_type NOT NULL,
    amount BIGINT NOT NULL CHECK (amount <> 0),
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    reference_type VARCHAR(80),
    reference_id UUID,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payment_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    type payment_method_type NOT NULL,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    instructions TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE deposit_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    price_vnd BIGINT NOT NULL CHECK (price_vnd > 0),
    coin_amount BIGINT NOT NULL CHECK (coin_amount >= 0),
    gem_amount BIGINT NOT NULL CHECK (gem_amount >= 0),
    bonus_coin BIGINT NOT NULL DEFAULT 0 CHECK (bonus_coin >= 0),
    bonus_gem BIGINT NOT NULL DEFAULT 0 CHECK (bonus_gem >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    payment_method_id UUID NOT NULL REFERENCES payment_methods(id) ON DELETE RESTRICT,
    deposit_package_id UUID REFERENCES deposit_packages(id) ON DELETE SET NULL,
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    coin_received BIGINT NOT NULL DEFAULT 0 CHECK (coin_received >= 0),
    gem_received BIGINT NOT NULL DEFAULT 0 CHECK (gem_received >= 0),
    transaction_code VARCHAR(255),
    external_transaction_id VARCHAR(255),
    status payment_status NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- PURCHASE / UNLOCK
-- ============================================================

CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE RESTRICT,
    purchase_type purchase_type NOT NULL,
    total_coin BIGINT NOT NULL CHECK (total_coin >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE purchase_order_items (
    order_id UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL REFERENCES chapters(id) ON DELETE RESTRICT,
    coin_price BIGINT NOT NULL CHECK (coin_price >= 0),
    PRIMARY KEY(order_id, chapter_id)
);

CREATE TABLE chapter_unlocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    purchase_order_id UUID REFERENCES purchase_orders(id) ON DELETE SET NULL,
    coin_paid BIGINT NOT NULL CHECK (coin_paid >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, chapter_id)
);

-- ============================================================
-- DONATION / TEAM REVENUE
-- ============================================================

CREATE TABLE donations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
    story_id UUID REFERENCES stories(id) ON DELETE SET NULL,
    gross_coin BIGINT NOT NULL CHECK (gross_coin > 0),
    commission_rate NUMERIC(5,2) NOT NULL CHECK (commission_rate BETWEEN 0 AND 100),
    commission_coin BIGINT NOT NULL CHECK (commission_coin >= 0),
    team_net_coin BIGINT NOT NULL CHECK (team_net_coin >= 0),
    message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT donation_sum_check CHECK (commission_coin + team_net_coin = gross_coin)
);

CREATE TABLE team_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    type team_ledger_type NOT NULL,
    gross_coin BIGINT NOT NULL DEFAULT 0 CHECK (gross_coin >= 0),
    platform_fee_coin BIGINT NOT NULL DEFAULT 0 CHECK (platform_fee_coin >= 0),
    net_coin BIGINT NOT NULL DEFAULT 0 CHECK (net_coin >= 0),
    reference_type VARCHAR(80),
    reference_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- GEM RECOMMENDATION / RANKING
-- ============================================================

CREATE TABLE story_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    gem_amount BIGINT NOT NULL CHECK (gem_amount > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE ranking_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ranking_type ranking_type NOT NULL,
    story_id UUID NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    score BIGINT NOT NULL DEFAULT 0,
    rank INTEGER NOT NULL CHECK (rank > 0),
    period ranking_period NOT NULL,
    snapshot_date DATE NOT NULL,
    UNIQUE(ranking_type, story_id, period, snapshot_date)
);

-- ============================================================
-- NOTIFICATIONS
-- ============================================================

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type notification_type NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    target_type VARCHAR(80),
    target_id UUID,
    target_url TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    story_updates BOOLEAN NOT NULL DEFAULT TRUE,
    team_updates BOOLEAN NOT NULL DEFAULT TRUE,
    system_updates BOOLEAN NOT NULL DEFAULT TRUE,
    payment_updates BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- DAILY MISSIONS
-- ============================================================

CREATE TABLE missions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    description TEXT,
    mission_type mission_type NOT NULL,
    target_count INTEGER NOT NULL DEFAULT 1 CHECK (target_count > 0),
    reward_coin BIGINT NOT NULL DEFAULT 0 CHECK (reward_coin >= 0),
    reward_gem BIGINT NOT NULL DEFAULT 0 CHECK (reward_gem >= 0),
    daily_limit INTEGER NOT NULL DEFAULT 1 CHECK (daily_limit > 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_mission_progress (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mission_id UUID NOT NULL REFERENCES missions(id) ON DELETE CASCADE,
    progress_date DATE NOT NULL DEFAULT CURRENT_DATE,
    progress INTEGER NOT NULL DEFAULT 0 CHECK (progress >= 0),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    claimed BOOLEAN NOT NULL DEFAULT FALSE,
    claimed_at TIMESTAMPTZ,
    PRIMARY KEY(user_id, mission_id, progress_date)
);

-- ============================================================
-- REFERRAL
-- ============================================================

CREATE TABLE referral_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE referrals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referrer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    referred_user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    qualified_at TIMESTAMPTZ,
    reward_coin BIGINT NOT NULL DEFAULT 0 CHECK (reward_coin >= 0),
    reward_gem BIGINT NOT NULL DEFAULT 0 CHECK (reward_gem >= 0),
    rewarded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT referral_self_check CHECK (referrer_id <> referred_user_id)
);

-- ============================================================
-- COMMUNITY
-- ============================================================

CREATE TABLE community_rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    status generic_content_status NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE community_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES community_rooms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    reply_to_id UUID REFERENCES community_messages(id) ON DELETE SET NULL,
    status generic_content_status NOT NULL DEFAULT 'VISIBLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- SITE SETTINGS / DOCUMENTS
-- ============================================================

CREATE TABLE site_settings (
    key VARCHAR(150) PRIMARY KEY,
    value JSONB NOT NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE site_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type site_document_type NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(type, version)
);

-- ============================================================
-- ADVERTISEMENT / AFFILIATE
-- ============================================================

CREATE TABLE advertisements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(180) NOT NULL,
    type advertisement_type NOT NULL,
    image_url TEXT,
    target_url TEXT NOT NULL,
    placement advertisement_placement NOT NULL,
    trigger_every_n_views INTEGER CHECK (
        trigger_every_n_views IS NULL OR trigger_every_n_views > 0
    ),
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ad_date_check CHECK (
        end_at IS NULL OR start_at IS NULL OR end_at > start_at
    )
);

CREATE TABLE ad_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    advertisement_id UUID NOT NULL REFERENCES advertisements(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    session_id VARCHAR(255),
    story_id UUID REFERENCES stories(id) ON DELETE SET NULL,
    event_type ad_event_type NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- ADMIN AUDIT
-- ============================================================

CREATE TABLE admin_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(120) NOT NULL,
    entity_id UUID,
    old_data JSONB,
    new_data JSONB,
    ip_hash TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- INDEXES
-- ============================================================

CREATE INDEX idx_users_role_status
    ON users(role, status);

CREATE INDEX idx_auth_accounts_user
    ON auth_accounts(user_id);

CREATE INDEX idx_team_members_user_status
    ON team_members(user_id, status);

CREATE INDEX idx_team_members_team_status
    ON team_members(team_id, status);

CREATE INDEX idx_team_follows_team
    ON team_follows(team_id);

CREATE INDEX idx_stories_team
    ON stories(team_id);

CREATE INDEX idx_stories_status_published
    ON stories(status, published_at DESC);

CREATE INDEX idx_stories_progress
    ON stories(progress_status);

CREATE INDEX idx_stories_view_cache
    ON stories(view_count_cache DESC);

CREATE INDEX idx_stories_recommendation_cache
    ON stories(recommendation_gem_cache DESC);

CREATE INDEX idx_story_genres_genre
    ON story_genres(genre_id);

CREATE INDEX idx_story_reviews_story_status
    ON story_reviews(story_id, status);

CREATE INDEX idx_chapters_story_status_number
    ON chapters(story_id, status, chapter_number);

CREATE INDEX idx_audio_listens_audio_date
    ON audio_listens(chapter_audio_id, created_at DESC);

CREATE INDEX idx_story_views_story_date
    ON story_views(story_id, viewed_at DESC);

CREATE INDEX idx_story_views_user
    ON story_views(user_id);

CREATE INDEX idx_story_views_session
    ON story_views(session_id);

CREATE INDEX idx_library_story
    ON library_items(story_id);

CREATE INDEX idx_story_follows_story
    ON story_follows(story_id);

CREATE INDEX idx_comments_story_date
    ON comments(story_id, created_at DESC);

CREATE INDEX idx_comments_chapter_date
    ON comments(chapter_id, created_at DESC);

CREATE INDEX idx_comments_parent
    ON comments(parent_id);

CREATE INDEX idx_reports_status_date
    ON reports(status, created_at DESC);

CREATE INDEX idx_wallet_transactions_user_date
    ON wallet_transactions(user_id, created_at DESC);

CREATE INDEX idx_wallet_transactions_reference
    ON wallet_transactions(reference_type, reference_id);

CREATE INDEX idx_payment_methods_active
    ON payment_methods(is_active, sort_order);

CREATE INDEX idx_payments_user_date
    ON payments(user_id, created_at DESC);

CREATE INDEX idx_payments_status
    ON payments(status, created_at DESC);

CREATE UNIQUE INDEX idx_payments_external_transaction_unique
    ON payments(external_transaction_id)
    WHERE external_transaction_id IS NOT NULL;

CREATE INDEX idx_purchase_orders_user_date
    ON purchase_orders(user_id, created_at DESC);

CREATE INDEX idx_purchase_orders_story
    ON purchase_orders(story_id);

CREATE INDEX idx_chapter_unlocks_chapter
    ON chapter_unlocks(chapter_id);

CREATE INDEX idx_donations_team_date
    ON donations(team_id, created_at DESC);

CREATE INDEX idx_donations_story_date
    ON donations(story_id, created_at DESC);

CREATE INDEX idx_team_ledger_team_date
    ON team_ledger(team_id, created_at DESC);

CREATE INDEX idx_story_recommendations_story_date
    ON story_recommendations(story_id, created_at DESC);

CREATE INDEX idx_ranking_lookup
    ON ranking_snapshots(ranking_type, period, snapshot_date, rank);

CREATE INDEX idx_notifications_user_read_date
    ON notifications(user_id, is_read, created_at DESC);

CREATE INDEX idx_mission_progress_user_date
    ON user_mission_progress(user_id, progress_date DESC);

CREATE INDEX idx_community_messages_room_date
    ON community_messages(room_id, created_at DESC);

CREATE INDEX idx_ads_active_placement
    ON advertisements(is_active, placement);

CREATE INDEX idx_ad_events_ad_date
    ON ad_events(advertisement_id, created_at DESC);

CREATE INDEX idx_admin_audit_admin_date
    ON admin_audit_logs(admin_id, created_at DESC);

CREATE INDEX idx_admin_audit_entity
    ON admin_audit_logs(entity_type, entity_id);

-- ============================================================
-- UPDATED_AT TRIGGERS
-- ============================================================

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_user_profiles_updated_at
BEFORE UPDATE ON user_profiles
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_user_settings_updated_at
BEFORE UPDATE ON user_settings
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_teams_updated_at
BEFORE UPDATE ON teams
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_genres_updated_at
BEFORE UPDATE ON genres
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_stories_updated_at
BEFORE UPDATE ON stories
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_chapters_updated_at
BEFORE UPDATE ON chapters
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_comments_updated_at
BEFORE UPDATE ON comments
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_wallets_updated_at
BEFORE UPDATE ON wallets
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_payment_methods_updated_at
BEFORE UPDATE ON payment_methods
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_deposit_packages_updated_at
BEFORE UPDATE ON deposit_packages
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_notification_preferences_updated_at
BEFORE UPDATE ON notification_preferences
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_missions_updated_at
BEFORE UPDATE ON missions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_community_rooms_updated_at
BEFORE UPDATE ON community_rooms
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_site_documents_updated_at
BEFORE UPDATE ON site_documents
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_advertisements_updated_at
BEFORE UPDATE ON advertisements
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- DEFAULT SITE SETTINGS
-- Values are examples and can be changed in Admin Dashboard.
-- ============================================================

INSERT INTO site_settings(key, value)
VALUES
    ('donation_commission_percent', '{"value": 10}'),
    ('registration_enabled', '{"value": true}'),
    ('maintenance_mode', '{"value": false}'),
    ('daily_free_coin_limit', '{"value": 1000}'),
    ('default_theme', '{"value": "SYSTEM"}'),
    ('referral_reward', '{"coin": 0, "gem": 0}')
ON CONFLICT (key) DO NOTHING;

-- Example package requested:
-- 100,000 VND -> 90,000 Coin + 90,000 Gem
INSERT INTO deposit_packages(
    name,
    price_vnd,
    coin_amount,
    gem_amount,
    bonus_coin,
    bonus_gem,
    is_active
)
VALUES (
    '100K',
    100000,
    90000,
    90000,
    0,
    0,
    TRUE
);

COMMIT;

-- ============================================================
-- IMPORTANT APPLICATION RULES
-- ============================================================
-- 1. TEAM is NOT a users.role.
--    Team permissions come from team_members.
--
-- 2. Only ADMIN should create teams and add/remove team members.
--    Enforce in service/authorization layer.
--
-- 3. Only ACTIVE team members should be allowed to create/submit stories.
--
-- 4. A story must be approved before PUBLISHED.
--
-- 5. genres cannot be deleted while referenced because story_genres.genre_id
--    uses ON DELETE RESTRICT.
--
-- 6. wallets are cached balances.
--    wallet_transactions should be treated as the financial audit ledger.
--
-- 7. Coin/Gem changes should be executed inside database transactions
--    with row locking (SELECT ... FOR UPDATE) to prevent double spending.
--
-- 8. Gem balance must not be exposed as a public competitive metric.
--
-- 9. COIN_REVENUE ranking should only be exposed to ADMIN/internal APIs
--    if it is meant to remain hidden from public UI.
--
-- 10. reports.target_id and wallet_transactions.reference_id are polymorphic
--     references. Their existence must be validated in the application layer.
