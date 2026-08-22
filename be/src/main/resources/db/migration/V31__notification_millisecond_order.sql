-- Thứ tự hộp thư, khi nhiều thông báo tới trong cùng một giây.
--
-- `created_at` vốn chỉ có độ chính xác giây. Hộp thư sắp xếp theo cột này, nên
-- hai thông báo sinh ra cách nhau vài mili giây là một cặp bằng nhau - và MySQL
-- trả về theo thứ tự tuỳ ý. Người nhận có thể thấy "đã chuyển tiền cho bạn"
-- nằm trên "yêu cầu đã được duyệt", tức là đọc ngược trình tự sự việc.
--
-- Chuyện này chỉ mới lộ ra khi luồng rút tiền bắt đầu gửi nhiều thông báo cho
-- cùng một người trong thời gian ngắn; các luồng cũ mỗi lần chỉ gửi một cái nên
-- không ai để ý.
--
-- Bảng đang rất nhỏ (vài chục dòng) nên phép đổi này chạy trong tích tắc. Dữ
-- liệu cũ giữ nguyên, chỉ được đọc là .000 - vẫn đúng thứ tự với nhau.

ALTER TABLE `notifications`
    MODIFY COLUMN `created_at` TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
