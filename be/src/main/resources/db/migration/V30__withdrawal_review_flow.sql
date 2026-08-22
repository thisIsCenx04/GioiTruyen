-- Vòng đời của một yêu cầu rút tiền, từ lúc gửi tới lúc hai bên cùng xác nhận.
--
-- Trước đây bảng chỉ có mỗi cột `state` và không có một dòng mã nào ghi vào nó:
-- mọi yêu cầu nằm mãi ở PENDING_REVIEW, quản trị viên duyệt hay huỷ thì người
-- rút vẫn thấy "Chờ duyệt". Xu đã bị trừ ngay lúc gửi, nên một yêu cầu bị huỷ
-- mà không hoàn lại là người dùng mất trắng số xu đó.
--
-- Hai trạng thái mới khép kín vòng đời:
--   COMPLETED - người rút đã bấm xác nhận nhận được tiền.
--   DISPUTED  - người rút báo chưa nhận được, kèm ghi chú, trả về cho admin.

ALTER TABLE `withdrawal_requests`
    MODIFY COLUMN `state` ENUM(
        'PENDING_REVIEW',
        'APPROVED',
        'PROCESSING',
        'PAID',
        'COMPLETED',
        'DISPUTED',
        'REJECTED',
        'FAILED'
    ) NOT NULL DEFAULT 'PENDING_REVIEW';

ALTER TABLE `withdrawal_requests`
    -- Lời của quản trị viên, người rút đọc được. Huỷ thì bắt buộc phải có.
    ADD COLUMN `admin_note` VARCHAR(500) NULL AFTER `note`,
    -- Mã giao dịch ngân hàng, để người rút đối chiếu với sao kê của mình.
    ADD COLUMN `transfer_reference` VARCHAR(160) NULL AFTER `admin_note`,
    ADD COLUMN `reviewed_by` VARCHAR(36) NULL AFTER `transfer_reference`,
    ADD COLUMN `reviewed_at` TIMESTAMP(3) NULL AFTER `reviewed_by`,
    -- Mốc chuyển tiền, tách khỏi mốc duyệt: duyệt hôm nay chuyển ngày mai là
    -- chuyện bình thường, và khoảng cách giữa hai mốc chính là thứ cần đo.
    ADD COLUMN `paid_at` TIMESTAMP(3) NULL AFTER `reviewed_at`,
    ADD COLUMN `confirmed_at` TIMESTAMP(3) NULL AFTER `paid_at`,
    -- Lời của người rút khi báo chưa nhận được tiền.
    ADD COLUMN `confirm_note` VARCHAR(500) NULL AFTER `confirmed_at`,
    -- Chốt chặn hoàn xu: chỉ hoàn đúng một lần cho một yêu cầu, kể cả khi hai
    -- quản trị viên cùng bấm huỷ trong một tích tắc.
    ADD COLUMN `refunded_at` TIMESTAMP(3) NULL AFTER `confirm_note`;

-- Hàng đợi của quản trị viên lọc theo trạng thái rồi xếp theo thời gian; chỉ
-- mục `idx_withdrawal_requests_state` từ V27 đã phục vụ đúng truy vấn đó, nên
-- ở đây không thêm chỉ mục nào nữa.
