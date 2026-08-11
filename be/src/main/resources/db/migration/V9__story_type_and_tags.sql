-- Two gaps this closes:
--
-- 1. Stories had no editorial classification, so the "độc quyền" / "sáng tác"
--    shelves on the catalog had nothing real to filter on.
-- 2. The admin form collected tags but there was nowhere to store them, so every
--    tag typed in was silently discarded and tag links matched nothing.

ALTER TABLE `stories`
    ADD COLUMN `story_type` ENUM('TEXT', 'AUDIO', 'EXCLUSIVE', 'ORIGINAL') NOT NULL DEFAULT 'TEXT'
    AFTER `story_format`;

-- Shelves filter on type + status together.
CREATE INDEX `idx_stories_type_status` ON `stories` (`story_type`, `status`);

-- Tags are free-form editorial labels, distinct from `genres` (a fixed taxonomy).
-- The slug is what a URL carries; the label keeps the admin's original casing
-- and diacritics for display.
CREATE TABLE IF NOT EXISTS `story_tags` (
    story_id VARCHAR(36) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    label VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (story_id, slug),
    KEY idx_story_tags_slug (slug),
    CONSTRAINT fk_story_tags_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
