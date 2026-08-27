# Giới Truyện — Tổng quan hệ thống

Tài liệu mô tả kiến trúc, công nghệ và các luồng nghiệp vụ của nền tảng đọc
truyện Giới Truyện. Viết cho người mới nhận bàn giao, hoặc cho chính mình sáu
tháng sau.

**Cập nhật:** 24/08/2026 · **Môi trường thật:** https://gioitruyen.com

---

## 1. Hình dạng hệ thống

Một khối đơn (monolith) chạy trên một VPS. Không microservice, không hàng đợi
phân tán, không Kubernetes. Lựa chọn này là có chủ đích: lượng truy cập hiện tại
không đòi hỏi, và một khối đơn dễ tra lỗi hơn nhiều khi chỉ có một, hai người
bảo trì.

```
Trình duyệt
    │  HTTPS
    ▼
nginx  ── /assets/, /  ─────────────►  pm2 → Node tĩnh (cổng 3000)
    │                                   phục vụ bản build của React
    └── /api/v1/  ──────────────────►  systemd → Spring Boot (127.0.0.1:8080)
                                            │
                                            ├── MySQL 8.4 (127.0.0.1:3306)
                                            └── Redis (127.0.0.1:6379) — đang chạy, chưa dùng
```

Backend **chỉ lắng nghe trên loopback**. Mọi thứ từ ngoài vào đều phải qua
nginx — đây là tiền đề cho toàn bộ phần bảo mật ở mục 7.

---

## 2. Công nghệ

### Backend

| Thành phần | Lựa chọn | Ghi chú |
|---|---|---|
| Ngôn ngữ | Java 21 | Dùng record, text block, switch biểu thức |
| Khung | Spring Boot 3.5.4 | MVC servlet, không WebFlux |
| Truy cập dữ liệu | Spring JDBC (`JdbcClient`, `NamedParameterJdbcTemplate`) | SQL viết tay; JPA chỉ dùng cho vài entity |
| CSDL | MySQL 8.4 | InnoDB, utf8mb4_unicode_ci |
| Migration | Flyway | Đang ở **V31**, chạy tự động lúc khởi động |
| Xác thực | JWT ký HS256 + refresh token lưu CSDL | Access token sống 30 phút |
| Kiểm thử | JUnit 5, AssertJ, Mockito, jqwik | 268 file nguồn, ~122 test đơn vị |

**Vì sao SQL viết tay thay vì JPA.** Phần lớn truy vấn ở đây là báo cáo và
tổng hợp — bảng xếp hạng, sổ doanh thu, đối chiếu ví. JPA làm những việc đó
bằng cách sinh SQL mà không ai đọc lại được, còn ở đây SQL nằm ngay trong mã và
tra được từng câu.

### Frontend

| Thành phần | Lựa chọn |
|---|---|
| Khung | React 18 + Vite 5 |
| Ngôn ngữ | TypeScript 5 (strict) |
| Định tuyến | React Router 6 — **63 route** |
| Kiểu dựng | SPA, không SSR |
| Kiểm thử | Vitest + Testing Library — 255 test |
| Quản lý gói | pnpm workspace |

Kho frontend chia thành `apps/web` và ba gói dùng chung: `api-client` (lớp gọi
API sinh kiểu), `ui` (component nền), `config`.

**Không có thư viện quản lý trạng thái.** Không Redux, không Zustand, không
React Query. Trạng thái nằm trong component, và các phần cần đồng bộ chéo thì
nói chuyện với nhau qua sự kiện `window` (`auth-change`, `combo-purchased`).
Cách này đơn giản nhưng có giới hạn — xem mục 9.

### Hạ tầng

- **nginx** — TLS (Certbot), reverse proxy, phục vụ `/uploads/`, và từ 24/08/2026 là lớp chặn DDoS.
- **systemd** — `gioitruyen-backend`, chạy JAR đóng gói sẵn.
- **pm2** — `gioitruyen-fe` phục vụ thư mục `dist` ở cổng 3000. Có thêm `gioitruyen-fe-staging` ở 3001.
- **Triển khai** — `upload_vps.ps1`: build, đẩy JAR + `dist`, sao lưu bản cũ thành `application.jar.bak`, khởi động lại dịch vụ, rồi kiểm tra sức khoẻ. Có `-DryRun`.

