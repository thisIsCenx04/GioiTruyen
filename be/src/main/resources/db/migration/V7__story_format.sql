-- Distinguishes a serialised novel from a Zhihu-style one-page short story.
-- Both formats remain stories with chapters; the explicit flag avoids inferring
-- intent from chapter count while a serialised story is still new.

ALTER TABLE `stories`
    ADD COLUMN `story_format` ENUM('SERIAL', 'ONESHOT') NOT NULL DEFAULT 'SERIAL'
    AFTER `content_type`;

CREATE INDEX `idx_stories_format_status`
    ON `stories` (`story_format`, `status`);
