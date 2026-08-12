-- Two things this adds:
--
-- 1. Momo and ZaloPay settle over Napas, so giving them a bank id lets the same
--    VietQR generator build a code carrying the amount and transfer note. They
--    previously showed no QR at all.
-- 2. An admin note on a payment, so a rejected transfer can say why - and the
--    reader is told rather than left guessing.

UPDATE `payment_methods`
SET `config` = JSON_SET(`config`, '$.bankBin', '970454', '$.bank', 'Momo'),
    `instructions` = 'Quét mã hoặc chuyển tới số 0898662206 (NGUYEN THI THU TRANG), giữ nguyên nội dung chuyển khoản.',
    `updated_at` = NOW()
WHERE `id` = '22000000-0000-0000-0000-000000000011';

UPDATE `payment_methods`
SET `config` = JSON_SET(`config`, '$.bankBin', '970454', '$.bank', 'ZaloPay'),
    `instructions` = 'Quét mã hoặc chuyển tới số 0898662206 (NGUYEN THI THU TRANG), giữ nguyên nội dung chuyển khoản.',
    `updated_at` = NOW()
WHERE `id` = '22000000-0000-0000-0000-000000000012';

ALTER TABLE `payments`
    ADD COLUMN `admin_note` VARCHAR(500) NULL AFTER `status`,
    ADD COLUMN `reviewed_by` VARCHAR(36) NULL AFTER `admin_note`,
    ADD COLUMN `reviewed_at` TIMESTAMP NULL AFTER `reviewed_by`;

-- The admin queue is read by status, newest first.
CREATE INDEX `idx_payments_status_created` ON `payments` (`status`, `created_at`);
