-- Indexes backing the revenue ranking board ("Thánh bảng").
--
-- The board sums, per story, the coins from paid chapter unlocks, whole-story
-- combos and donations, optionally within a time window. None of those lookups
-- had an index to stand on:
--   * chapter_unlocks carried only UNIQUE(user_id, chapter_id), so a lookup by
--     chapter_id could not use it - chapter_id is the second column.
--   * donations and story_combo_purchases had nothing but their primary key.
-- Each index leads with the column joined on and carries created_at so the
-- weekly and monthly windows are served by the same index.

CREATE INDEX idx_chapter_unlocks_chapter_created
    ON chapter_unlocks (chapter_id, created_at);

CREATE INDEX idx_donations_story_created
    ON donations (story_id, created_at);

CREATE INDEX idx_story_combo_purchases_story_created
    ON story_combo_purchases (story_id, created_at);
