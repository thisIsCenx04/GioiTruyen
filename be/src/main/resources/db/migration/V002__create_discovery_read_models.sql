CREATE TABLE home_read_models (
    locale VARCHAR(16) PRIMARY KEY,
    version VARCHAR(128) NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE home_read_model_stories (
    locale VARCHAR(16) NOT NULL,
    section_id VARCHAR(32) NOT NULL,
    section_type VARCHAR(24) NOT NULL,
    section_title VARCHAR(128) NOT NULL,
    position_index INT NOT NULL,
    story_id CHAR(36) NOT NULL,
    team_id CHAR(36) NOT NULL,
    slug VARCHAR(180) NOT NULL,
    title VARCHAR(255) NOT NULL,
    cover_asset_id CHAR(36),
    published_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (locale, section_id, position_index),
    CONSTRAINT fk_home_story_model
        FOREIGN KEY (locale) REFERENCES home_read_models(locale)
        ON DELETE CASCADE
);

CREATE INDEX idx_home_story_lookup
    ON home_read_model_stories(locale, section_id, position_index);

CREATE INDEX idx_story_public_search
    ON stories(workflow_status, published_at, title);
