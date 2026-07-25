CREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    email_normalized VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    state VARCHAR(40) NOT NULL,
    security_version BIGINT NOT NULL DEFAULT 1,
    accepted_consent_version VARCHAR(40) NOT NULL,
    consent_accepted_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email_normalized)
);

CREATE TABLE user_roles (
    user_id CHAR(36) NOT NULL,
    role VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE user_profiles (
    user_id CHAR(36) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    bio VARCHAR(1000) NOT NULL DEFAULT '',
    avatar_media_id CHAR(36),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_profiles_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE refresh_token_families (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    security_version BIGINT NOT NULL,
    generation INT NOT NULL DEFAULT 0,
    current_token_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6),
    revoke_reason VARCHAR(80),
    version BIGINT NOT NULL DEFAULT 0,
    INDEX ix_refresh_families_user (user_id, created_at),
    CONSTRAINT fk_refresh_families_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE refresh_token_history (
    family_id CHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    used_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (family_id, token_hash),
    CONSTRAINT uk_refresh_history_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_history_family
        FOREIGN KEY (family_id)
        REFERENCES refresh_token_families(id) ON DELETE CASCADE
);

CREATE TABLE password_reset_tokens (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_password_reset_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE email_verification_tokens (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_email_verification_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE mfa_factors (
    user_id CHAR(36) PRIMARY KEY,
    protected_secret TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    activated_at TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_mfa_factor_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE mfa_recovery_codes (
    user_id CHAR(36) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6),
    PRIMARY KEY (user_id, code_hash),
    CONSTRAINT fk_mfa_recovery_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE reauthentication_grants (
    id CHAR(36) PRIMARY KEY,
    token_hash VARCHAR(255) NOT NULL,
    actor_id CHAR(36) NOT NULL,
    scope VARCHAR(80) NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_reauthentication_hash UNIQUE (token_hash),
    CONSTRAINT fk_reauthentication_actor
        FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE teams (
    id CHAR(36) PRIMARY KEY,
    slug VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    owner_user_id CHAR(36) NOT NULL,
    state VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_teams_slug UNIQUE (slug),
    CONSTRAINT fk_teams_owner
        FOREIGN KEY (owner_user_id) REFERENCES users(id)
);

CREATE TABLE team_memberships (
    team_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    role VARCHAR(30) NOT NULL,
    permissions JSON NOT NULL,
    state VARCHAR(30) NOT NULL,
    joined_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (team_id, user_id),
    CONSTRAINT fk_memberships_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_memberships_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE team_invitations (
    id CHAR(36) PRIMARY KEY,
    team_id CHAR(36) NOT NULL,
    target_user_id CHAR(36) NOT NULL,
    invited_by CHAR(36) NOT NULL,
    permissions JSON NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    state VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    accepted_at TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_team_invitation_token UNIQUE (token_hash),
    CONSTRAINT uk_team_invitation_idempotency
        UNIQUE (team_id, idempotency_key),
    CONSTRAINT fk_team_invitation_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_invitation_target
        FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_invitation_actor
        FOREIGN KEY (invited_by) REFERENCES users(id)
);

CREATE TABLE team_follows (
    team_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (team_id, user_id),
    CONSTRAINT fk_team_follows_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_follows_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE team_follow_counters (
    team_id CHAR(36) PRIMARY KEY,
    follower_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_team_follow_counter_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);

CREATE TABLE categories (
    id CHAR(36) PRIMARY KEY,
    slug VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    group_key VARCHAR(40) NOT NULL,
    group_order INT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT uk_categories_slug UNIQUE (slug)
);

CREATE TABLE stories (
    id CHAR(36) PRIMARY KEY,
    team_id CHAR(36) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    title VARCHAR(240) NOT NULL,
    synopsis TEXT NOT NULL,
    cover_url VARCHAR(1000),
    author_name VARCHAR(160) NOT NULL DEFAULT '',
    origin VARCHAR(30) NOT NULL,
    language VARCHAR(20) NOT NULL,
    completion_status VARCHAR(30) NOT NULL,
    workflow_status VARCHAR(40) NOT NULL,
    current_revision CHAR(36) NOT NULL,
    cover_asset_id CHAR(36),
    published_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    FULLTEXT INDEX ft_stories_search (title, synopsis, author_name),
    CONSTRAINT uk_stories_slug UNIQUE (slug),
    CONSTRAINT fk_stories_team
        FOREIGN KEY (team_id) REFERENCES teams(id)
);

CREATE TABLE story_aliases (
    story_id CHAR(36) NOT NULL,
    alias VARCHAR(200) NOT NULL,
    PRIMARY KEY (story_id, alias),
    CONSTRAINT fk_story_aliases_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE TABLE story_categories (
    story_id CHAR(36) NOT NULL,
    category_id CHAR(36) NOT NULL,
    PRIMARY KEY (story_id, category_id),
    CONSTRAINT fk_story_categories_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_categories_category
        FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE TABLE chapters (
    id CHAR(36) PRIMARY KEY,
    story_id CHAR(36) NOT NULL,
    team_id CHAR(36) NOT NULL,
    chapter_number INT NOT NULL,
    slug VARCHAR(100) NOT NULL,
    title VARCHAR(240) NOT NULL,
    workflow_status VARCHAR(40) NOT NULL,
    current_revision CHAR(36) NOT NULL,
    scheduled_at TIMESTAMP(6),
    published_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_chapters_story_number
        UNIQUE (story_id, chapter_number),
    CONSTRAINT fk_chapters_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE TABLE chapter_revisions (
    id CHAR(36) PRIMARY KEY,
    chapter_id CHAR(36) NOT NULL,
    revision_no BIGINT NOT NULL,
    content_html MEDIUMTEXT NOT NULL,
    plain_text MEDIUMTEXT NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_chapter_revision_no UNIQUE (chapter_id, revision_no),
    CONSTRAINT fk_chapter_revisions_chapter
        FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
);

CREATE TABLE reading_progress (
    user_id CHAR(36) NOT NULL,
    story_id CHAR(36) NOT NULL,
    chapter_id CHAR(36) NOT NULL,
    position DOUBLE NOT NULL DEFAULT 0,
    device_updated_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, story_id),
    CONSTRAINT fk_reading_progress_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reading_progress_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_reading_progress_chapter
        FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
);

CREATE TABLE reading_sessions (
    id CHAR(36) PRIMARY KEY,
    story_id CHAR(36) NOT NULL,
    chapter_id CHAR(36) NOT NULL,
    actor_type VARCHAR(30) NOT NULL,
    actor_ref VARCHAR(160) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    purge_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(40) NOT NULL,
    last_sequence BIGINT NOT NULL DEFAULT 0,
    completion_id CHAR(36),
    completed_at TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX ix_reading_sessions_expiry (status, expires_at),
    CONSTRAINT fk_reading_sessions_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_reading_sessions_chapter
        FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
);

CREATE TABLE reading_session_batches (
    session_id CHAR(36) NOT NULL,
    batch_id CHAR(36) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (session_id, batch_id),
    CONSTRAINT fk_reading_batches_session
        FOREIGN KEY (session_id) REFERENCES reading_sessions(id)
        ON DELETE CASCADE
);

CREATE TABLE comments (
    id CHAR(36) PRIMARY KEY,
    story_id CHAR(36) NOT NULL,
    chapter_id CHAR(36),
    user_id CHAR(36) NOT NULL,
    parent_id CHAR(36),
    body VARCHAR(4000) NOT NULL,
    state VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    INDEX ix_comments_story_created (story_id, created_at),
    CONSTRAINT fk_comments_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_chapter
        FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_comments_parent
        FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE CASCADE
);

CREATE TABLE moderation_cases (
    id CHAR(36) PRIMARY KEY,
    target_type VARCHAR(40) NOT NULL,
    target_id CHAR(36) NOT NULL,
    case_type VARCHAR(40) NOT NULL,
    state VARCHAR(40) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    assigned_to CHAR(36),
    summary VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    INDEX ix_moderation_queue (state, case_type, priority, created_at),
    CONSTRAINT fk_moderation_assignee
        FOREIGN KEY (assigned_to) REFERENCES users(id)
);

CREATE TABLE wallets (
    user_id CHAR(36) PRIMARY KEY,
    available_xu BIGINT NOT NULL DEFAULT 0,
    pending_xu BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_wallets_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE ledger_entries (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    entry_type VARCHAR(50) NOT NULL,
    amount_xu BIGINT NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    INDEX ix_ledger_user_created (user_id, created_at),
    CONSTRAINT fk_ledger_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE notifications (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(240) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    target_url VARCHAR(1000),
    read_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    INDEX ix_notifications_user_created (user_id, created_at),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE outbox_messages (
    id CHAR(36) PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    event_version INT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    trace_context JSON NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    actor_id VARCHAR(100),
    team_id VARCHAR(100),
    content_type VARCHAR(80) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    lease_owner VARCHAR(100),
    lease_until TIMESTAMP(6),
    last_error VARCHAR(100),
    created_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6),
    INDEX ix_outbox_claim (
        status, next_attempt_at, lease_until, id
    )
);

CREATE TABLE inbox_receipts (
    id VARCHAR(180) PRIMARY KEY,
    consumer VARCHAR(64) NOT NULL,
    event_id CHAR(36) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    event_version INT NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_inbox_consumer_event UNIQUE (consumer, event_id)
);
