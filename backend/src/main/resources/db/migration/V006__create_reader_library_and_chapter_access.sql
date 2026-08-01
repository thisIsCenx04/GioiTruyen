CREATE TABLE IF NOT EXISTS story_library_entries (
    user_id CHAR(36) NOT NULL,
    story_id CHAR(36) NOT NULL,
    saved_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id, story_id),
    INDEX ix_story_library_story (story_id, saved_at),
    CONSTRAINT fk_story_library_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_library_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chapter_prices (
    chapter_id CHAR(36) PRIMARY KEY,
    price_xu BIGINT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_chapter_price_non_negative CHECK (price_xu >= 0),
    CONSTRAINT fk_chapter_price_chapter
        FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS story_follows (
    user_id CHAR(36) NOT NULL,
    story_id CHAR(36) NOT NULL,
    followed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id, story_id),
    INDEX ix_story_follows_story (story_id, followed_at),
    CONSTRAINT fk_story_follows_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_follows_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chapter_unlocks (
    user_id CHAR(36) NOT NULL,
    chapter_id CHAR(36) NOT NULL,
    price_xu BIGINT NOT NULL,
    ledger_entry_id CHAR(36) NOT NULL,
    unlocked_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id, chapter_id),
    CONSTRAINT uk_chapter_unlock_ledger UNIQUE (ledger_entry_id),
    CONSTRAINT fk_chapter_unlock_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_chapter_unlock_chapter
        FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE CASCADE,
    CONSTRAINT fk_chapter_unlock_ledger
        FOREIGN KEY (ledger_entry_id) REFERENCES ledger_entries(id)
);
