# Chiến Dịch PR — kế hoạch tính năng

> Trạng thái: **đề xuất, chưa viết dòng code nào.**
>
> Nhóm xuất bản thuê độc giả quảng bá truyện **ra ngoài nền tảng** — fanpage Facebook,
> TikTok, YouTube. Nền tảng không phát quảng cáo; nền tảng **giữ tiền, khớp người, và phân
> xử khi hai bên bất đồng**.

|                |                                                                                              |
| -------------- | -------------------------------------------------------------------------------------------- |
| Bản chất     | Chợ dịch vụ hai chiều, có ký quỹ                                                      |
| Bảng mới     | 4                                                                                            |
| Giai đoạn    | 3                                                                                            |
| Rủi ro chính | Owner ôm tiền không duyệt · Slot bị giữ chỗ chết · Tài khoản ảo tự thuê mình |

---

## 1. Bản chất tính năng

Đây **không phải** quảng cáo hiển thị trên web. Đây là **chợ thuê dịch vụ**:

- **Bên mua** — chủ nhóm, có Xu, cần truyện được biết đến
- **Bên bán** — độc giả có kênh TikTok/YouTube/fanpage, có khả năng làm nội dung
- **Nền tảng** — giữ ký quỹ, khớp hai bên, thu phí, và xử tranh chấp

Công việc diễn ra ở nơi nền tảng **không nhìn thấy được**. Đó là điều định hình toàn bộ
thiết kế: mọi thứ ở đây đều xoay quanh câu hỏi *làm sao trả tiền công bằng cho một việc mà
máy chủ không tự kiểm chứng được*.

Câu trả lời là: **ký quỹ trước, con người duyệt, admin phân xử, và có thời hạn cho mọi bước.**

---

## 2. Vòng lặp

1. **Chủ nhóm tạo nhiệm vụ PR** trong tab mới của bảng điều khiển nhóm.
   Điền: nền tảng (TikTok / YouTube / Facebook / khác), KPI cụ thể, thưởng mỗi người, số
   suất, hạn đăng ký.
2. **Publish → trừ ví nhóm ngay** — `(thưởng × số suất) + phí nền tảng`, chuyển sang ký quỹ.
   Không đủ Xu thì không publish được.
3. **Nhiệm vụ lên trang Nhiệm vụ** — mọi độc giả đủ điều kiện đều thấy.
4. **Độc giả bấm Nhận** — hết suất thì nút khoá lại. Ai commit trước ở tầng CSDL thì được
   (xem mục 5).
5. **Làm nội dung ngoài nền tảng** → **nộp link** (+ ảnh chụp màn hình nên có và k bắt buộc).
6. **Chủ nhóm duyệt.**
   - Duyệt → trả Xu ngay từ ký quỹ vào ví độc giả.
   - Từ chối → **bắt buộc nhập lý do + kênh liên hệ** (Facebook / Zalo / số điện thoại) để
     hai bên trao đổi tiếp.
7. **Kết thúc** — hết hạn hoặc chủ nhóm dừng. Phần ký quỹ chưa dùng hoàn về ví nhóm.

---

## 3. Trang Nhiệm vụ

Hai tab.

### Tab "Nhiệm vụ"

Hai cột:

| Cột trái —**60%**                                                      | Cột phải —**40%**                                   |
| ------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| **Nhiệm vụ PR** đang mở                                               | **Nhiệm vụ của tôi** — PR đã nhận, đang làm  |
| Sắp xếp: mới nhất · thưởng cao nhất · sắp hết hạn · còn ít suất | Bên dưới:**nhiệm vụ hằng ngày** như hiện tại |
| Lọc: nền tảng, mức thưởng, còn suất                                     |                                                              |

Mỗi thẻ nhiệm vụ PR hiện: tên truyện, nhóm, nền tảng, KPI, **thưởng/người**, `đã nhận / tổng suất`, hạn đăng ký, và **"Đã ký quỹ N Xu"** — dòng cuối để độc giả tin rằng tiền đã nằm sẵn,
không phải hứa suông.

### Tab "Lịch sử"

Chỉ nhiệm vụ PR. Mỗi dòng: nhiệm vụ, ngày nhận, ngày nộp, kết quả (Đã duyệt / Bị từ chối /
Quá hạn), Xu nhận được, và lý do từ chối nếu có.

