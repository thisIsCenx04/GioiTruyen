CREATE TABLE IF NOT EXISTS story_labels (
    story_id CHAR(36) NOT NULL,
    label VARCHAR(40) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (story_id, label),
    INDEX ix_story_labels_label (label, story_id),
    CONSTRAINT fk_story_labels_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS story_revenue_policies (
    story_id CHAR(36) PRIMARY KEY,
    exclusive BOOLEAN NOT NULL,
    author_share_bps INT NOT NULL,
    admin_share_bps INT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_story_revenue_share
        CHECK (author_share_bps + admin_share_bps = 10000),
    CONSTRAINT fk_story_revenue_policy_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS story_engagement_metrics (
    story_id CHAR(36) PRIMARY KEY,
    donation_xu BIGINT NOT NULL DEFAULT 0,
    recommendation_count BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_story_engagement_metrics_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS team_applications (
    id CHAR(36) PRIMARY KEY,
    requester_user_id CHAR(36) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    state VARCHAR(40) NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL,
    reviewed_at TIMESTAMP(6),
    reviewed_by CHAR(36),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_team_applications_slug UNIQUE (slug),
    CONSTRAINT fk_team_applications_requester
        FOREIGN KEY (requester_user_id) REFERENCES users(id),
    CONSTRAINT fk_team_applications_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES users(id)
);
