ALTER TABLE `advertisements`
    MODIFY COLUMN `placement` ENUM('GLOBAL_CLICK', 'STORY_OPEN', 'STORY_DETAIL', 'READER', 'HOME', 'SIDEBAR') NOT NULL;

UPDATE `advertisements`
SET
    `cooldown_seconds` = COALESCE(`cooldown_seconds`, 600),
    `max_clicks_per_day` = COALESCE(`max_clicks_per_day`, 5),
    `priority` = COALESCE(`priority`, 0);

INSERT INTO `advertisements` (
    `id`, `name`, `type`, `image_url`, `target_url`, `placement`, `cooldown_seconds`,
    `max_clicks_per_day`, `priority`, `trigger_every_n_views`, `start_at`, `end_at`,
    `is_active`, `created_at`, `updated_at`
) VALUES (
    '42000000-0000-0000-0000-000000000003',
    'Global click affiliate demo',
    'AFFILIATE_REDIRECT',
    NULL,
    'https://gioitruyen.local/affiliate-demo',
    'GLOBAL_CLICK',
    600,
    5,
    10,
    NULL,
    NULL,
    NULL,
    TRUE,
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE
    `target_url` = VALUES(`target_url`),
    `placement` = VALUES(`placement`),
    `cooldown_seconds` = VALUES(`cooldown_seconds`),
    `max_clicks_per_day` = VALUES(`max_clicks_per_day`),
    `priority` = VALUES(`priority`),
    `is_active` = VALUES(`is_active`),
    `updated_at` = NOW();
