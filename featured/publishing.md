# Đăng truyện

## Ai được làm gì

| Vai trò | Đăng truyện | Đặt giá chương | Xoá truyện | Chạy PR |
|---|:--:|:--:|:--:|:--:|
| OWNER | ✓ | ✓ | ✓ | ✓ |
| MANAGER | ✓ | ✓ | ✕ | ✓ |
| EDITOR | ✓ | ✓ | ✕ | ✕ |
| MEMBER | ✕ | ✕ | ✕ | ✕ |

Quyền đến từ dòng `team_members` đang `ACTIVE`, **không** từ `users.role`.

---

## Upload file

Định dạng: `.docx` `.epub` `.odt` `.rtf` `.html` `.txt` `.md`

Nhận dạng theo **magic byte**, không theo phần mở rộng — một file `.docx` thật ra là zip,
và người dùng hay đổi tên file.

### Ba nút, ba hành vi khác nhau

| Nút | Hành vi |
|---|---|
| **Upload file truyện** | Đọc cả file: tên truyện, thể loại, văn án, và tách chương |
| **Thêm file** | Chỉ tách chương. Không đọc lại thông tin truyện |
| **+ Thêm chương** | Thêm một chương trống để nhập tay |

**Thêm file** không phải "một file một chương". File có nhiều dòng phân chương thì ra
nhiều chương; không có dòng nào thì file đó thật sự là một chương và giữ nguyên toàn bộ
nội dung dù dài bao nhiêu.

### Đọc đầu file

```
Tên truyện: Xuyên Nhanh: Bản Năng Tồn Sinh Của Bug
Tác giả: Tuyết Nguyên U Linh
Thể loại: Cận Đại, HE, Hiện Đại, Ngôn Tình, …
Văn án                          ← nhãn, không cần dấu hai chấm
Cô là một bug, nhưng tuyệt đối…  ← nội dung giới thiệu
                                ← khoảng trống
Chương 1: Phụ Trợ Mạnh Nhất     ← từ đây là chương
```

Thể loại được đối chiếu với danh sách của trang (bỏ dấu, không phân biệt hoa thường,
`đ`≡`d`) và tick sẵn. Thể loại trang chưa có thì báo rõ, không đoán bừa.

### Sắp xếp nhiều file

Trình chọn file trả về theo thứ tự hệ điều hành — tức **bảng chữ cái**, nên `Chương 10`
đứng trước `Chương 2`. Hệ thống sắp lại theo số chương đọc từ tên file:

- `Chương 12`, `chuong-7`, `Chapter 3`, `C005` → 12, 7, 3, 5
- `Chương 12.5` → chèn đúng giữa 12 và 13
- `Quyen 3 - Chuong 12` → lấy **12**, không lấy 3
- Không có số → xếp cuối, đánh số nối tiếp chương cuối cùng

### Bảo đảm không mất chữ

Sau khi đọc, hệ thống đối chiếu:

```
số từ nguồn = số từ header + số từ tiêu đề chương + số từ nội dung
```

Lệch quá 0,5% thì cảnh báo. Đã kiểm chứng trên file 386 chương / 310.000 từ: **0 dòng
bị mất**.

### Cảnh báo thiếu chương

Mở một truyện đã đăng, nếu số chương nhảy cóc:

> ⚠️ Truyện đang thiếu **1 chương** ở giữa truyện. [Xem các chương đang thiếu]

Đọc từ **tiêu đề chương**, không từ `chapter_number` — cột đó luôn liền mạch 1…N vì được
đánh lại theo vị trí mỗi lần lưu.

Chỉ tính lỗ hổng **bên trong** khoảng: truyện bắt đầu từ chương 40 là quyển sau, không
phải thiếu 39 chương.

---

## Sửa chương

- **20 chương/trang**, có nhảy trang — bộ hoàn có thể tới 2.500 chương
- Nhãn trang đọc theo **số chương thật**: `Chương 382–386 · 385 chương`
- Chọn nhanh: cả trang · khoảng từ–đến · tất cả
- Đặt giá nhanh: *miễn phí N chương đầu, từ chương N+1 khoá X Xu*
- **Giá 0 = miễn phí**, không tạo chương PAID giá 0 (server từ chối vì mở khoá không mất gì)

### Xoá chương

Cần xác nhận, và danh sách chương sắp mất được liệt kê tên. Chương **đã có người mua**
luôn được giữ lại — xoá đi là lấy mất thứ người ta đã trả tiền.

---

## Giới hạn

| Mục | Trần | Vì sao |
|---|---|---|
| Chương mỗi lần lưu | 3.000 | Tomcat `max-part-count` 20.000 ÷ 6 phần mỗi chương |
| Nội dung mỗi lần lưu | 90 MB | Tránh timeout và request quá lớn |
| Giới thiệu truyện | 100.000 ký tự | Quyết định sản phẩm; cột `MEDIUMTEXT` chứa được nhiều hơn |
| Tiêu đề truyện / chương | 255 ký tự | Độ rộng cột |

---

## Lỗi đã sửa, ghi lại để không lặp

| Lỗi | Nguyên nhân |
|---|---|
| Sửa truyện làm mất giới thiệu, chỉ còn 500 ký tự | Form nạp `short_description` (teaser đã cắt) rồi lưu đè lên `description` |
| Sửa tên truyện làm chết mọi link cũ | `slug` bị suy lại từ tiêu đề ở mỗi lần lưu |
| Không xoá được chương | Chốt chặn từ chối **mọi** danh sách ngắn hơn, kể cả khi cố ý xoá |
| Chèn chương giữa bộ ghi đè nhầm chương | Đối chiếu theo vị trí thay vì theo id |
| Bộ >2.500 chương không lưu được | `max-part-count` 15.000 < 6 × 3.000 |
