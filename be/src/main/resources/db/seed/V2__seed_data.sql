-- ============================================================
-- Gioitruyen - Complete Demo Seed Data (V2)
-- Seed content follows the Vietnamese short story / Zhihu catalog style.
-- Demo password for seeded local accounts: Admin@123
-- ============================================================

SET @now = TIMESTAMP('2026-08-09 09:00:00');
SET @yesterday = DATE('2026-08-08');
SET @today = DATE('2026-08-09');

INSERT INTO `users` (
    `id`, `email`, `username`, `password_hash`, `display_name`, `avatar_url`, `role`, `status`,
    `email_verified_at`, `last_login_at`, `created_at`, `updated_at`
) VALUES
('00000000-0000-0000-0000-000000000001', 'admin@gioitruyen.com', 'admin', '$2a$10$DJ7.CxIRm.97hv9g1JnLi.pV7pvYJEOMNt4hA7n.OW74suEpuJ7Im', 'Quản trị Giới Truyện', '/assets/avatars/admin.png', 'ADMIN', 'ACTIVE', @now - INTERVAL 60 DAY, @now - INTERVAL 1 HOUR, @now - INTERVAL 60 DAY, @now),
('00000000-0000-0000-0000-000000000002', 'maianh@gioitruyen.com', 'maianh', '$2a$10$DJ7.CxIRm.97hv9g1JnLi.pV7pvYJEOMNt4hA7n.OW74suEpuJ7Im', 'Mai Anh Dịch Truyện', '/assets/avatars/mai-anh.png', 'READER', 'ACTIVE', @now - INTERVAL 45 DAY, @now - INTERVAL 2 HOUR, @now - INTERVAL 45 DAY, @now),
('00000000-0000-0000-0000-000000000003', 'linhchi@gioitruyen.com', 'linhchi', '$2a$10$DJ7.CxIRm.97hv9g1JnLi.pV7pvYJEOMNt4hA7n.OW74suEpuJ7Im', 'Linh Chi Biên Tập', '/assets/avatars/linh-chi.png', 'READER', 'ACTIVE', @now - INTERVAL 30 DAY, @now - INTERVAL 20 MINUTE, @now - INTERVAL 30 DAY, @now)
ON DUPLICATE KEY UPDATE
    `display_name` = VALUES(`display_name`),
    `avatar_url` = VALUES(`avatar_url`),
    `role` = VALUES(`role`),
    `status` = VALUES(`status`),
    `email_verified_at` = VALUES(`email_verified_at`),
    `last_login_at` = VALUES(`last_login_at`),
    `updated_at` = VALUES(`updated_at`);

