-- Puts every top-up package on the agreed exchange rate: 100.000 VND buys
-- 90.000 xu and 90.000 ngọc, so 1 VND is 0,9 xu and 0,9 ngọc at every tier.
--
-- The bonus columns are cleared rather than kept: the wallet credits
-- coin_amount + bonus_coin, so leaving a bonus in place would hand out more
-- than the rate promises and make the number on the package card wrong.
UPDATE `deposit_packages`
SET `coin_amount` = FLOOR(`price_vnd` * 9 / 10),
    `gem_amount` = FLOOR(`price_vnd` * 9 / 10),
    `bonus_coin` = 0,
    `bonus_gem` = 0,
    `updated_at` = NOW();

-- A production database is never seeded, so the 100.000đ tier the rate is quoted
-- against is created here if it is missing. The fixed id means a redeploy
-- corrects that row instead of adding a second copy of the same package.
INSERT INTO `deposit_packages`
    (`id`, `name`, `price_vnd`, `coin_amount`, `gem_amount`, `bonus_coin`, `bonus_gem`, `is_active`)
VALUES
    ('30000000-0000-0000-0000-000000000003', 'Gói 100.000đ', 100000, 90000, 90000, 0, 0, TRUE)
ON DUPLICATE KEY UPDATE
    `price_vnd` = VALUES(`price_vnd`),
    `coin_amount` = VALUES(`coin_amount`),
    `gem_amount` = VALUES(`gem_amount`),
    `bonus_coin` = VALUES(`bonus_coin`),
    `bonus_gem` = VALUES(`bonus_gem`),
    `is_active` = VALUES(`is_active`),
    `updated_at` = NOW();
