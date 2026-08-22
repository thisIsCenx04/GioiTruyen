# Chiến dịch PR

Nhóm xuất bản thuê độc giả quảng bá truyện **ra ngoài nền tảng** — TikTok, YouTube,
fanpage Facebook. Nền tảng không phát quảng cáo; nền tảng **giữ tiền, khớp người, và phân
xử khi hai bên bất đồng**.

Kế hoạch gốc và lý lẽ đằng sau từng quyết định: [../docs/PR_CAMPAIGN_PLAN.md](../docs/PR_CAMPAIGN_PLAN.md)

---

## Vì sao thiết kế như vậy

Công việc diễn ra ở nơi máy chủ **không nhìn thấy được**. Không luật nào chấm được một
video TikTok. Toàn bộ thiết kế xoay quanh câu hỏi: *làm sao trả tiền công bằng cho một
việc không kiểm chứng tự động được?*

Ba trụ:

1. **Ký quỹ trước** — creator nhìn thấy tiền đã có sẵn, không phải lời hứa
2. **Người duyệt** — vì không có luật nào thay được
3. **Mọi bước có thời hạn** — bên giữ tiền không có lý do gì phải vội, bên làm việc thì
   không đợi mãi được

---

## Hai loại nhiệm vụ

| | `OPEN` | `APPLY` |
|---|---|---|
| Cách nhận | Bấm là được, ai trước được trước | Gửi đơn, nhóm chọn |
| Chiếm suất khi nào | Ngay lúc bấm | Khi nhóm duyệt đơn |
| Dùng khi | Việc đơn giản, ai làm cũng được | Yêu cầu cao, cần xem kênh trước |

Quest `APPLY` có thể gom nhiều đơn hơn số suất — đơn không chiếm suất.

---

## Tiền

**Một khoản phí duy nhất: 10.000 Xu, thu khi đăng nhiệm vụ.**

Không có phần trăm. Mọi Xu trong ngân sách hoặc đến tay creator, hoặc quay về ví nhóm —
nên số tiền nhóm trả cho nền tảng là một con số họ biết trước khi đăng và không đổi về sau.

Phí này **không hoàn lại** trong mọi trường hợp, kể cả khi không ai nhận nhiệm vụ. Đó là
thứ khiến "tự thuê tài khoản phụ của mình" mất tiền chứ không được lợi.

### Ví dụ đã kiểm chứng bằng test

TikTok, 60.000 Xu/người × 5 suất, chỉ 1 người được duyệt:

| Thời điểm | Ví chủ nhóm | Ký quỹ | Trả creator | Phí giữ lại |
|---|---:|---:|---:|---:|
| Đăng | −310.000 | 300.000 | 0 | 10.000 |
| Duyệt 1 người | — | 240.000 | 60.000 | 10.000 |
| Đóng chiến dịch | **+240.000** | 0 | 60.000 | **10.000** |

Chủ nhóm chi tổng cộng **70.000** Xu: 60.000 cho creator, 10.000 phí đăng. Không có Xu nào
tự sinh hay biến mất.

---

## Thời hạn

| Bước | Hạn | Quá hạn thì sao |
|---|---|---|
| Đăng ký | **5 hoặc 10 ngày** | Đóng nhiệm vụ, hoàn phần chưa ai nhận |
| Nộp bài | **7 ngày** từ lúc nhận | Suất trả về kho cho người khác |
| Nhóm duyệt | **7 ngày** từ lúc nộp | **Tự động duyệt và trả Xu** |
| Giữ bài | 30 ngày sau khi duyệt | Nhóm được khiếu nại nếu bài bị gỡ |

**Tự động duyệt là điều kiện sống còn.** Không có nó, bên giữ tiền chỉ cần im lặng:
creator đã làm xong việc, không có gì để khiếu nại vì "chưa bị từ chối", và sẽ không ai
nhận nhiệm vụ thứ hai.

