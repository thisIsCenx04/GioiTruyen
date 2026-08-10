-- ============================================================
-- Replace the placeholder affiliate redirect with the real Shopee
-- links. These are served through the GLOBAL_CLICK placement, which
-- opens one affiliate tab per eligible click and then cools down.
-- ============================================================

-- The demo row pointed at a non-existent host and would 404 on click.
UPDATE `advertisements`
SET `is_active` = FALSE, `updated_at` = NOW(3)
WHERE `id` = '42000000-0000-0000-0000-000000000003'
  AND `target_url` = 'https://gioitruyen.local/affiliate-demo';

INSERT INTO `advertisements` (
    `id`, `name`, `type`, `image_url`, `target_url`, `placement`,
    `cooldown_seconds`, `max_clicks_per_day`, `priority`, `trigger_every_n_views`,
    `start_at`, `end_at`, `is_active`, `created_at`, `updated_at`
) VALUES
    ('44000000-0000-0000-0000-000000000001', 'Shopee Affiliate 1', 'AFFILIATE_REDIRECT', NULL,
     'https://s.shopee.vn/80BjRF8KJA', 'GLOBAL_CLICK', 600, 5, 10, NULL, NULL, NULL, TRUE, NOW(3), NOW(3)),
    ('44000000-0000-0000-0000-000000000002', 'Shopee Affiliate 2', 'AFFILIATE_REDIRECT', NULL,
     'https://s.shopee.vn/4fvHT5LxgY', 'GLOBAL_CLICK', 600, 5, 9, NULL, NULL, NULL, TRUE, NOW(3), NOW(3)),
    ('44000000-0000-0000-0000-000000000003', 'Shopee Affiliate 3', 'AFFILIATE_REDIRECT', NULL,
     'https://s.shopee.vn/9fJxQFVImW', 'GLOBAL_CLICK', 600, 5, 8, NULL, NULL, NULL, TRUE, NOW(3), NOW(3)),
    ('44000000-0000-0000-0000-000000000004', 'Shopee Affiliate 4', 'AFFILIATE_REDIRECT', NULL,
     'https://s.shopee.vn/3LPtsa6O8X', 'GLOBAL_CLICK', 600, 5, 7, NULL, NULL, NULL, TRUE, NOW(3), NOW(3)),
    ('44000000-0000-0000-0000-000000000005', 'Shopee Affiliate 5', 'AFFILIATE_REDIRECT', NULL,
     'https://s.shopee.vn/904Gcyxasu', 'GLOBAL_CLICK', 600, 5, 6, NULL, NULL, NULL, TRUE, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `target_url` = VALUES(`target_url`),
    `placement` = VALUES(`placement`),
    `cooldown_seconds` = VALUES(`cooldown_seconds`),
    `max_clicks_per_day` = VALUES(`max_clicks_per_day`),
    `priority` = VALUES(`priority`),
    `is_active` = VALUES(`is_active`),
    `updated_at` = VALUES(`updated_at`);