---

## 4. Tiền và ký quỹ

### Ví dụ theo đúng case bạn đưa

Nhiệm vụ: *TikTok, video 1000+ view* — **60.000 Xu/người × 5 suất**, phí nền tảng 15%.

| Thời điểm             |          Ví nhóm | Ký quỹ | Đã trả creator |    Phí nền tảng |
| ------------------------ | -----------------: | -------: | ----------------: | -----------------: |
| Lúc publish             |          −345.000 |  300.000 |                 0 | 45.000 (trừ mất) |
| 3 người được duyệt |                    |  120.000 |           180.000 |                    |
| Hết hạn, hoàn lại    | **+147.000** |        0 |           180.000 |   **27.000** |

### Phí tính trên phần thực trả, không tính trên phần hoàn

Đây là chỗ ví dụ của bạn chưa nói rõ, và nó quan trọng.

Nếu giữ nguyên 45.000 phí dù chỉ trả 180.000, thì **nhóm bị phạt vì nền tảng không kiếm đủ
creator** — lỗi không phải của họ. Đề xuất: thu phí trên phần thực trả (15% × 180.000 =
27.000), hoàn lại 120.000 + 18.000 phí thừa = **147.000 Xu**.

Phí thu ở mức tối đa lúc publish rồi hoàn phần thừa, chứ không thu sau — nếu thu sau thì
nhóm có thể rút sạch ví trước khi bị thu.

**Làm tròn**: phí làm tròn **xuống** đơn vị Xu. Chênh lệch nghiêng về phía người dùng, không
nghiêng về nền tảng.

### Chủ nhóm dừng giữa chừng

Suất **đã có người nhận** thì tiền đã cam kết — không hoàn. Chỉ hoàn phần suất chưa ai nhận.
Người đang làm dở vẫn có quyền nộp và được duyệt bình thường; dừng chiến dịch chỉ đóng
đường đăng ký mới.

---

## 5. Đồng bộ khi nhiều người cùng bấm Nhận

Bạn hỏi riêng phần này. Cách làm **sai** phổ biến là đọc rồi ghi:

```sql
SELECT claimed_count FROM pr_quests WHERE id = ?;   -- đọc: 4/5
-- ...hai request cùng đọc được 4...
INSERT INTO pr_claims ...                            -- cả hai cùng ghi → 6/5
```

Hai người bấm cùng lúc đều đọc thấy còn suất, cả hai cùng ghi, quest vượt số suất và ký quỹ
không đủ trả. Khoảng trống giữa `SELECT` và `INSERT` là chỗ hỏng.

**Cách đúng — một câu UPDATE có điều kiện, tự nguyên tử:**

```sql
UPDATE pr_quests
   SET claimed_count = claimed_count + 1
 WHERE id = :questId
   AND status = 'OPEN'
   AND claimed_count < slot_count
   AND registration_ends_at > NOW();
```

Rồi kiểm tra số dòng bị ảnh hưởng:

- `1` → giành được suất, ghi tiếp `pr_claims` **trong cùng transaction**
- `0` → hết suất, hoặc quest đã đóng, hoặc quá hạn → trả về "Nhiệm vụ đã đủ người"

MySQL khoá dòng đó trong lúc UPDATE, nên hai request buộc phải xếp hàng. Không cần khoá thủ
công, không cần hàng đợi, không có race. **Ai commit trước thì được** — đúng nghĩa ai nhanh
tay hơn.

Thêm một lớp nữa ở tầng CSDL:

```sql
UNIQUE KEY uk_claim_once (quest_id, user_id)
```

Một người bấm Nhận hai lần — hoặc bấm hai lần thật nhanh trên hai tab — thì lần thứ hai
va vào `UNIQUE` và bị từ chối, bất kể tầng ứng dụng có kiểm tra hay không.

**Giao diện**: nút Nhận khoá lại ngay khi bấm (tránh double-submit), và trang nhiệm vụ poll
lại số suất mỗi ~15 giây để `4/5` không đứng yên khi thực tế đã đầy.

---

## 6. Ràng buộc và hạn mức

