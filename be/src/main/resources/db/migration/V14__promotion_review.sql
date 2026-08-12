-- Bố cáo now goes through an admin before it appears on the home page.
--
-- Paying for a slot used to publish it immediately, so the only way to stop an
-- unwanted feature was to cancel it after readers had already seen it. A booking
-- now starts as PENDING and waits for a decision; a rejection refunds the coins
-- and tells the buyer why.

ALTER TABLE `story_promotions`
    MODIFY COLUMN `status` ENUM('PENDING',
                                'ACTIVE',
                                'REJECTED',
                                'EXPIRED',
                                'CANCELLED') NOT NULL DEFAULT 'PENDING';

ALTER TABLE `story_promotions`
    ADD COLUMN `review_note` VARCHAR(500) NULL AFTER `status`,
    ADD COLUMN `reviewed_by` VARCHAR(36) NULL AFTER `review_note`,
    ADD COLUMN `reviewed_at` TIMESTAMP(3) NULL AFTER `reviewed_by`;

-- Bookings that predate the review step were live already; leaving them PENDING
-- would pull them off the home page for work nobody asked for.
UPDATE `story_promotions`
SET `reviewed_at` = `created_at`
WHERE `status` = 'ACTIVE';

-- The review queue reads by status, oldest first.
CREATE INDEX `idx_story_promotions_review` ON `story_promotions` (`status`, `created_at`);