---

## 3. Các miền nghiệp vụ

Backend chia theo miền, mỗi miền có `api / application / domain / infrastructure`:

| Miền | Việc nó lo |
|---|---|
| `auth` | Đăng ký, đăng nhập, refresh token, đặt lại mật khẩu, chống dò mật khẩu |
| `catalog` | Kho truyện công khai, chương, tìm kiếm, kệ hiển thị, phân quyền đọc |
| `monetization` | Ví, nạp xu, mua chương, mua combo, ủng hộ, rút tiền |
| `promotion` | Chiến dịch PR, ký quỹ, khiếu nại; gói quảng bá truyện |
| `quest` | Nhiệm vụ hằng ngày và phần thưởng |
| `teams` | Nhóm dịch/xuất bản, thành viên, không gian làm việc |
| `engagement` | Phiên đọc, tiến độ đọc, đề cử |
| `community`, `moderation`, `media`, `tts`, `system`, `admin` | Bình luận, kiểm duyệt, tệp tải lên, đọc bằng giọng nói, cấu hình, bàn quản trị |

---

## 4. Dữ liệu

**66 bảng.** Các bảng cốt lõi quanh dòng tiền:

```
users ──┬── wallets (coin_balance, gem_balance)
        ├── wallet_transactions   ← sổ cái, append-only
        ├── payments              ← đơn nạp xu
        └── withdrawal_requests   ← đơn rút tiền

teams ──┬── team_members (OWNER / MANAGER / EDITOR / MEMBER)
        ├── team_ledger           ← doanh thu gộp / phí / thực nhận
        └── stories ── chapters
                          │
                          ├── chapter_unlocks        UNIQUE(user_id, chapter_id)
                          └── story_combo_purchases  UNIQUE(user_id, story_id)
```

Hai ràng buộc `UNIQUE` đó không phải trang trí: chúng là thứ chặn việc bấm mua
hai lần thành trừ tiền hai lần. Giao dịch thứ hai vi phạm ràng buộc và bị cuộn
ngược toàn bộ.

---

## 5. Đơn vị tiền tệ

- **Xu** — tiền chính. Nạp bằng chuyển khoản, tiêu để mở chương, mua combo, ủng hộ nhóm, ký quỹ nhiệm vụ PR. Rút được ra tiền thật.
- **Ngọc** — tiền phụ, dùng cho đề cử. Không rút được.

Số dư là `BIGINT` với ràng buộc `balance_after >= 0` trên sổ cái — không có
khái niệm ví âm.

---

## 6. Các luồng tiền

### 6.1 Nạp xu

```
Người dùng chọn gói → DRAFT (chỉ là màn hình QR, không ai thấy)
       │ bấm "Tôi đã chuyển khoản"
       ▼
   PENDING  ──► admin duyệt ──► PAID + cộng ví
       │                        (SELECT FOR UPDATE, kiểm lại trạng thái)
       ├──► admin từ chối ──► CANCELLED
       ├──► người dùng tự huỷ ──► CANCELLED
       └──► quá 2 giờ ──► CANCELLED (bộ quét tự dọn)
```

Trạng thái `DRAFT` tồn tại vì một lý do cụ thể: mở màn hình QR không phải là
tuyên bố đã chuyển tiền. Trước khi có nó, mỗi lần tải lại trang ví lại đẩy thêm
một đơn vào hàng chờ duyệt.

Giới hạn: mỗi tài khoản gửi tối đa **3 xác nhận / phút**.

### 6.2 Mua truyện và phí nền tảng

**Đây là quy tắc kinh doanh quan trọng nhất trong hệ thống.**

| Loại truyện (`stories.story_type`) | Phí nền tảng | Nhóm nhận |
|---|---|---|
| `EXCLUSIVE` (độc quyền) | 10% | 90% |
| `ORIGINAL` (sáng tác) | 10% | 90% |
| `TEXT`, `AUDIO` (đăng lại) | 30% | 70% |

Ví dụ combo 90.000 xu: truyện độc quyền → nhóm nhận **81.000**; truyện đăng lại
→ nhóm nhận **63.000**.

Phí làm tròn **xuống**, nên phần lẻ luôn rơi về phía nhóm.

Ủng hộ (donate) tính riêng: phí cố định **10%**.

