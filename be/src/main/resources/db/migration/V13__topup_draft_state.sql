-- Separates "the reader looked at a QR code" from "the reader says they paid".
--
-- Opening the payment screen used to insert a PENDING row, so every reload of
-- the wallet page put another request in front of the admin. A top-up now starts
-- as DRAFT, which nobody reviews, and only becomes PENDING when the reader
-- presses the button confirming they made the transfer.

ALTER TABLE `payments`
    MODIFY COLUMN `status` ENUM('DRAFT',
                                'PENDING',
                                'PAID',
                                'FAILED',
                                'CANCELLED',
                                'REFUNDED') NOT NULL DEFAULT 'DRAFT';

-- When the reader pressed "I have transferred". Null while still a draft.
ALTER TABLE `payments`
    ADD COLUMN `submitted_at` TIMESTAMP NULL AFTER `created_at`;

-- Rows created before this migration were all reader-confirmed by definition:
-- the old flow had no other state, so they keep their place in the queue.
UPDATE `payments` SET `submitted_at` = `created_at` WHERE `status` <> 'DRAFT';

-- Rate limiting counts a user's recent submissions, and the admin queue reads
-- by status and submission time.
CREATE INDEX `idx_payments_user_submitted` ON `payments` (`user_id`, `submitted_at`);