| Ràng buộc                       | Giá trị                             | Ghi chú                                  |
| --------------------------------- | ------------------------------------- | ----------------------------------------- |
| Hạn đăng ký | **5 hoặc 10 ngày** | Chọn từ danh sách, không nhập tự do |
| Tổng ngân sách                 | 1.000 – 10.000.000 Xu                |                                           |
| Số suất                         | 1 – 100                              |                                           |
| **Thưởng mỗi người**   | **tối thiểu 1.000 Xu**        | *Bổ sung — xem mục 7*                |
| Phí nền tảng                   | 5–15%, admin cấu hình              | Hiện trên form trước khi publish      |
| Hạn nộp bài sau khi nhận      | **7 ngày**                     | *Bổ sung — xem mục 7*                |
| Hạn chủ nhóm phải duyệt      | **7 ngày kể từ lúc nộp**   | *Bổ sung — xem mục 7*                |
| Số nhiệm vụ PR giữ cùng lúc | **3**                           | *Bổ sung — xem mục 7*                |

---

## 7. Những chỗ hỏng cần vá

Đây là phần trả lời câu *"còn vấn đề gì ở đây"*. Bảy chỗ dưới đây đều có thể làm tính năng
chết trong tháng đầu.

### 7.1 — Chủ nhóm im lặng, creator mất trắng

**Nghiêm trọng nhất.** Creator bỏ công quay video thật, nộp link, rồi chủ nhóm **không bấm
gì cả**. Xu nằm trong ký quỹ, creator không được trả, không có gì để khiếu nại vì "chưa bị
từ chối". Chỉ cần vài lần như vậy là không ai nhận nhiệm vụ PR nữa.

**Vá:** hạn duyệt **7 ngày**. Quá hạn → **tự động duyệt và trả tiền**. Chủ nhóm muốn từ chối
thì phải chủ động từ chối, kèm lý do. Im lặng nghĩa là đồng ý.

Không có luật này thì mọi luật khác đều vô nghĩa, vì bên nắm tiền không có lý do gì phải
hành động.

### 7.2 — Slot bị giữ chỗ chết

5 suất, 5 người bấm Nhận, 3 người biến mất. Nhiệm vụ đứng im đến hết hạn, chủ nhóm không
tuyển được ai thay, tiền kẹt trong ký quỹ.

**Vá:** nhận nhiệm vụ có **hạn nộp 7 ngày**. Quá hạn mà chưa nộp → claim huỷ, **suất trả về
kho**, người khác nhận được. Kèm hạn mức **3 nhiệm vụ PR cùng lúc** mỗi người, để không ai
ôm hết suất của cả sàn.

### 7.3 — Nhóm tự thuê chính mình

Chủ nhóm tạo nhiệm vụ, tài khoản phụ nhận, chủ nhóm duyệt. Xu đi từ ví nhóm sang ví cá nhân.
Nếu Xu rút được ra tiền thật thì đây là **đường rửa tiền**, và phí nền tảng chính là cái giá
rẻ để rửa.

**Vá, ba lớp:**

1. Chặn `team_members` ACTIVE của nhóm đó nhận nhiệm vụ của nhóm mình — chặn được trường hợp
   lười, không chặn được tài khoản ảo.
2. **Xu nhận từ PR đánh dấu riêng.** Tiêu trong trang thoải mái; **rút ra tiền thật thì phải
   qua admin duyệt.** Đây là lớp thật sự chặn được.
3. Admin thấy được: cặp nhóm ↔ người nhận lặp lại bất thường, thời gian duyệt gần bằng 0,
   tài khoản mới tinh nhận nhiệm vụ giá trị cao.

### 7.4 — Creator gỡ bài sau khi nhận tiền

Video được duyệt, tiền đã trả, hôm sau video bị xoá hoặc để riêng tư. Nhóm trả tiền cho
không.

**Vá:** cam kết **giữ bài tối thiểu 30 ngày**, ghi rõ trên nhiệm vụ. Trong 30 ngày đó chủ
nhóm có quyền **khiếu nại ngược** kèm ảnh chụp. Nghĩa là khiếu nại phải **hai chiều** —
plan bạn mô tả mới có chiều creator → admin.

Bắt buộc **nộp ảnh chụp màn hình cùng lúc với link**, không phải chỉ link. Link có thể chết;
ảnh chụp là bằng chứng tại thời điểm nộp.

### 7.5 — Thưởng quá thấp, không ai làm

