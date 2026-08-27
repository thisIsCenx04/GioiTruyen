-- Dấu tích xanh xác nhận nhóm.
--
-- Trạng thái (`status`) trả lời "nhóm còn hoạt động không", còn tích xanh trả
-- lời "ban quản trị đã xác minh nhóm này chưa" - hai câu hỏi khác nhau. Gộp
-- chúng vào một cột ENUM sẽ khiến việc tạm khoá một nhóm đã xác minh làm mất
-- luôn dấu xác minh, và bật lại phải xác minh từ đầu.
--
-- Lưu mốc thời gian chứ không phải cờ boolean: khi có khiếu nại, câu hỏi đầu
-- tiên luôn là "xác nhận lúc nào, ai bấm".

ALTER TABLE `teams`
    ADD COLUMN `verified_at` TIMESTAMP NULL DEFAULT NULL AFTER `status`,
    ADD COLUMN `verified_by` VARCHAR(36) NULL DEFAULT NULL AFTER `verified_at`;

-- Bảng nhóm rất nhỏ, nhưng trang chủ và danh bạ đều lọc theo cột này nên vẫn
-- đánh chỉ mục để hai truy vấn đó không phải quét toàn bảng khi nhóm nhiều lên.
CREATE INDEX `idx_teams_verified_at` ON `teams` (`verified_at`);
