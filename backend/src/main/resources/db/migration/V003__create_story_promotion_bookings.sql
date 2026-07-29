CREATE TABLE story_promotion_bookings (
    id CHAR(36) PRIMARY KEY,
    story_id CHAR(36) NOT NULL,
    team_id CHAR(36) NOT NULL,
    slot_position INT NOT NULL,
    tag_label VARCHAR(40) NOT NULL DEFAULT 'Nổi bật',
    price_xu_per_day BIGINT NOT NULL,
    booked_days INT NOT NULL,
    total_cost_xu BIGINT NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    state VARCHAR(30) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    INDEX ix_story_promotions_home (state, starts_at, ends_at, slot_position),
    CONSTRAINT uk_story_promotion_slot UNIQUE (slot_position, starts_at, ends_at),
    CONSTRAINT fk_story_promotion_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_promotion_team
        FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);