Luật hiện tại cho phép 1.000 Xu tổng chia 100 suất = **10 Xu/người**. Không ai quay TikTok
lấy 10 Xu, nhưng nhiệm vụ vẫn chiếm chỗ trên sàn và làm loãng trang.

**Vá:** đặt sàn ở **mức thưởng mỗi người**, không phải ở tổng. Tối thiểu 1.000 Xu/người.
Hệ quả: 100 suất thì ngân sách tối thiểu là 100.000 Xu — hợp lý cho một chiến dịch 100 video.

### 7.6 — Không biết mình đang làm cho ai

Creator không có cách nào biết nhóm nào duyệt sòng phẳng, nhóm nào ngâm. Bỏ ba tiếng quay
video là canh bạc.

**Vá:** trên mỗi thẻ nhiệm vụ hiện **hồ sơ trả tiền của nhóm** — số nhiệm vụ đã đăng, tỉ lệ
duyệt, thời gian duyệt trung bình. Ba con số này lấy thẳng từ dữ liệu, không cần ai chấm
điểm. Nhóm ngâm bài sẽ tự lộ, và tự mất người nhận.

### 7.7 — Nhóm không lọc được người làm

Ai cũng nhận được, kể cả tài khoản TikTok 0 follower. Chủ nhóm duyệt xong mới biết.

**Vá:** người dùng khai **kênh của mình** trong hồ sơ (link + số follower). Nhiệm vụ đặt được
**yêu cầu follower tối thiểu**. Lọc trước khi nhận, không phải lọc sau khi đã tốn thời gian
của cả hai bên.

Giai đoạn 1 có thể để số follower là tự khai — chủ nhóm nhìn link tự đánh giá. Không cần
xác minh tự động ngay.

---

## 8. Dữ liệu

### Bốn bảng mới

```
pr_quests
  id, team_id, story_id, created_by
  platform              -- TIKTOK | YOUTUBE | FACEBOOK | OTHER
  title, requirement    -- KPI viết rõ: "video 1000+ view, gắn link truyện"
  min_followers         -- 0 = không yêu cầu
  reward_xu             -- mỗi người
  slot_count, claimed_count, approved_count
  escrow_xu, paid_xu, fee_rate, fee_reserved_xu, fee_charged_xu
  status                -- DRAFT | OPEN | FULL | CLOSED | CANCELLED
  registration_ends_at
  submit_window_days    -- mặc định 7
  review_window_days    -- mặc định 7
  content_hold_days     -- mặc định 30
  created_at, updated_at
```

```
pr_claims
  id, quest_id, user_id
  status                -- CLAIMED | SUBMITTED | APPROVED | REJECTED
                        --  | EXPIRED | CANCELLED
  claimed_at, submit_due_at
  submitted_at, submission_url, submission_note
  reviewed_at, reviewed_by, review_due_at
  reject_reason, contact_channel, contact_handle
  paid_xu, paid_at
  UNIQUE (quest_id, user_id)     -- một người một suất
```

```
pr_submission_files              -- ảnh chụp màn hình kèm bài nộp
  id, claim_id, media_url, uploaded_at
```

```
pr_disputes                      -- khiếu nại, hai chiều
  id, claim_id
  raised_by                      -- user_id
  raised_role                    -- CREATOR | TEAM
  reason, evidence_urls
  status                         -- OPEN | RESOLVED | DISMISSED
  admin_id, admin_note, penalty_xu
  created_at, resolved_at
```

### Dùng lại nguyên vẹn

- `team_ledger` — Xu rời ví nhóm, phí, và hoàn lại
- `wallets` + `wallet_transactions` — trả Xu cho creator
- `notifications` — báo mọi bước có hạn (xem dưới)
- `admin_audit_logs` — mọi phán quyết khiếu nại và mọi lần phạt Xu

### Thông báo bắt buộc

Mọi bước có thời hạn đều phải có thông báo, nếu không thì thời hạn chỉ là cái bẫy:

| Sự kiện                                | Báo cho   |
| ---------------------------------------- | ---------- |
| Có người nhận nhiệm vụ             | Chủ nhóm |
| Có bài nộp                            | Chủ nhóm |
| Còn 2 ngày phải duyệt                | Chủ nhóm |
| **Tự động duyệt do quá hạn** | Cả hai    |
| Được duyệt / bị từ chối           | Creator    |
| Còn 2 ngày phải nộp bài             | Creator    |
| Claim hết hạn, mất suất              | Creator    |
| Nhiệm vụ hết hạn, đã hoàn Xu      | Chủ nhóm |
| Có khiếu nại mới                     | Admin      |