Chạy bằng `PrQuestScheduler`, 15 phút một lần. Không thể chạy theo request — người đang
câu giờ chính là người không gửi request nào.

---

## Giới hạn

| Mục | Giá trị |
|---|---|
| Thưởng mỗi người | tối thiểu 1.000 Xu |
| Tổng ngân sách | 1.000 – 10.000.000 Xu |
| Số suất | 1 – 100 |
| Giữ cùng lúc | 3 nhiệm vụ mỗi người |

Sàn đặt ở **mức mỗi người**, không phải tổng. 1.000 Xu chia 100 suất là 10 Xu/người —
không ai quay video lấy 10 Xu, mà nhiệm vụ vẫn chiếm chỗ trên bảng.

---

## Từ chối và khiếu nại

Từ chối **bắt buộc** ghi lý do và để lại kênh liên hệ (Facebook / Zalo / SĐT). Suất vẫn
thuộc về creator để sửa và nộp lại — từ chối là góp ý, không phải đuổi việc.

Khiếu nại **hai chiều**:

- **Creator → admin**: làm đúng yêu cầu nhưng không được trả
- **Nhóm → admin**: đã trả tiền rồi creator gỡ bài

Admin thấy đủ bốn thứ để phán xử: yêu cầu KPI gốc, link đã nộp, **ảnh chụp lúc nộp**
(link giờ có thể đã chết — đó là lý do giữ ảnh), và lời của cả hai bên.

Phán quyết **chuyển Xu thật** giữa hai ví, khoá `FOR UPDATE`, ghi sổ, báo cả hai bên. Ví
rỗng thì lấy được bao nhiêu lấy bấy nhiêu — ví trống không phải cách thoát phán quyết.

---

## Chống lạm dụng

| Đường tấn công | Chặn bằng |
|---|---|
| Một người nhận hai lần | `UNIQUE (quest_id, user_id)` ở tầng CSDL |
| Ôm hết suất của cả sàn | Trần 3 nhiệm vụ cùng lúc |
| Nhóm tự nhận nhiệm vụ của mình | Chặn `team_members` ACTIVE của nhóm đó |
| Tài khoản ảo tự thuê mình | Phí publish thu ngay, không hoàn |
| Giữ chỗ chết | Hạn nộp 7 ngày, quá hạn trả suất |
| Nhóm ôm tiền không duyệt | Quá hạn duyệt → tự trả |
| Creator gỡ bài sau khi nhận tiền | Cam kết giữ 30 ngày + nhóm khiếu nại ngược |

Chặn thành viên nhóm chỉ ngăn được trường hợp lười, không ngăn được tài khoản ảo. Thứ
thật sự làm việc đó vô nghĩa là **phí publish không hoàn**.

---

## Đã kiểm chứng

10 test chạy tiền thật trong CSDL:

| Test | Kiểm |
|---|---|
| Vòng đầy đủ | publish → nhận → nộp → duyệt → đóng, cân sổ từng bước |
| Tự động duyệt | quá hạn → creator được trả, `auto_approved = 1` |
| Trả suất | quá hạn nộp → `claimed_count` giảm, `FULL` → `OPEN` |
| Vượt số suất | suất cuối chỉ nhận được một lần |
| Nhận trùng | UNIQUE chặn, **không mất suất** |
| Thiếu Xu | từ chối publish, ví không đổi |
| Dừng giữa chừng | giữ tiền suất đã nhận |
| Quest APPLY | đơn không chiếm suất tới khi được chọn |
| Nộp lại sau từ chối | vẫn giữ suất |
| Từ chối thiếu lý do | bị chặn |

**Chưa test qua giao diện thật** — cần một tài khoản nhóm thật để chạy end-to-end.

---

## Chưa làm

- Màn admin chỉnh phí đăng (hiện cố định 10.000 Xu trong mã)
- Xu từ PR hiện **rút được như Xu thường**. Kế hoạch đề xuất tách riêng và bắt admin duyệt
  khi rút — đây là chốt chặn rửa tiền, **chưa triển khai**.
