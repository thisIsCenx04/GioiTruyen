-- Daily quests: a fixed catalogue of quest types the admin switches on and
-- tunes, plus one progress row per user per quest per day.
--
-- Progress is deliberately a single counted column rather than an event log.
-- Reading time arrives as a heartbeat every minute, so an append-only log would
-- grow by 1440 rows per active user per day; incrementing one row keeps the
-- write cheap enough for a single small VPS.

CREATE TABLE IF NOT EXISTS `quest_definitions` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    -- Which built-in rule measures this quest. The admin picks from this list;
    -- the logic for each lives in the backend, so no free-form scripting.
    quest_type ENUM(
        'LOGIN_DAILY',
        'READ_MINUTES',
        'ONLINE_MINUTES',
        'READ_CHAPTERS',
        'SHARE_STORY',
        'COMMENT_STORY'
    ) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    -- Meaning depends on quest_type: minutes for READ/ONLINE_MINUTES, a count
    -- for the others. LOGIN_DAILY is always 1.
    target_value INT NOT NULL DEFAULT 1 CHECK (target_value > 0),
    reward_coin INT NOT NULL DEFAULT 0 CHECK (reward_coin >= 0),
    reward_gem INT NOT NULL DEFAULT 0 CHECK (reward_gem >= 0),
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_quest_definitions_active (is_active, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One row per user per quest per day. quest_date is the Asia/Ho_Chi_Minh day the
-- progress belongs to, so a reset happens at local midnight rather than UTC.
CREATE TABLE IF NOT EXISTS `user_quest_progress` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id VARCHAR(36) NOT NULL,
    quest_id VARCHAR(36) NOT NULL,
    quest_date DATE NOT NULL,
    progress_value INT NOT NULL DEFAULT 0 CHECK (progress_value >= 0),
    completed_at TIMESTAMP(3) NULL,
    claimed_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    -- Makes "today's row for this quest" a single upsert, and stops a double
    -- claim from creating a second row.
    UNIQUE KEY uk_user_quest_day (user_id, quest_id, quest_date),
    KEY idx_user_quest_date (user_id, quest_date),
    CONSTRAINT fk_user_quest_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_quest_definition FOREIGN KEY (quest_id) REFERENCES quest_definitions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Starter catalogue matching the quests the platform advertises. Deterministic
-- ids keep this migration idempotent.
INSERT INTO `quest_definitions`
    (`id`, `quest_type`, `title`, `description`, `target_value`, `reward_coin`, `reward_gem`, `sort_order`, `is_active`)
VALUES
    ('0a000000-0000-0000-0000-000000000001', 'LOGIN_DAILY',
     'Điểm danh hằng ngày', 'Đăng nhập mỗi ngày để nhận thưởng.', 1, 10, 0, 1, 1),
    ('0a000000-0000-0000-0000-000000000002', 'READ_MINUTES',
     'Đọc truyện 5 phút', 'Đọc truyện trong 5 phút.', 5, 10, 0, 2, 1),
    ('0a000000-0000-0000-0000-000000000003', 'READ_MINUTES',
     'Đọc truyện 15 phút', 'Đọc truyện trong 15 phút.', 15, 20, 0, 3, 1),
    ('0a000000-0000-0000-0000-000000000004', 'READ_MINUTES',
     'Đọc truyện 30 phút', 'Đọc truyện trong 30 phút.', 30, 35, 1, 4, 1),
    ('0a000000-0000-0000-0000-000000000005', 'READ_MINUTES',
     'Đọc truyện 60 phút', 'Đọc truyện trong 60 phút.', 60, 60, 2, 5, 1),
    ('0a000000-0000-0000-0000-000000000006', 'ONLINE_MINUTES',
     'Online 30 phút', 'Ở lại trang trong 30 phút.', 30, 20, 0, 6, 1),
    ('0a000000-0000-0000-0000-000000000007', 'SHARE_STORY',
     'Chia sẻ truyện', 'Chia sẻ một truyện lên mạng xã hội.', 1, 15, 0, 7, 1),
    ('0a000000-0000-0000-0000-000000000008', 'READ_CHAPTERS',
     'Đọc 3 chương', 'Mở và đọc 3 chương bất kỳ.', 3, 15, 0, 8, 1)
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`),
    `description` = VALUES(`description`),
    `target_value` = VALUES(`target_value`),
    `updated_at` = CURRENT_TIMESTAMP(3);
