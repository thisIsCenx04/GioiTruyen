-- ============================================================
-- Web Truyen - Initial Seed Data (V2)
-- ============================================================

-- Seed Default Admin User (Password: Admin@123)
INSERT INTO `users` (`id`, `email`, `username`, `password_hash`, `role`, `status`, `created_at`, `updated_at`)
VALUES ('00000000-0000-0000-0000-000000000001', 'admin@gioitruyen.com', 'admin', '$2a$10$76/nK8p3eBwW8mH.zS2W/.x4/tK9r7X1h5gK4wW.J5xH9X.J5xH9X', 'ADMIN', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE `updated_at` = NOW();

-- Seed Default Genres
INSERT INTO `genres` (`id`, `name`, `slug`, `description`)
VALUES 
('10000000-0000-0000-0000-000000000001', 'Tên Tiên Hạp', 'tien-hiep', 'Truyện tiên hiệp tu chân'),
('10000000-0000-0000-0000-000000000002', 'Huyền Huyễn', 'huyen-huyen', 'Truyện huyền huyễn thế giới mới'),
('10000000-0000-0000-0000-000000000003', 'Đô Thị', 'do-thi', 'Truyện đô thị hiện đại'),
('10000000-0000-0000-0000-000000000004', 'Võ Học', 'vo-hac', 'Truyện kiếm hiệp võ lâm')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- Seed Default Currencies
INSERT INTO `currencies` (`id`, `code`, `name`, `symbol`, `is_active`, `created_at`)
VALUES 
('20000000-0000-0000-0000-000000000001', 'COIN', 'Xu Truyện', 'Xu', TRUE, NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- Seed Default Deposit Packages
INSERT INTO `deposit_packages` (`id`, `name`, `price_vnd`, `coin_amount`, `bonus_coin`, `is_active`)
VALUES 
('30000000-0000-0000-0000-000000000001', 'Gói Nạp Khởi Đầu', 10000, 100, 10, TRUE),
('30000000-0000-0000-0000-000000000002', 'Gói Nạp Phổ Thông', 50000, 500, 70, TRUE),
('30000000-0000-0000-0000-000000000003', 'Gói Nạp Cao Cấp', 100000, 1000, 200, TRUE)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);