> **Lưu ý về dữ liệu cũ.** Trước 24/08/2026 mọi truyện đều bị tính chung một
> mức 20%. Các giao dịch phát sinh trước mốc đó vẫn mang con số sai trong
> `team_ledger` và `wallet_transactions`.

Khi người đăng chọn diện độc quyền, form đăng truyện bắt tick vào một **cam kết
độc quyền** — nhắc nhở, không ràng buộc pháp lý, và không lưu xuống CSDL.

### 6.3 Doanh thu nhóm

Tiền của nhóm chảy thẳng vào **ví của chủ nhóm**, đồng thời ghi một dòng
`team_ledger` (gộp / phí / thực nhận) và một dòng `wallet_transactions` loại
`EARNING`.

Chỗ chọn chủ nhóm dùng `member_role = 'OWNER' ORDER BY joined_at LIMIT 1`. Vì
vậy **mỗi nhóm chỉ được có đúng một OWNER** — nếu có hai, tiền chạy về người vào
trước, bất kể ai mới là chủ thật.

### 6.4 Rút tiền

```
PENDING_REVIEW ──► APPROVED ──► PAID ──► COMPLETED (người rút xác nhận)
       │              │           └──► DISPUTED (người rút báo chưa nhận)
       └──────────────┴──► REJECTED + hoàn xu
```

Xu bị trừ **ngay lúc gửi yêu cầu**, không phải lúc duyệt — nên huỷ là phải hoàn.
Việc hoàn được chốt bằng `refunded_at IS NULL`: hai quản trị viên cùng bấm huỷ
thì chỉ một người ghi được mốc đó.

Phí: miễn phí từ 1.000.000 xu trở lên, dưới mức đó thu 20.000 xu. Tối thiểu
100.000 xu mỗi lần.

### 6.5 Nhiệm vụ PR

Nhóm đăng nhiệm vụ → trả **phí đăng** (không hoàn) và **ký quỹ** phần thưởng.
Người dùng nhận suất → làm → gửi kết quả → nhóm duyệt → xu ra khỏi ký quỹ về ví
người làm.

Nếu nhóm im lặng quá hạn duyệt, hệ thống **tự duyệt và trả tiền**. Đây là quy
tắc chống bên giữ tiền chây ì — không có nó thì không ai nhận nhiệm vụ thứ hai.

Khiếu nại đi lên bàn quản trị viên, và phán quyết có thể chuyển xu giữa hai bên.

---

## 7. Bảo mật

### Đã có

**Lớp nginx** (áp dụng 24/08/2026):

| Vùng | Hạn mức | Áp cho |
|---|---|---|
| `gt_auth` | 20 req/phút, burst 10 | `/api/v1/login`, `/register`, `/refresh`, `/auth/*` |
| `gt_api` | 25 req/giây, burst 50 | `/api/v1/*` còn lại |
| `gt_general` | 50 req/giây, burst 100 | Trang web |
| `gt_conn` | 20–60 kết nối đồng thời | Theo từng khu vực |

Khoá theo `$binary_remote_addr` — địa chỉ TCP nginx nhìn thấy, không giả được.

**Lớp ứng dụng:**

- `RateLimitingFilter` — 60 req/giây, 600/phút chung, 15/phút cho auth, 60/phút cho lệnh ghi.
- `LoginAttemptLimiter` — 5 lần sai trong 15 phút thì khoá 15 phút.
- `ClientIp` — nguồn địa chỉ duy nhất, chỉ tin `X-Forwarded-For` / `X-Real-IP` khi yêu cầu đến từ loopback, và lấy **chặng cuối** chứ không phải chặng đầu.
- Hạn mức nghiệp vụ: 3 xác nhận nạp/phút, 2 thành viên nhóm/24 giờ, tối đa 10 tài khoản mỗi nhóm, giới hạn số suất PR mỗi người.
- Đổi vai trò hoặc khoá tài khoản → thu hồi toàn bộ refresh token.

> **Vì sao `ClientIp` quan trọng.** nginx dùng `$proxy_add_x_forwarded_for`, tức
> là *nối* IP thật vào cuối chuỗi client gửi lên. Mã cũ lấy `split(",")[0]` —
> đúng phần do người gọi tự viết. Đổi một dòng tiêu đề mỗi lần gọi là mỗi lần
> được cấp một ô đếm mới, và **toàn bộ hạn mức theo IP coi như không tồn tại**.
> Lỗi này có ở bốn nơi và đã được gom về một chỗ, có 7 test riêng.

