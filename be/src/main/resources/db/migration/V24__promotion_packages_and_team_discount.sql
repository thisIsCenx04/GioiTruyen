-- Bố cáo: the package line-up teams actually buy, and a per-team discount.
--
-- 1. Packages
--    The seeded set ran 1/3/7/14/30 days. Only three lengths are offered now,
--    at the prices the operator set: 3, 7 and 10 days. The old rows are
--    deactivated rather than deleted - story_promotions references package_id,
--    and a past booking should keep pointing at what was actually bought.
UPDATE `promotion_packages`
SET `is_active` = FALSE
WHERE `code` IN ('PROMO_1D', 'PROMO_3D', 'PROMO_7D', 'PROMO_14D', 'PROMO_30D');

INSERT INTO `promotion_packages`
    (`id`, `code`, `name`, `duration_days`, `price_coin`, `description`, `sort_order`, `is_active`)
VALUES
    ('50000000-0000-0000-0000-000000000011', 'BOCAO_3D', 'Bố cáo 3 ngày', 3, 15000,
     'Đưa truyện lên khu Bố cáo trang chủ trong 3 ngày.', 1, TRUE),
    ('50000000-0000-0000-0000-000000000012', 'BOCAO_7D', 'Bố cáo 7 ngày', 7, 32000,
     'Bảy ngày trên trang chủ, tiết kiệm hơn so với gói 3 ngày.', 2, TRUE),
    ('50000000-0000-0000-0000-000000000013', 'BOCAO_10D', 'Bố cáo 10 ngày', 10, 42000,
     'Gói dài nhất, giá mỗi ngày tốt nhất.', 3, TRUE)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `duration_days` = VALUES(`duration_days`),
    `price_coin` = VALUES(`price_coin`),
    `description` = VALUES(`description`),
    `sort_order` = VALUES(`sort_order`),
    `is_active` = TRUE;

-- 2. Per-team discount
--    A flat number of xu taken off every bố cáo this team books, set by an
--    admin and never sent to the team: it is a commercial arrangement between
--    the operator and one partner, and publishing it would invite every other
--    team to ask why their number is different. The team is shown, and charged,
--    only the final figure.
--
--    Flat xu rather than a percentage because that is how the arrangement is
--    negotiated - "giảm thẳng 5000 xu" - and a percentage would need rounding
--    rules nobody agreed on.
ALTER TABLE `teams`
    ADD COLUMN `promotion_discount_coin` BIGINT NOT NULL DEFAULT 0;

ALTER TABLE `teams`
    ADD CONSTRAINT `chk_teams_promotion_discount_non_negative`
    CHECK (`promotion_discount_coin` >= 0);

-- What the booking actually cost after the discount is kept on the row, so the
-- ledger and any refund work from the figure that was really charged rather
-- than re-deriving it from a discount that may have changed since.
ALTER TABLE `story_promotions`
    ADD COLUMN `discount_coin` BIGINT NOT NULL DEFAULT 0 AFTER `coin_paid`;