### Việc chạy nền

Một job định kỳ (15 phút/lần) xử lý mọi thứ hết hạn: claim quá hạn nộp → `EXPIRED`, trả suất
về kho; bài quá hạn duyệt → **tự duyệt và trả tiền**; quest quá hạn đăng ký → đóng và hoàn
Xu. Không có job này thì toàn bộ luật thời hạn ở trên chỉ là chữ trong tài liệu.

---

## 9. Bảng điều khiển nhóm — tab mới

Tab hiện tại: **Thống kê · Quản lý truyện · Quản lý nhóm**
Thêm: **Chiến dịch PR**

Trong tab: danh sách chiến dịch (đang mở / đã đóng), nút tạo mới, và **hàng đợi duyệt bài** —
thứ chủ nhóm mở ra hằng ngày. Mỗi bài chờ duyệt hiện link, ảnh chụp, và **đếm ngược đến hạn
tự động duyệt**, để không ai lỡ hạn vì không biết.

---

## 10. Lộ trình

### Giai đoạn 1 — sàn chạy được (~1,5 tuần)

- 4 bảng + migration
- Tab Chiến dịch PR: tạo, xem, dừng chiến dịch
- Ký quỹ + hoàn, nguyên khối trong một transaction
- Nhận suất bằng UPDATE nguyên tử + `UNIQUE` (mục 5)
- Nộp link + ảnh chụp
- Duyệt / từ chối kèm lý do và kênh liên hệ
- Job hết hạn: **tự duyệt, trả suất, hoàn Xu**
- Trang Nhiệm vụ hai cột + tab Lịch sử
- Tách Xu-PR khỏi Xu rút được

### Giai đoạn 2 — công bằng và tin cậy (~1 tuần)

- Khiếu nại **hai chiều** + màn xử lý cho admin
- Admin phạt Xu nhóm, ghi `admin_audit_logs`
- Hồ sơ trả tiền của nhóm hiện trên thẻ nhiệm vụ
- Kênh cá nhân + yêu cầu follower tối thiểu
- Bộ lọc và sắp xếp đầy đủ
- Cảnh báo gian lận cho admin

### Giai đoạn 3 — đo hiệu quả (~4 ngày)

- Mỗi chiến dịch: chi phí, số bài đạt, chi phí mỗi bài
- Lượt xem truyện trước / trong / sau chiến dịch
- So sánh PR với Bố cáo trên cùng một truyện
- Chỉnh phí nền tảng theo số liệu thật

---

## 11. Cần chốt trước khi làm

1. **Phí tính trên phần thực trả hay trên toàn ngân sách?**
   Đề xuất: **thực trả**. Giữ tối đa lúc publish, hoàn phần thừa.
2. **Xu kiếm từ PR có rút ra tiền thật được không?**
   Đề xuất: tiêu trong trang thì tự do, **rút phải qua admin duyệt**. Đây là chốt chặn rửa
   tiền duy nhất thật sự hiệu quả.
3. **Tự động duyệt sau 7 ngày — đồng ý không?**
   Đây là điều kiện sống còn của tính năng. Nếu không đồng ý thì cần cơ chế khác buộc chủ
   nhóm phải trả lời, chứ không thể để trống.
4. **Phí nền tảng bao nhiêu, và ai đổi được?**
   5–15% là dải bạn đưa. Cần một con số mặc định và một màn cấu hình cho admin.
5. **Nhóm nào được đăng nhiệm vụ PR?**
   Mọi nhóm, hay chỉ nhóm đã xuất bản tối thiểu N truyện? Mở cho tất cả sẽ có nhóm lập ra
   chỉ để chuyển Xu.
6. **Admin phạt Xu nhóm — trừ vào đâu khi ví nhóm trống?**
   Ghi âm ví, hay khoá rút tiền đến khi trả đủ? Cần quyết định trước khi có ca đầu tiên.

---

*Các con số thời hạn (7 ngày nộp, 7 ngày duyệt, 30 ngày giữ bài) là đề xuất khởi điểm, chỉnh
được trong cấu hình admin.*
