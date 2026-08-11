-- Replaces the demo payment rows with the platform's real accounts and puts the
-- deposit packages on the agreed rule: a top-up grants the same number of coins
-- and gems.
--
-- The bank/PayPal details live in a row rather than in application code so they
-- can be corrected from the admin screen without a redeploy.

-- Every package grants gems equal to its coins.
UPDATE `deposit_packages`
SET `gem_amount` = `coin_amount`,
    `bonus_gem` = `bonus_coin`,
    `updated_at` = NOW();

-- The demo rows named themselves "demo" and pointed at a fake account.
DELETE FROM `payment_methods`
WHERE `id` IN (
    '22000000-0000-0000-0000-000000000001',
    '22000000-0000-0000-0000-000000000002'
) AND `id` NOT IN (SELECT DISTINCT `payment_method_id` FROM `payments`);

-- Anything demo that a payment already references is hidden instead of removed,
-- so the payment keeps pointing at the method that took it.
UPDATE `payment_methods` SET `is_active` = FALSE
WHERE `name` LIKE '%demo%';

INSERT INTO `payment_methods`
    (`id`, `name`, `type`, `config`, `instructions`, `is_active`, `sort_order`, `created_by`)
VALUES
    ('22000000-0000-0000-0000-000000000010', 'Ngân hàng MB (VietQR)', 'BANK_TRANSFER',
     JSON_OBJECT(
        'bank', 'MB',
        'bankBin', '970422',
        'accountNumber', '0898662206',
        'accountName', 'NGUYEN THI THU TRANG',
        'autoQr', 'true'),
     'Quét mã VietQR hoặc chuyển khoản đúng số tiền và nội dung hiển thị. Xu vào ví sau khi admin xác nhận.',
     1, 1, '00000000-0000-0000-0000-000000000001'),

    ('22000000-0000-0000-0000-000000000011', 'Momo', 'QR',
     JSON_OBJECT(
        'accountNumber', '0898662206',
        'accountName', 'NGUYEN THI THU TRANG',
        'provider', 'Momo'),
     'Chuyển tới số điện thoại 0898662206 (NGUYEN THI THU TRANG), ghi đúng nội dung chuyển khoản.',
     1, 2, '00000000-0000-0000-0000-000000000001'),

    ('22000000-0000-0000-0000-000000000012', 'ZaloPay', 'QR',
     JSON_OBJECT(
        'accountNumber', '0898662206',
        'accountName', 'NGUYEN THI THU TRANG',
        'provider', 'ZaloPay'),
     'Chuyển tới số điện thoại 0898662206 (NGUYEN THI THU TRANG), ghi đúng nội dung chuyển khoản.',
     1, 3, '00000000-0000-0000-0000-000000000001'),

    ('22000000-0000-0000-0000-000000000013', 'PayPal', 'PAYPAL',
     JSON_OBJECT(
        'paypalEmail', 'dau290819',
        'paypalMeLink', 'https://www.paypal.com/paypalme/dau290819'),
     'Thanh toán qua PayPal.me, ghi mã giao dịch vào phần ghi chú.',
     1, 4, '00000000-0000-0000-0000-000000000001')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `type` = VALUES(`type`),
    `config` = VALUES(`config`),
    `instructions` = VALUES(`instructions`),
    `is_active` = VALUES(`is_active`),
    `sort_order` = VALUES(`sort_order`),
    `updated_at` = NOW();
