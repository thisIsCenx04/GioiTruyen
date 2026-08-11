-- Durable integration-event delivery tables. These tables are append-only
-- infrastructure and intentionally have no foreign keys to business records:
-- event metadata must remain available even when an aggregate is removed.

CREATE TABLE IF NOT EXISTS `outbox_messages` (
    `id` VARCHAR(36) NOT NULL,
    `event_type` VARCHAR(160) NOT NULL,
    `event_version` INT NOT NULL,
    `occurred_at` TIMESTAMP(6) NOT NULL,
    `correlation_id` VARCHAR(255),
    `trace_context` JSON NOT NULL,
    `aggregate_type` VARCHAR(160) NOT NULL,
    `aggregate_id` VARCHAR(255) NOT NULL,
    `actor_id` VARCHAR(36),
    `team_id` VARCHAR(36),
    `content_type` VARCHAR(100) NOT NULL,
    `payload` JSON NOT NULL,
    `status` ENUM('PENDING', 'PROCESSING', 'PROCESSED', 'DEAD_LETTER') NOT NULL DEFAULT 'PENDING',
    `attempts` INT NOT NULL DEFAULT 0,
    `next_attempt_at` TIMESTAMP(6) NOT NULL,
    `lease_owner` VARCHAR(255),
    `lease_until` TIMESTAMP(6),
    `last_error` VARCHAR(255),
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `processed_at` TIMESTAMP(6),
    PRIMARY KEY (`id`),
    INDEX `idx_outbox_claim` (`status`, `next_attempt_at`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `inbox_receipts` (
    `id` VARCHAR(320) NOT NULL,
    `consumer` VARCHAR(64) NOT NULL,
    `event_id` VARCHAR(36) NOT NULL,
    `event_type` VARCHAR(160) NOT NULL,
    `event_version` INT NOT NULL,
    `processed_at` TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_inbox_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
