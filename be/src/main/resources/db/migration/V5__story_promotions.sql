-- ============================================================
-- Bố cáo (story promotion): teams pay coins to feature a story
-- on the home page for a fixed number of days.
-- ============================================================

CREATE TABLE IF NOT EXISTS `promotion_packages` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    duration_days INT UNSIGNED NOT NULL,
    price_coin BIGINT UNSIGNED NOT NULL,
    description VARCHAR(500) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_promotion_packages_code (code),
    KEY idx_promotion_packages_active (is_active, sort_order),
    CONSTRAINT chk_promotion_packages_duration CHECK (duration_days BETWEEN 1 AND 30),
    CONSTRAINT chk_promotion_packages_price CHECK (price_coin > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `story_promotions` (
    id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    story_id VARCHAR(36) NOT NULL,
    team_id VARCHAR(36) NOT NULL,
    purchased_by VARCHAR(36) NOT NULL,
    package_id VARCHAR(36) NULL,
    duration_days INT UNSIGNED NOT NULL,
    coin_paid BIGINT UNSIGNED NOT NULL,
    starts_at TIMESTAMP(3) NOT NULL,
    ends_at TIMESTAMP(3) NOT NULL,
    status ENUM('ACTIVE', 'EXPIRED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    slot_position INT UNSIGNED NULL,
    tag_label VARCHAR(60) NOT NULL DEFAULT 'Bố cáo',
    cancelled_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_story_promotions_active (status, ends_at),
    KEY idx_story_promotions_story (story_id, status),
    KEY idx_story_promotions_team (team_id, created_at),
    CONSTRAINT chk_story_promotions_window CHECK (ends_at > starts_at),
    CONSTRAINT fk_story_promotions_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_promotions_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_promotions_user FOREIGN KEY (purchased_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_story_promotions_package FOREIGN KEY (package_id) REFERENCES promotion_packages(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Longer bookings carry a lower per-day rate.
INSERT INTO `promotion_packages`
    (`id`, `code`, `name`, `duration_days`, `price_coin`, `description`, `sort_order`, `is_active`)
VALUES
    ('50000000-0000-0000-0000-000000000001', 'PROMO_1D', 'Bố cáo 1 ngày', 1, 13000,
     'Hiển thị truyện tại khu Bố cáo trang chủ trong 1 ngày.', 1, TRUE),
    ('50000000-0000-0000-0000-000000000002', 'PROMO_3D', 'Bố cáo 3 ngày', 3, 36000,
     'Tiết kiệm 1.000 xu mỗi ngày so với gói 1 ngày.', 2, TRUE),
    ('50000000-0000-0000-0000-000000000003', 'PROMO_7D', 'Bố cáo 7 ngày', 7, 70000,
     'Gói phổ biến nhất, chỉ 10.000 xu mỗi ngày.', 3, TRUE),
    ('50000000-0000-0000-0000-000000000004', 'PROMO_14D', 'Bố cáo 14 ngày', 14, 133000,
     'Duy trì hiển thị nửa tháng với 9.500 xu mỗi ngày.', 4, TRUE),
    ('50000000-0000-0000-0000-000000000005', 'PROMO_30D', 'Bố cáo 30 ngày', 30, 270000,
     'Gói dài nhất, giá tốt nhất: 9.000 xu mỗi ngày.', 5, TRUE)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `duration_days` = VALUES(`duration_days`),
    `price_coin` = VALUES(`price_coin`),
    `description` = VALUES(`description`),
    `sort_order` = VALUES(`sort_order`),
    `is_active` = VALUES(`is_active`);
