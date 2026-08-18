-- Create story_combo_purchases table for storing full story combo purchases.
CREATE TABLE IF NOT EXISTS story_combo_purchases (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    story_id VARCHAR(36) NOT NULL,
    price_xu BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_story_combo UNIQUE (user_id, story_id)
);
