-- Where each reader left off, one row per reader and story.
--
-- The chapter reader has been sending progress every 15 seconds since it was
-- written, but ReadingSessionController had nowhere to put it: the handler
-- answered "SAVED" and dropped the payload, and /me/reading-history answered
-- with an empty list because nothing was ever recorded.
--
-- Keyed on (user_id, story_id) rather than appended to: a reader has one place
-- in a story, and the history screen wants the story once, not once per
-- heartbeat. `story_views` stays what it is - the view log used for counts.
CREATE TABLE IF NOT EXISTS `reading_progress` (
    user_id VARCHAR(36) NOT NULL,
    story_id VARCHAR(36) NOT NULL,
    -- NULL while a reader is on the story page but has opened no chapter yet.
    chapter_id VARCHAR(36) NULL,
    -- How far down the chapter, as the percentage the reader's device reports.
    position DECIMAL(5,2) NOT NULL DEFAULT 0,
    -- The device's own clock at the moment it measured `position`. A phone and
    -- a laptop reading the same story arrive out of order often enough that the
    -- later measurement, not the later request, has to win.
    device_updated_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW() ON UPDATE NOW(),
    PRIMARY KEY (user_id, story_id),
    KEY idx_reading_progress_recent (user_id, updated_at DESC),
    CONSTRAINT fk_reading_progress_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_reading_progress_story
        FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_reading_progress_chapter
        FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