INSERT INTO `auth_accounts` (`id`, `user_id`, `provider`, `provider_account_id`, `created_at`) VALUES
('01000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'LOCAL', 'admin@gioitruyen.com', @now - INTERVAL 60 DAY),
('01000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'LOCAL', 'maianh@gioitruyen.com', @now - INTERVAL 45 DAY),
('01000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003', 'LOCAL', 'linhchi@gioitruyen.com', @now - INTERVAL 30 DAY)
ON DUPLICATE KEY UPDATE `user_id` = VALUES(`user_id`);

INSERT INTO `password_reset_tokens` (`id`, `user_id`, `token_hash`, `expires_at`, `used_at`, `created_at`) VALUES
('02000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'demo-reset-token-hash-linhchi', @now + INTERVAL 1 DAY, NULL, @now - INTERVAL 1 HOUR)
ON DUPLICATE KEY UPDATE `expires_at` = VALUES(`expires_at`), `used_at` = VALUES(`used_at`);

INSERT INTO `user_profiles` (`user_id`, `bio`, `cover_url`, `gender`, `birthday`, `website_url`, `updated_at`) VALUES
('00000000-0000-0000-0000-000000000001', 'Quản trị nội dung, duyệt truyện và vận hành thư viện demo.', '/assets/covers/admin-cover.png', 'OTHER', '1993-03-12', 'https://gioitruyen.local/', @now),
('00000000-0000-0000-0000-000000000002', 'Dịch giả thích truyện ngắn, cổ đại, chữa lành và những cú bẻ lái kiểu Zhihu.', '/assets/covers/mai-anh-cover.png', 'FEMALE', '1998-08-21', 'https://gioitruyen.local/', @now),
('00000000-0000-0000-0000-000000000003', 'Biên tập viên phụ trách lịch đăng, tag và chất lượng chương.', '/assets/covers/linh-chi-cover.png', 'FEMALE', '2000-11-05', 'https://gioitruyen.local/', @now)
ON DUPLICATE KEY UPDATE `bio` = VALUES(`bio`), `cover_url` = VALUES(`cover_url`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `user_settings` (`user_id`, `theme`, `reader_font_size`, `reader_font_family`, `reader_line_height`, `updated_at`) VALUES
('00000000-0000-0000-0000-000000000001', 'SYSTEM', 18, 'Inter', 1.70, @now),
('00000000-0000-0000-0000-000000000002', 'LIGHT', 19, 'Merriweather', 1.80, @now),
('00000000-0000-0000-0000-000000000003', 'DARK', 18, 'Nunito', 1.75, @now)
ON DUPLICATE KEY UPDATE `theme` = VALUES(`theme`), `reader_font_size` = VALUES(`reader_font_size`), `reader_font_family` = VALUES(`reader_font_family`), `reader_line_height` = VALUES(`reader_line_height`);

INSERT INTO `teams` (
    `id`, `name`, `slug`, `avatar_url`, `cover_url`, `description`, `status`, `created_by`, `created_at`, `updated_at`
) VALUES
('03000000-0000-0000-0000-000000000001', 'Nhà Dịch Ánh Trăng', 'nha-dich-anh-trang', '/assets/teams/anh-trang.png', '/assets/teams/anh-trang-cover.png', 'Nhóm dịch truyện ngắn, ngôn tình, cổ đại và chữa lành theo nhịp đăng đều hằng ngày.', 'ACTIVE', '00000000-0000-0000-0000-000000000001', @now - INTERVAL 40 DAY, @now),
('03000000-0000-0000-0000-000000000002', 'Zhihu Góc Nhỏ', 'zhihu-goc-nho', '/assets/teams/zhihu-goc-nho.png', '/assets/teams/zhihu-goc-nho-cover.png', 'Team chuyên truyện Zhihu, đoản văn đô thị, trả thù, vả mặt và kết thúc gọn.', 'ACTIVE', '00000000-0000-0000-0000-000000000002', @now - INTERVAL 28 DAY, @now)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `description` = VALUES(`description`), `status` = VALUES(`status`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `team_members` (`id`, `team_id`, `user_id`, `member_role`, `status`, `added_by`, `joined_at`, `removed_at`) VALUES
('04000000-0000-0000-0000-000000000001', '03000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'OWNER', 'ACTIVE', '00000000-0000-0000-0000-000000000001', @now - INTERVAL 40 DAY, NULL),
('04000000-0000-0000-0000-000000000002', '03000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'MANAGER', 'ACTIVE', '00000000-0000-0000-0000-000000000001', @now - INTERVAL 39 DAY, NULL),
('04000000-0000-0000-0000-000000000003', '03000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'EDITOR', 'ACTIVE', '00000000-0000-0000-0000-000000000001', @now - INTERVAL 38 DAY, NULL),
('04000000-0000-0000-0000-000000000004', '03000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'OWNER', 'ACTIVE', '00000000-0000-0000-0000-000000000002', @now - INTERVAL 28 DAY, NULL),
('04000000-0000-0000-0000-000000000005', '03000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', 'EDITOR', 'ACTIVE', '00000000-0000-0000-0000-000000000002', @now - INTERVAL 27 DAY, NULL),
('04000000-0000-0000-0000-000000000006', '03000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'MANAGER', 'ACTIVE', '00000000-0000-0000-0000-000000000002', @now - INTERVAL 26 DAY, NULL)
ON DUPLICATE KEY UPDATE `member_role` = VALUES(`member_role`), `status` = VALUES(`status`), `removed_at` = VALUES(`removed_at`);

INSERT INTO `team_follows` (`user_id`, `team_id`, `created_at`) VALUES
('00000000-0000-0000-0000-000000000002', '03000000-0000-0000-0000-000000000001', @now - INTERVAL 20 DAY),
('00000000-0000-0000-0000-000000000003', '03000000-0000-0000-0000-000000000001', @now - INTERVAL 18 DAY),
('00000000-0000-0000-0000-000000000003', '03000000-0000-0000-0000-000000000002', @now - INTERVAL 10 DAY)
ON DUPLICATE KEY UPDATE `created_at` = VALUES(`created_at`);

INSERT INTO `genres` (`id`, `name`, `slug`, `description`, `icon_url`, `is_active`, `created_at`, `updated_at`) VALUES
('10000000-0000-0000-0000-000000000001', 'Ngôn Tình', 'ngon-tinh', 'Tình cảm hiện đại và cổ đại, nhịp đọc nhẹ, nhiều cảm xúc.', '/assets/genres/ngon-tinh.svg', TRUE, @now - INTERVAL 40 DAY, @now),
('10000000-0000-0000-0000-000000000002', 'Zhihu', 'zhihu', 'Truyện ngắn kiểu hỏi đáp, twist nhanh, kết luận gọn.', '/assets/genres/zhihu.svg', TRUE, @now - INTERVAL 40 DAY, @now),
('10000000-0000-0000-0000-000000000003', 'Cổ Đại', 'co-dai', 'Bối cảnh cung đình, gia đấu, hôn ước và danh môn.', '/assets/genres/co-dai.svg', TRUE, @now - INTERVAL 40 DAY, @now),
('10000000-0000-0000-0000-000000000004', 'Đô Thị', 'do-thi', 'Câu chuyện đời thường, công sở, gia đình và lựa chọn trưởng thành.', '/assets/genres/do-thi.svg', TRUE, @now - INTERVAL 40 DAY, @now),
('10000000-0000-0000-0000-000000000005', 'Trọng Sinh', 'trong-sinh', 'Nhân vật trở lại quá khứ để sửa sai hoặc tự cứu mình.', '/assets/genres/trong-sinh.svg', TRUE, @now - INTERVAL 40 DAY, @now),
('10000000-0000-0000-0000-000000000006', 'Chữa Lành', 'chua-lanh', 'Tông dịu, tập trung chữa lành tổn thương và hy vọng.', '/assets/genres/chua-lanh.svg', TRUE, @now - INTERVAL 40 DAY, @now),
('10000000-0000-0000-0000-000000000007', 'Vả Mặt', 'va-mat', 'Cao trào trả đũa, lật ngược tình thế và giải oan.', '/assets/genres/va-mat.svg', TRUE, @now - INTERVAL 40 DAY, @now),
('10000000-0000-0000-0000-000000000008', 'Hài Hước', 'hai-huoc', 'Tình huống vui, thoại nhanh và nhịp đọc thư giãn.', '/assets/genres/hai-huoc.svg', TRUE, @now - INTERVAL 40 DAY, @now)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `description` = VALUES(`description`), `icon_url` = VALUES(`icon_url`), `is_active` = VALUES(`is_active`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `stories` (
    `id`, `team_id`, `created_by`, `title`, `slug`, `original_title`, `original_author`, `short_description`, `description`, `cover_url`, `banner_url`,
    `content_type`, `status`, `progress_status`, `age_rating`, `published_at`, `last_chapter_at`,
    `view_count_cache`, `follow_count_cache`, `favorite_count_cache`, `recommendation_gem_cache`, `created_at`, `updated_at`
) VALUES
('11000000-0000-0000-0000-000000000001', '03000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'Phật Trước Kiều', 'phat-truoc-kieu', '佛前娇', 'Thư Sách', 'Nàng gặp lại cố nhân trước điện Phật, nơi một lời hứa cũ đổi hướng cả đời.', 'Một truyện cổ đại lãng mạn theo phong cách đề cử truyện chữ: nhiều cảm xúc, ít chương, kết thúc trọn vẹn.', '/assets/stories/phat-truoc-kieu.jpg', '/assets/stories/phat-truoc-kieu-banner.jpg', 'TEXT', 'PUBLISHED', 'COMPLETED', '13+', @now - INTERVAL 12 DAY, @now - INTERVAL 4 HOUR, 22, 2, 2, 8, @now - INTERVAL 15 DAY, @now - INTERVAL 4 HOUR),
('11000000-0000-0000-0000-000000000002', '03000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', 'Quy Tắc Bao Lì Xì Đỏ', 'quy-tac-bao-li-xi-do', '红包规则', 'Lạc Giữa Ngân Hà', 'Một nhóm chat phát bao lì xì bỗng xuất hiện luật chơi kỳ lạ.', 'Truyện Zhihu ngắn, nhịp nhanh, có yếu tố quy tắc và cú lật cuối theo gu truyện hot.', '/assets/stories/quy-tac-bao-li-xi-do.jpg', '/assets/stories/quy-tac-bao-li-xi-do-banner.jpg', 'TEXT_AUDIO', 'PUBLISHED', 'COMPLETED', '16+', @now - INTERVAL 8 DAY, @now - INTERVAL 2 HOUR, 15, 1, 1, 4, @now - INTERVAL 10 DAY, @now - INTERVAL 2 HOUR),
('11000000-0000-0000-0000-000000000003', '03000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'Tuyết Tận Kiến Quân Tâm', 'tuyet-tan-kien-quan-tam', '雪尽见君心', 'Tịch Mặc Tĩnh Du', 'Ba năm làm dâu, nàng chọn rời phủ vào ngày tuyết cuối mùa.', 'Cổ đại gia đấu, chậm rãi nhưng có điểm rơi cảm xúc rõ, phù hợp nhóm độc giả thích nữ chính tự cường.', '/assets/stories/tuyet-tan-kien-quan-tam.jpg', '/assets/stories/tuyet-tan-kien-quan-tam-banner.jpg', 'TEXT', 'PUBLISHED', 'ONGOING', '13+', @now - INTERVAL 6 DAY, @now - INTERVAL 1 HOUR, 9, 0, 0, 0, @now - INTERVAL 7 DAY, @now - INTERVAL 1 HOUR),
('11000000-0000-0000-0000-000000000004', '03000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', 'Sau Khi Từ Hôn Với Tam Gia', 'sau-khi-tu-hon-voi-tam-gia', '退婚三爷后', 'Tiểu Yêu Núi Phiêu Phiêu', 'Nữ chính tự rút khỏi hôn ước và khiến cả nhà quyền quý phải nhìn lại.', 'Đô thị hào môn, có trả thù nhẹ, tập trung vào lựa chọn cá nhân và sự trưởng thành.', '/assets/stories/sau-khi-tu-hon-voi-tam-gia.jpg', '/assets/stories/sau-khi-tu-hon-voi-tam-gia-banner.jpg', 'TEXT', 'PENDING_REVIEW', 'ONGOING', '13+', NULL, @now - INTERVAL 30 MINUTE, 6, 0, 0, 0, @now - INTERVAL 4 DAY, @now - INTERVAL 30 MINUTE),
('11000000-0000-0000-0000-000000000005', '03000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'Móc Khóa Cộng Cảm', 'moc-khoa-cong-cam', '共感钥匙扣', 'Cam Mười Tú', 'Một chiếc móc khóa khiến hai người xa lạ nghe thấy cảm xúc của nhau.', 'Đoản văn chữa lành pha hài, nhẹ nhàng, hợp khu vực truyện mới cập nhật.', '/assets/stories/moc-khoa-cong-cam.jpg', '/assets/stories/moc-khoa-cong-cam-banner.jpg', 'AUDIO', 'DRAFT', 'PAUSED', '13+', NULL, NULL, 0, 0, 0, 0, @now - INTERVAL 2 DAY, @now - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE
    `team_id` = VALUES(`team_id`), `created_by` = VALUES(`created_by`), `title` = VALUES(`title`), `original_title` = VALUES(`original_title`),
    `original_author` = VALUES(`original_author`), `short_description` = VALUES(`short_description`), `description` = VALUES(`description`),
    `cover_url` = VALUES(`cover_url`), `banner_url` = VALUES(`banner_url`), `content_type` = VALUES(`content_type`), `status` = VALUES(`status`),
    `progress_status` = VALUES(`progress_status`), `age_rating` = VALUES(`age_rating`), `published_at` = VALUES(`published_at`),
    `last_chapter_at` = VALUES(`last_chapter_at`), `view_count_cache` = VALUES(`view_count_cache`), `follow_count_cache` = VALUES(`follow_count_cache`),
    `favorite_count_cache` = VALUES(`favorite_count_cache`), `recommendation_gem_cache` = VALUES(`recommendation_gem_cache`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `story_genres` (`story_id`, `genre_id`) VALUES
('11000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001'),
('11000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000003'),
('11000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002'),
('11000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000007'),
('11000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003'),
('11000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000006'),
('11000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004'),
('11000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000007'),
('11000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000006'),
('11000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000008')
ON DUPLICATE KEY UPDATE `genre_id` = VALUES(`genre_id`);

INSERT INTO `story_reviews` (`id`, `story_id`, `submitted_by`, `reviewed_by`, `status`, `admin_note`, `submitted_at`, `reviewed_at`) VALUES
('12000000-0000-0000-0000-000000000001', '11000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'APPROVED', 'Đạt chất lượng biên tập, đủ ảnh bìa và mô tả.', @now - INTERVAL 13 DAY, @now - INTERVAL 12 DAY),
('12000000-0000-0000-0000-000000000002', '11000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003', NULL, 'PENDING', 'Chờ kiểm tra chính tả chương 2.', @now - INTERVAL 1 DAY, NULL)
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`), `admin_note` = VALUES(`admin_note`), `reviewed_at` = VALUES(`reviewed_at`);

INSERT INTO `chapters` (
    `id`, `story_id`, `chapter_number`, `title`, `slug`, `content`, `short_description`, `access_type`, `coin_price`, `status`, `published_at`, `created_by`, `created_at`, `updated_at`
) VALUES
('13000000-0000-0000-0000-000000000001', '11000000-0000-0000-0000-000000000001', 1.00, 'Chương 1: Trước điện Phật', 'chuong-1-truoc-dien-phat', 'Nàng đặt nhành mai xuống bậc đá lạnh và nghe tiếng chuông sớm vang qua sân chùa.', 'Cuộc gặp mở đầu tại cổ tự.', 'FREE', 0, 'PUBLISHED', @now - INTERVAL 12 DAY, '00000000-0000-0000-0000-000000000002', @now - INTERVAL 13 DAY, @now - INTERVAL 12 DAY),
('13000000-0000-0000-0000-000000000002', '11000000-0000-0000-0000-000000000001', 2.00, 'Chương 2: Lời hứa dưới tuyết', 'chuong-2-loi-hua-duoi-tuyet', 'Một lời hứa tưởng đã mục nát lại được nhắc bằng giọng rất khẽ.', 'Lời hứa cũ quay lại.', 'FREE', 0, 'PUBLISHED', @now - INTERVAL 10 DAY, '00000000-0000-0000-0000-000000000002', @now - INTERVAL 11 DAY, @now - INTERVAL 10 DAY),
('13000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000001', 3.00, 'Chương 3: Hoa đăng không tắt', 'chuong-3-hoa-dang-khong-tat', 'Đêm hội hoa đăng, nàng cuối cùng hiểu lòng người trước mặt.', 'Kết truyện trọn vẹn.', 'PAID', 20, 'PUBLISHED', @now - INTERVAL 4 HOUR, '00000000-0000-0000-0000-000000000002', @now - INTERVAL 1 DAY, @now - INTERVAL 4 HOUR),
('13000000-0000-0000-0000-000000000004', '11000000-0000-0000-0000-000000000002', 1.00, 'Chương 1: Nhóm vừa thêm một người', 'chuong-1-nhom-vua-them-mot-nguoi', 'Tin nhắn hệ thống xuất hiện đúng lúc đồng hồ chỉ 00:00.', 'Luật đầu tiên được công bố.', 'FREE', 0, 'PUBLISHED', @now - INTERVAL 8 DAY, '00000000-0000-0000-0000-000000000003', @now - INTERVAL 8 DAY, @now - INTERVAL 8 DAY),
('13000000-0000-0000-0000-000000000005', '11000000-0000-0000-0000-000000000002', 2.00, 'Chương 2: Không được nhận lì xì đỏ', 'chuong-2-khong-duoc-nhan-li-xi-do', 'Người đầu tiên phá luật biến mất khỏi danh sách thành viên.', 'Twist chính của câu chuyện.', 'PAID', 20, 'PUBLISHED', @now - INTERVAL 2 HOUR, '00000000-0000-0000-0000-000000000003', @now - INTERVAL 2 DAY, @now - INTERVAL 2 HOUR),
('13000000-0000-0000-0000-000000000006', '11000000-0000-0000-0000-000000000003', 1.00, 'Chương 1: Tuyết cuối mùa', 'chuong-1-tuyet-cuoi-mua', 'Nàng rời phủ khi tuyết phủ trắng mái hiên phía đông.', 'Mở đầu hành trình rời đi.', 'FREE', 0, 'PUBLISHED', @now - INTERVAL 6 DAY, '00000000-0000-0000-0000-000000000002', @now - INTERVAL 6 DAY, @now - INTERVAL 6 DAY),
('13000000-0000-0000-0000-000000000007', '11000000-0000-0000-0000-000000000003', 2.00, 'Chương 2: Một phong hòa ly thư', 'chuong-2-mot-phong-hoa-ly-thu', 'Lá thư được đặt lên bàn, chữ viết ngay ngắn đến mức đau lòng.', 'Biến cố chính.', 'PAID', 20, 'PUBLISHED', @now - INTERVAL 1 HOUR, '00000000-0000-0000-0000-000000000002', @now - INTERVAL 1 DAY, @now - INTERVAL 1 HOUR),
('13000000-0000-0000-0000-000000000008', '11000000-0000-0000-0000-000000000004', 1.00, 'Chương 1: Hôn ước trả lại', 'chuong-1-hon-uoc-tra-lai', 'Cô đặt nhẫn lên bàn họp và mỉm cười rất bình tĩnh.', 'Chương đang chờ duyệt.', 'FREE', 0, 'PENDING_REVIEW', NULL, '00000000-0000-0000-0000-000000000003', @now - INTERVAL 4 DAY, @now - INTERVAL 30 MINUTE),
('13000000-0000-0000-0000-000000000009', '11000000-0000-0000-0000-000000000005', 1.00, 'Tập 1: Tín hiệu đầu tiên', 'tap-1-tin-hieu-dau-tien', 'Bản thảo audio mở đầu bằng tiếng mưa và tiếng chìa khóa chạm cửa.', 'Bản nháp audio.', 'FREE', 0, 'DRAFT', NULL, '00000000-0000-0000-0000-000000000001', @now - INTERVAL 2 DAY, @now - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`), `content` = VALUES(`content`), `short_description` = VALUES(`short_description`), `access_type` = VALUES(`access_type`),
    `coin_price` = VALUES(`coin_price`), `status` = VALUES(`status`), `published_at` = VALUES(`published_at`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `chapter_audios` (`id`, `chapter_id`, `audio_url`, `duration_seconds`, `file_size`, `narrator`, `status`, `created_at`) VALUES
('14000000-0000-0000-0000-000000000001', '13000000-0000-0000-0000-000000000004', '/media/audio/quy-tac-bao-li-xi-do-chuong-1.mp3', 684, 10485760, 'Linh Chi', 'VISIBLE', @now - INTERVAL 7 DAY),
('14000000-0000-0000-0000-000000000002', '13000000-0000-0000-0000-000000000009', '/media/audio/moc-khoa-cong-cam-tap-1-demo.mp3', 522, 8388608, 'Quản trị Giới Truyện', 'VISIBLE', @now - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE `audio_url` = VALUES(`audio_url`), `duration_seconds` = VALUES(`duration_seconds`), `file_size` = VALUES(`file_size`), `narrator` = VALUES(`narrator`);

INSERT INTO `audio_listens` (`id`, `chapter_audio_id`, `user_id`, `session_id`, `listened_seconds`, `created_at`) VALUES
('15000000-0000-0000-0000-000000000001', '14000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'sess-audio-001', 684, @now - INTERVAL 5 DAY),
('15000000-0000-0000-0000-000000000002', '14000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'sess-audio-002', 361, @now - INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE `listened_seconds` = VALUES(`listened_seconds`);

INSERT INTO `story_daily_stats` (`story_id`, `stat_date`, `views`, `unique_views`, `audio_listens`, `favorites`, `follows`, `recommendations`, `coin_revenue`) VALUES
('11000000-0000-0000-0000-000000000001', @yesterday, 14, 10, 0, 1, 1, 1, 0),
('11000000-0000-0000-0000-000000000001', @today, 8, 6, 0, 1, 1, 1, 20),
('11000000-0000-0000-0000-000000000002', @yesterday, 7, 5, 1, 0, 0, 0, 0),
('11000000-0000-0000-0000-000000000002', @today, 8, 5, 1, 1, 1, 1, 20),
('11000000-0000-0000-0000-000000000003', @yesterday, 4, 3, 0, 0, 0, 0, 0),
('11000000-0000-0000-0000-000000000003', @today, 5, 4, 0, 0, 0, 0, 20),
('11000000-0000-0000-0000-000000000004', @today, 6, 4, 0, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE `views` = VALUES(`views`), `unique_views` = VALUES(`unique_views`), `audio_listens` = VALUES(`audio_listens`), `favorites` = VALUES(`favorites`), `follows` = VALUES(`follows`), `recommendations` = VALUES(`recommendations`), `coin_revenue` = VALUES(`coin_revenue`);

INSERT INTO `story_views` (`id`, `story_id`, `chapter_id`, `user_id`, `session_id`, `ip_hash`, `viewed_at`) VALUES
('16000000-0000-0000-0000-000000000001', '11000000-0000-0000-0000-000000000001', '13000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'sess-view-001', 'ip-demo-001', @now - INTERVAL 20 HOUR),
('16000000-0000-0000-0000-000000000002', '11000000-0000-0000-0000-000000000001', '13000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003', 'sess-view-002', 'ip-demo-002', @now - INTERVAL 2 HOUR),
('16000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000002', '13000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000002', 'sess-view-003', 'ip-demo-003', @now - INTERVAL 1 HOUR)
ON DUPLICATE KEY UPDATE `viewed_at` = VALUES(`viewed_at`);

INSERT INTO `library_items` (`user_id`, `story_id`, `created_at`) VALUES
('00000000-0000-0000-0000-000000000002', '11000000-0000-0000-0000-000000000001', @now - INTERVAL 7 DAY),
('00000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000001', @now - INTERVAL 6 DAY),
('00000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000002', @now - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE `created_at` = VALUES(`created_at`);

INSERT INTO `story_follows` (`user_id`, `story_id`, `notify_new_chapter`, `created_at`) VALUES
('00000000-0000-0000-0000-000000000002', '11000000-0000-0000-0000-000000000001', TRUE, @now - INTERVAL 7 DAY),
('00000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000001', TRUE, @now - INTERVAL 6 DAY),
('00000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000002', FALSE, @now - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE `notify_new_chapter` = VALUES(`notify_new_chapter`);

INSERT INTO `comments` (`id`, `user_id`, `story_id`, `chapter_id`, `parent_id`, `content`, `status`, `like_count_cache`, `created_at`, `updated_at`) VALUES
('17000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000001', '13000000-0000-0000-0000-000000000003', NULL, 'Đoạn kết gọn và cảm xúc, đúng kiểu đoản văn mình thích.', 'VISIBLE', 1, @now - INTERVAL 3 HOUR, @now - INTERVAL 3 HOUR),
('17000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', '11000000-0000-0000-0000-000000000001', '13000000-0000-0000-0000-000000000003', '17000000-0000-0000-0000-000000000001', 'Cảm ơn bạn, chương cuối mình đã biên lại nhịp thoại khá kỹ.', 'VISIBLE', 0, @now - INTERVAL 2 HOUR, @now - INTERVAL 2 HOUR),
('17000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', '11000000-0000-0000-0000-000000000004', '13000000-0000-0000-0000-000000000008', NULL, 'Cần rà lại dấu câu trước khi duyệt xuất bản.', 'VISIBLE', 0, @now - INTERVAL 1 HOUR, @now - INTERVAL 1 HOUR)
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`), `like_count_cache` = VALUES(`like_count_cache`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `comment_likes` (`comment_id`, `user_id`, `created_at`) VALUES
('17000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', @now - INTERVAL 2 HOUR)
ON DUPLICATE KEY UPDATE `created_at` = VALUES(`created_at`);

INSERT INTO `reports` (`id`, `reporter_id`, `target_type`, `target_id`, `report_type`, `description`, `status`, `handled_by`, `admin_note`, `created_at`, `resolved_at`) VALUES
('18000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'COMMENT', '17000000-0000-0000-0000-000000000003', 'EDITORIAL_NOTE', 'Ghi chú nội bộ bị hiển thị như bình luận công khai.', 'RESOLVED', '00000000-0000-0000-0000-000000000001', 'Giữ lại làm dữ liệu demo kiểm duyệt.', @now - INTERVAL 45 MINUTE, @now - INTERVAL 30 MINUTE)
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`), `handled_by` = VALUES(`handled_by`), `admin_note` = VALUES(`admin_note`), `resolved_at` = VALUES(`resolved_at`);

INSERT INTO `currencies` (`id`, `code`, `name`) VALUES
('20000000-0000-0000-0000-000000000001', 'COIN', 'Xu'),
('20000000-0000-0000-0000-000000000002', 'GEM', 'Đá đề cử')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `wallets` (`id`, `user_id`, `coin_balance`, `gem_balance`, `updated_at`) VALUES
('21000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 0, 0, @now),
('21000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 1515, 25, @now),
('21000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003', 797, 11, @now)
ON DUPLICATE KEY UPDATE `coin_balance` = VALUES(`coin_balance`), `gem_balance` = VALUES(`gem_balance`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `payment_methods` (`id`, `name`, `type`, `config`, `instructions`, `is_active`, `sort_order`, `created_by`, `created_at`, `updated_at`) VALUES
('22000000-0000-0000-0000-000000000001', 'Chuyển khoản ngân hàng demo', 'BANK_TRANSFER', '{"bank":"DemoBank","accountName":"GIOI TRUYEN DEMO","accountNumber":"000123456789"}', 'Chuyển khoản theo mã giao dịch hiển thị trên màn hình nạp Xu.', TRUE, 1, '00000000-0000-0000-0000-000000000001', @now - INTERVAL 30 DAY, @now),
('22000000-0000-0000-0000-000000000002', 'QR nội bộ demo', 'QR', '{"provider":"internal-demo","currency":"VND"}', 'Quét QR demo trong môi trường local, không dùng cho thanh toán thật.', TRUE, 2, '00000000-0000-0000-0000-000000000001', @now - INTERVAL 30 DAY, @now)
ON DUPLICATE KEY UPDATE `config` = VALUES(`config`), `instructions` = VALUES(`instructions`), `is_active` = VALUES(`is_active`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `deposit_packages` (`id`, `name`, `price_vnd`, `coin_amount`, `gem_amount`, `bonus_coin`, `bonus_gem`, `is_active`, `created_at`, `updated_at`) VALUES
('30000000-0000-0000-0000-000000000001', 'Gói Đọc Thử', 20000, 300, 5, 0, 0, TRUE, @now - INTERVAL 30 DAY, @now),
('30000000-0000-0000-0000-000000000002', 'Gói Theo Dõi Tháng', 50000, 600, 10, 50, 0, TRUE, @now - INTERVAL 30 DAY, @now),
('30000000-0000-0000-0000-000000000003', 'Gói Cày Truyện Full', 100000, 1000, 20, 150, 2, TRUE, @now - INTERVAL 30 DAY, @now)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `price_vnd` = VALUES(`price_vnd`), `coin_amount` = VALUES(`coin_amount`), `gem_amount` = VALUES(`gem_amount`), `bonus_coin` = VALUES(`bonus_coin`), `bonus_gem` = VALUES(`bonus_gem`), `is_active` = VALUES(`is_active`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `payments` (`id`, `user_id`, `payment_method_id`, `deposit_package_id`, `amount_vnd`, `coin_received`, `gem_received`, `transaction_code`, `external_transaction_id`, `status`, `paid_at`, `created_at`) VALUES
('23000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '22000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000003', 100000, 1000, 20, 'GT-DEMO-1001', 'EXT-DEMO-1001', 'PAID', @now - INTERVAL 9 DAY, @now - INTERVAL 9 DAY),
('23000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', '22000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000002', 50000, 600, 10, 'GT-DEMO-1002', 'EXT-DEMO-1002', 'PAID', @now - INTERVAL 5 DAY, @now - INTERVAL 5 DAY)
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`), `paid_at` = VALUES(`paid_at`);

INSERT INTO `wallet_transactions` (`id`, `user_id`, `currency`, `type`, `amount`, `balance_after`, `reference_type`, `reference_id`, `description`, `created_at`) VALUES
('24000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'COIN', 'DEPOSIT', 1000, 1000, 'PAYMENT', '23000000-0000-0000-0000-000000000001', 'Nạp Gói Cày Truyện Full.', @now - INTERVAL 9 DAY),
('24000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'GEM', 'DEPOSIT', 20, 20, 'PAYMENT', '23000000-0000-0000-0000-000000000001', 'Nhận đá đề cử từ gói nạp.', @now - INTERVAL 9 DAY),
('24000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', 'COIN', 'ADMIN_ADJUSTMENT', 100, 1100, 'ADMIN_AUDIT', '99000000-0000-0000-0000-000000000001', 'Thưởng nhập liệu demo.', @now - INTERVAL 8 DAY),
('24000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000002', 'COIN', 'DONATION', -60, 1040, 'DONATION', '27000000-0000-0000-0000-000000000001', 'Ủng hộ Phật Trước Kiều.', @now - INTERVAL 6 DAY),
('24000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002', 'COIN', 'PURCHASE', -20, 1020, 'PURCHASE_ORDER', '25000000-0000-0000-0000-000000000001', 'Mở khóa chương trả phí.', @now - INTERVAL 4 HOUR),
('24000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000002', 'GEM', 'RECOMMENDATION', -5, 15, 'STORY_RECOMMENDATION', '29000000-0000-0000-0000-000000000001', 'Đề cử Phật Trước Kiều.', @now - INTERVAL 3 HOUR),
('24000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000002', 'COIN', 'DAILY_REWARD', 10, 1030, 'MISSION', '36000000-0000-0000-0000-000000000001', 'Thưởng đăng nhập hằng ngày.', @now - INTERVAL 2 HOUR),
('24000000-0000-0000-0000-000000000008', '00000000-0000-0000-0000-000000000002', 'COIN', 'REFUND', -15, 1015, 'ADMIN_AUDIT', '99000000-0000-0000-0000-000000000002', 'Điều chỉnh lại số dư demo.', @now - INTERVAL 1 HOUR),
('24000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000003', 'COIN', 'DEPOSIT', 600, 600, 'PAYMENT', '23000000-0000-0000-0000-000000000002', 'Nạp Gói Theo Dõi Tháng.', @now - INTERVAL 5 DAY),
('24000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000003', 'GEM', 'DEPOSIT', 10, 10, 'PAYMENT', '23000000-0000-0000-0000-000000000002', 'Nhận đá đề cử từ gói nạp.', @now - INTERVAL 5 DAY),
('24000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000003', 'COIN', 'DONATION', -40, 560, 'DONATION', '27000000-0000-0000-0000-000000000002', 'Ủng hộ Quy Tắc Bao Lì Xì Đỏ.', @now - INTERVAL 3 DAY),
('24000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000003', 'COIN', 'PURCHASE', -20, 540, 'PURCHASE_ORDER', '25000000-0000-0000-0000-000000000002', 'Mở khóa chương trả phí.', @now - INTERVAL 2 HOUR),
('24000000-0000-0000-0000-000000000013', '00000000-0000-0000-0000-000000000003', 'GEM', 'RECOMMENDATION', -3, 7, 'STORY_RECOMMENDATION', '29000000-0000-0000-0000-000000000002', 'Đề cử Phật Trước Kiều.', @now - INTERVAL 1 HOUR),
('24000000-0000-0000-0000-000000000014', '00000000-0000-0000-0000-000000000003', 'COIN', 'DAILY_REWARD', 7, 547, 'MISSION', '36000000-0000-0000-0000-000000000002', 'Thưởng đọc chương.', @now - INTERVAL 30 MINUTE),
('24000000-0000-0000-0000-000000000015', '00000000-0000-0000-0000-000000000003', 'GEM', 'RECOMMENDATION', -4, 3, 'STORY_RECOMMENDATION', '29000000-0000-0000-0000-000000000003', 'Đề cử Quy Tắc Bao Lì Xì Đỏ.', @now - INTERVAL 20 MINUTE),
('24000000-0000-0000-0000-000000000016', '00000000-0000-0000-0000-000000000002', 'COIN', 'ADMIN_ADJUSTMENT', 500, 1515, 'ADMIN_AUDIT', '99000000-0000-0000-0000-000000000003', 'Cộng thêm Xu demo cho tài khoản dịch giả.', @now - INTERVAL 15 MINUTE),
('24000000-0000-0000-0000-000000000017', '00000000-0000-0000-0000-000000000002', 'GEM', 'ADMIN_ADJUSTMENT', 10, 25, 'ADMIN_AUDIT', '99000000-0000-0000-0000-000000000003', 'Cộng thêm đá đề cử demo cho tài khoản dịch giả.', @now - INTERVAL 15 MINUTE),
('24000000-0000-0000-0000-000000000018', '00000000-0000-0000-0000-000000000003', 'COIN', 'ADMIN_ADJUSTMENT', 250, 797, 'ADMIN_AUDIT', '99000000-0000-0000-0000-000000000004', 'Cộng thêm Xu demo cho tài khoản biên tập.', @now - INTERVAL 10 MINUTE),
('24000000-0000-0000-0000-000000000019', '00000000-0000-0000-0000-000000000003', 'GEM', 'ADMIN_ADJUSTMENT', 8, 11, 'ADMIN_AUDIT', '99000000-0000-0000-0000-000000000004', 'Cộng thêm đá đề cử demo cho tài khoản biên tập.', @now - INTERVAL 10 MINUTE)
ON DUPLICATE KEY UPDATE `amount` = VALUES(`amount`), `balance_after` = VALUES(`balance_after`), `description` = VALUES(`description`);

INSERT INTO `purchase_orders` (`id`, `user_id`, `story_id`, `purchase_type`, `total_coin`, `created_at`) VALUES
('25000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '11000000-0000-0000-0000-000000000001', 'SINGLE_CHAPTER', 20, @now - INTERVAL 4 HOUR),
('25000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000002', 'SINGLE_CHAPTER', 20, @now - INTERVAL 2 HOUR)
ON DUPLICATE KEY UPDATE `total_coin` = VALUES(`total_coin`);

INSERT INTO `purchase_order_items` (`order_id`, `chapter_id`, `coin_price`) VALUES
('25000000-0000-0000-0000-000000000001', '13000000-0000-0000-0000-000000000003', 20),
('25000000-0000-0000-0000-000000000002', '13000000-0000-0000-0000-000000000005', 20)
ON DUPLICATE KEY UPDATE `coin_price` = VALUES(`coin_price`);

INSERT INTO `chapter_unlocks` (`id`, `user_id`, `chapter_id`, `purchase_order_id`, `coin_paid`, `created_at`) VALUES
('26000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '13000000-0000-0000-0000-000000000003', '25000000-0000-0000-0000-000000000001', 20, @now - INTERVAL 4 HOUR),
('26000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', '13000000-0000-0000-0000-000000000005', '25000000-0000-0000-0000-000000000002', 20, @now - INTERVAL 2 HOUR)
ON DUPLICATE KEY UPDATE `coin_paid` = VALUES(`coin_paid`);

INSERT INTO `donations` (`id`, `user_id`, `team_id`, `story_id`, `gross_coin`, `commission_rate`, `commission_coin`, `team_net_coin`, `message`, `created_at`) VALUES
('27000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '03000000-0000-0000-0000-000000000001', '11000000-0000-0000-0000-000000000001', 60, 10.00, 6, 54, 'Ủng hộ team dịch tiếp truyện cổ đại nhẹ nhàng.', @now - INTERVAL 6 DAY),
('27000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', '03000000-0000-0000-0000-000000000002', '11000000-0000-0000-0000-000000000002', 40, 10.00, 4, 36, 'Twist chương 2 rất cuốn.', @now - INTERVAL 3 DAY)
ON DUPLICATE KEY UPDATE `gross_coin` = VALUES(`gross_coin`), `commission_coin` = VALUES(`commission_coin`), `team_net_coin` = VALUES(`team_net_coin`), `message` = VALUES(`message`);

INSERT INTO `team_ledger` (`id`, `team_id`, `type`, `gross_coin`, `platform_fee_coin`, `net_coin`, `reference_type`, `reference_id`, `created_at`) VALUES
('28000000-0000-0000-0000-000000000001', '03000000-0000-0000-0000-000000000001', 'DONATION', 60, 6, 54, 'DONATION', '27000000-0000-0000-0000-000000000001', @now - INTERVAL 6 DAY),
('28000000-0000-0000-0000-000000000002', '03000000-0000-0000-0000-000000000002', 'DONATION', 40, 4, 36, 'DONATION', '27000000-0000-0000-0000-000000000002', @now - INTERVAL 3 DAY),
('28000000-0000-0000-0000-000000000003', '03000000-0000-0000-0000-000000000001', 'CHAPTER_UNLOCK', 20, 4, 16, 'CHAPTER_UNLOCK', '26000000-0000-0000-0000-000000000001', @now - INTERVAL 4 HOUR),
('28000000-0000-0000-0000-000000000004', '03000000-0000-0000-0000-000000000002', 'CHAPTER_UNLOCK', 20, 4, 16, 'CHAPTER_UNLOCK', '26000000-0000-0000-0000-000000000002', @now - INTERVAL 2 HOUR)
ON DUPLICATE KEY UPDATE `gross_coin` = VALUES(`gross_coin`), `platform_fee_coin` = VALUES(`platform_fee_coin`), `net_coin` = VALUES(`net_coin`);

INSERT INTO `story_recommendations` (`id`, `user_id`, `story_id`, `gem_amount`, `created_at`) VALUES
('29000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '11000000-0000-0000-0000-000000000001', 5, @now - INTERVAL 3 HOUR),
('29000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000001', 3, @now - INTERVAL 1 HOUR),
('29000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003', '11000000-0000-0000-0000-000000000002', 4, @now - INTERVAL 50 MINUTE)
ON DUPLICATE KEY UPDATE `gem_amount` = VALUES(`gem_amount`);

INSERT INTO `ranking_snapshots` (`id`, `ranking_type`, `story_id`, `score`, `rank`, `period`, `snapshot_date`) VALUES
('31000000-0000-0000-0000-000000000001', 'VIEWS', '11000000-0000-0000-0000-000000000001', 22, 1, 'DAILY', @today),
('31000000-0000-0000-0000-000000000002', 'VIEWS', '11000000-0000-0000-0000-000000000002', 15, 2, 'DAILY', @today),
('31000000-0000-0000-0000-000000000003', 'VIEWS', '11000000-0000-0000-0000-000000000003', 9, 3, 'DAILY', @today),
('31000000-0000-0000-0000-000000000004', 'GEM_RECOMMENDATION', '11000000-0000-0000-0000-000000000001', 8, 1, 'DAILY', @today),
('31000000-0000-0000-0000-000000000005', 'COIN_REVENUE', '11000000-0000-0000-0000-000000000001', 20, 1, 'DAILY', @today)
ON DUPLICATE KEY UPDATE `score` = VALUES(`score`), `rank` = VALUES(`rank`);

INSERT INTO `notifications` (`id`, `user_id`, `type`, `title`, `message`, `target_type`, `target_id`, `target_url`, `is_read`, `created_at`) VALUES
('32000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'STORY_PUBLISHED', 'Truyện đã được xuất bản', 'Phật Trước Kiều đang hiển thị trong khu đề cử hôm nay.', 'STORY', '11000000-0000-0000-0000-000000000001', '/truyen/phat-truoc-kieu', FALSE, @now - INTERVAL 12 DAY),
('32000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', 'ADMIN', 'Cần rà soát bản thảo', 'Sau Khi Từ Hôn Với Tam Gia còn ghi chú biên tập ở chương 1.', 'STORY', '11000000-0000-0000-0000-000000000004', '/dashboard/content/stories', FALSE, @now - INTERVAL 45 MINUTE),
('32000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003', 'PAYMENT', 'Nạp Xu thành công', 'Gói Theo Dõi Tháng đã cộng 600 Xu và 10 đá đề cử.', 'PAYMENT', '23000000-0000-0000-0000-000000000002', '/wallet', TRUE, @now - INTERVAL 5 DAY)
ON DUPLICATE KEY UPDATE `message` = VALUES(`message`), `is_read` = VALUES(`is_read`);

INSERT INTO `notification_preferences` (`user_id`, `story_updates`, `team_updates`, `system_updates`, `payment_updates`, `updated_at`) VALUES
('00000000-0000-0000-0000-000000000001', TRUE, TRUE, TRUE, TRUE, @now),
('00000000-0000-0000-0000-000000000002', TRUE, TRUE, TRUE, TRUE, @now),
('00000000-0000-0000-0000-000000000003', TRUE, FALSE, TRUE, TRUE, @now)
ON DUPLICATE KEY UPDATE `story_updates` = VALUES(`story_updates`), `team_updates` = VALUES(`team_updates`), `system_updates` = VALUES(`system_updates`), `payment_updates` = VALUES(`payment_updates`);

INSERT INTO `missions` (`id`, `code`, `name`, `description`, `mission_type`, `target_count`, `reward_coin`, `reward_gem`, `daily_limit`, `is_active`, `created_at`, `updated_at`) VALUES
('36000000-0000-0000-0000-000000000001', 'daily-login', 'Đăng nhập hằng ngày', 'Mở web mỗi ngày để nhận Xu đọc truyện.', 'LOGIN', 1, 10, 0, 1, TRUE, @now - INTERVAL 30 DAY, @now),
('36000000-0000-0000-0000-000000000002', 'read-two-chapters', 'Đọc 2 chương', 'Hoàn thành hai chương bất kỳ trong ngày.', 'READ_CHAPTER', 2, 7, 0, 1, TRUE, @now - INTERVAL 30 DAY, @now),
('36000000-0000-0000-0000-000000000003', 'comment-story', 'Bình luận văn minh', 'Để lại một bình luận hữu ích dưới truyện hoặc chương.', 'COMMENT', 1, 0, 1, 1, TRUE, @now - INTERVAL 30 DAY, @now)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `description` = VALUES(`description`), `reward_coin` = VALUES(`reward_coin`), `reward_gem` = VALUES(`reward_gem`), `is_active` = VALUES(`is_active`);

INSERT INTO `user_mission_progress` (`user_id`, `mission_id`, `progress_date`, `progress`, `completed`, `claimed`, `claimed_at`) VALUES
('00000000-0000-0000-0000-000000000002', '36000000-0000-0000-0000-000000000001', @today, 1, TRUE, TRUE, @now - INTERVAL 2 HOUR),
('00000000-0000-0000-0000-000000000003', '36000000-0000-0000-0000-000000000002', @today, 2, TRUE, TRUE, @now - INTERVAL 30 MINUTE),
('00000000-0000-0000-0000-000000000003', '36000000-0000-0000-0000-000000000003', @today, 1, TRUE, FALSE, NULL)
ON DUPLICATE KEY UPDATE `progress` = VALUES(`progress`), `completed` = VALUES(`completed`), `claimed` = VALUES(`claimed`), `claimed_at` = VALUES(`claimed_at`);

INSERT INTO `referral_codes` (`id`, `user_id`, `code`, `created_at`) VALUES
('37000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'MAIANH2026', @now - INTERVAL 20 DAY),
('37000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', 'LINHCHI2026', @now - INTERVAL 18 DAY)
ON DUPLICATE KEY UPDATE `code` = VALUES(`code`);

INSERT INTO `referrals` (`id`, `referrer_id`, `referred_user_id`, `status`, `qualified_at`, `reward_coin`, `reward_gem`, `rewarded_at`, `created_at`) VALUES
('38000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', 'QUALIFIED', @now - INTERVAL 5 DAY, 0, 0, @now - INTERVAL 5 DAY, @now - INTERVAL 18 DAY)
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`), `qualified_at` = VALUES(`qualified_at`), `reward_coin` = VALUES(`reward_coin`), `reward_gem` = VALUES(`reward_gem`);

INSERT INTO `community_rooms` (`id`, `name`, `status`, `created_at`, `updated_at`) VALUES
('39000000-0000-0000-0000-000000000001', 'Sảnh truyện mới cập nhật', 'VISIBLE', @now - INTERVAL 20 DAY, @now),
('39000000-0000-0000-0000-000000000002', 'Góc đề cử Zhihu', 'VISIBLE', @now - INTERVAL 18 DAY, @now)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `status` = VALUES(`status`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `community_messages` (`id`, `room_id`, `user_id`, `content`, `reply_to_id`, `status`, `created_at`) VALUES
('40000000-0000-0000-0000-000000000001', '39000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'Tối nay lên lịch thêm chương cuối Phật Trước Kiều nhé.', NULL, 'VISIBLE', @now - INTERVAL 5 HOUR),
('40000000-0000-0000-0000-000000000002', '39000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000003', 'Mình đã rà xong tag Cổ Đại và Chữa Lành.', '40000000-0000-0000-0000-000000000001', 'VISIBLE', @now - INTERVAL 4 HOUR),
('40000000-0000-0000-0000-000000000003', '39000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Ưu tiên các truyện ngắn có phần giới thiệu rõ và số chương dưới 15.', NULL, 'VISIBLE', @now - INTERVAL 3 HOUR)
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`), `reply_to_id` = VALUES(`reply_to_id`);

INSERT INTO `site_settings` (`key`, `value`, `updated_by`, `updated_at`) VALUES
('home.featured_story_ids', '["11000000-0000-0000-0000-000000000001","11000000-0000-0000-0000-000000000002","11000000-0000-0000-0000-000000000003"]', '00000000-0000-0000-0000-000000000001', @now),
('home.hero_copy', '{"title":"Giới Truyện - Thế giới truyện chữ và truyện Zhihu","subtitle":"Đọc truyện ngắn, truyện full và audio demo trong một thư viện gọn."}', '00000000-0000-0000-0000-000000000001', @now),
('monetization.platform_fee_rate', '{"donation":0.10,"chapter_unlock":0.20}', '00000000-0000-0000-0000-000000000001', @now)
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`), `updated_by` = VALUES(`updated_by`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `site_documents` (`id`, `type`, `title`, `content`, `version`, `is_published`, `created_by`, `published_at`, `created_at`, `updated_at`) VALUES
('41000000-0000-0000-0000-000000000001', 'TERMS', 'Điều khoản sử dụng demo', 'Nội dung demo dùng cho môi trường phát triển Gioitruyen. Không đại diện cho điều khoản pháp lý chính thức.', 1, TRUE, '00000000-0000-0000-0000-000000000001', @now - INTERVAL 20 DAY, @now - INTERVAL 21 DAY, @now - INTERVAL 20 DAY),
('41000000-0000-0000-0000-000000000002', 'COMMUNITY_RULES', 'Quy tắc cộng đồng demo', 'Bình luận văn minh, không spam, không tiết lộ nội dung chương trả phí ở khu vực công khai.', 1, TRUE, '00000000-0000-0000-0000-000000000001', @now - INTERVAL 20 DAY, @now - INTERVAL 21 DAY, @now - INTERVAL 20 DAY)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `content` = VALUES(`content`), `is_published` = VALUES(`is_published`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `advertisements` (`id`, `name`, `type`, `image_url`, `target_url`, `placement`, `cooldown_seconds`, `max_clicks_per_day`, `priority`, `trigger_every_n_views`, `start_at`, `end_at`, `is_active`, `created_at`, `updated_at`) VALUES
('42000000-0000-0000-0000-000000000001', 'Banner nạp Xu đầu tháng', 'BANNER', '/assets/ads/topup-monthly.png', '/wallet', 'HOME', 600, 5, 1, NULL, @now - INTERVAL 7 DAY, @now + INTERVAL 23 DAY, TRUE, @now - INTERVAL 7 DAY, @now),
('42000000-0000-0000-0000-000000000002', 'Popup đọc tiếp truyện hot', 'POPUP', '/assets/ads/continue-hot-story.png', '/truyen/phat-truoc-kieu', 'STORY_OPEN', 600, 5, 2, 5, @now - INTERVAL 5 DAY, @now + INTERVAL 10 DAY, TRUE, @now - INTERVAL 5 DAY, @now),
('42000000-0000-0000-0000-000000000003', 'Global click affiliate demo', 'AFFILIATE_REDIRECT', NULL, 'https://gioitruyen.local/affiliate-demo', 'GLOBAL_CLICK', 600, 5, 10, NULL, NULL, NULL, TRUE, @now - INTERVAL 5 DAY, @now)
ON DUPLICATE KEY UPDATE `image_url` = VALUES(`image_url`), `target_url` = VALUES(`target_url`), `placement` = VALUES(`placement`), `cooldown_seconds` = VALUES(`cooldown_seconds`), `max_clicks_per_day` = VALUES(`max_clicks_per_day`), `priority` = VALUES(`priority`), `is_active` = VALUES(`is_active`), `updated_at` = VALUES(`updated_at`);

INSERT INTO `ad_events` (`id`, `advertisement_id`, `user_id`, `session_id`, `story_id`, `event_type`, `created_at`) VALUES
('43000000-0000-0000-0000-000000000001', '42000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'sess-ad-001', NULL, 'IMPRESSION', @now - INTERVAL 2 HOUR),
('43000000-0000-0000-0000-000000000002', '42000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'sess-ad-001', NULL, 'CLICK', @now - INTERVAL 1 HOUR),
('43000000-0000-0000-0000-000000000003', '42000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', 'sess-ad-002', '11000000-0000-0000-0000-000000000001', 'IMPRESSION', @now - INTERVAL 30 MINUTE)
ON DUPLICATE KEY UPDATE `event_type` = VALUES(`event_type`), `created_at` = VALUES(`created_at`);

INSERT INTO `admin_audit_logs` (`id`, `admin_id`, `action`, `entity_type`, `entity_id`, `old_data`, `new_data`, `ip_hash`, `created_at`) VALUES
('99000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'ADMIN_ADJUSTMENT', 'wallet', '21000000-0000-0000-0000-000000000002', '{"coin_balance":1000}', '{"coin_balance":1100,"reason":"demo reward"}', 'ip-admin-demo', @now - INTERVAL 8 DAY),
('99000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'ADMIN_ADJUSTMENT', 'wallet', '21000000-0000-0000-0000-000000000002', '{"coin_balance":1030}', '{"coin_balance":1015,"reason":"demo correction"}', 'ip-admin-demo', @now - INTERVAL 1 HOUR),
('99000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'ADMIN_ADJUSTMENT', 'wallet', '21000000-0000-0000-0000-000000000002', '{"coin_balance":1015,"gem_balance":15}', '{"coin_balance":1515,"gem_balance":25,"reason":"seed bonus"}', 'ip-admin-demo', @now - INTERVAL 15 MINUTE),
('99000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'ADMIN_ADJUSTMENT', 'wallet', '21000000-0000-0000-0000-000000000003', '{"coin_balance":547,"gem_balance":3}', '{"coin_balance":797,"gem_balance":11,"reason":"seed bonus"}', 'ip-admin-demo', @now - INTERVAL 10 MINUTE)
ON DUPLICATE KEY UPDATE `new_data` = VALUES(`new_data`), `created_at` = VALUES(`created_at`);