### Chưa có

- Không CAPTCHA ở đăng ký và bình luận.
- Không có bộ phát hiện bot theo hành vi (chỉ có hạn mức tần suất).
- Không có chốt chặn riêng cho việc thổi lượt đề cử.
- Không chống DDoS ở tầng mạng — nếu bị đánh lớn thì cần Cloudflare hoặc tương đương đứng trước.
- Hạn mức lưu trong bộ nhớ tiến trình; chạy nhiều bản backend thì phải chuyển sang Redis (Redis đã có sẵn trên máy).

---

## 8. Quy ước viết mã

**Chốt trạng thái trước, chuyển tiền sau.** Mọi thao tác đụng tiền phải giành
quyền bằng một câu `UPDATE` có điều kiện trạng thái, kiểm số dòng đổi được, rồi
mới động vào ví:

```sql
UPDATE pr_quest_claims
   SET status = 'APPROVED'
 WHERE id = ? AND status = 'SUBMITTED';   -- 0 dòng = người khác vừa xử lý
```

Đọc trạng thái không khoá rồi ghi mà không nhắc lại điều kiện là công thức trả
tiền hai lần. Đã có bốn lỗi đúng kiểu này trong quá khứ.

**Khoá ví trước khi đọc số dư.** `SELECT ... FOR UPDATE`, không bao giờ đọc rồi
ghi đè một giá trị tuyệt đối mà không khoá.

**Ràng buộc CSDL là chốt chặn thật.** Giao diện che nút không phải là bảo vệ;
endpoint luôn gọi thẳng được.

**Bình luận giải thích *vì sao*, không phải *cái gì*.** Phần lớn bình luận
trong kho này ghi lại một lỗi đã từng xảy ra và lý do đoạn mã có hình dạng hiện
tại. Đừng xoá chúng khi refactor.

---

## 9. Nợ kỹ thuật đã biết

| Vấn đề | Ảnh hưởng |
|---|---|
| Phí 20% của dữ liệu cũ chưa được đối chiếu lại | Nhóm độc quyền bị thu thừa, nhóm thường được trả dư |
| Nhóm có thể đang tồn tại hai OWNER từ trước | Doanh thu chạy về ví chủ cũ |
| 6 trang dashboard thiếu lớp bọc `.adminDashboard` | Nội dung dính sát mép màn điện thoại |
| Danh sách lớn chưa phân trang | Trang audio dựng danh sách từ khối trang chủ, không gọi API danh mục |
| `AdminFinanceController.reverse` đảo được hai lần | Admin có thể vô tình tạo tiền |
| 36 file test bị cách ly trong `pom.xml` | Viết cho Spring Boot 4, chưa port ngược |
| Hạn mức nằm trong bộ nhớ | Chỉ đúng khi chạy một bản backend |
| Không có bộ đo hiệu năng / cảnh báo | Chỉ có `/actuator/health` |

---

## 10. Chạy tại máy

```bash
# CSDL
GioiTruyen/db/start-local-mysql.ps1

# Backend  (cần JWT_SIGNING_KEY ≥ 32 byte và CORS_ALLOWED_ORIGINS)
cd be && mvn spring-boot:run

# Frontend
cd fe && pnpm install && pnpm dev:web
```

Kiểm thử:

```bash
cd be && mvn test                    # test đơn vị
cd fe/apps/web && npx vitest run     # test giao diện
cd fe/apps/web && npx tsc --noEmit   # kiểm kiểu
```

Một số bài kiểm tra luồng tiền (`PrQuestFlowTest`, `WithdrawalFlowTest`) chạy
với CSDL thật và tự bỏ qua nếu không kết nối được — muốn chạy thì đặt biến
`MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`.

Triển khai:

```powershell
.\upload_vps.ps1 -DryRun    # xem sẽ làm gì
.\upload_vps.ps1            # build, đẩy, khởi động lại, kiểm tra sức khoẻ
```

Lùi bản trên VPS:

```bash
mv -f /opt/gioitruyen-backend/application.jar.bak /opt/gioitruyen-backend/application.jar
systemctl restart gioitruyen-backend
```
