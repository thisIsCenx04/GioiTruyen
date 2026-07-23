# 01. Phân tích sản phẩm và nghiệp vụ

## 1. Tầm nhìn

Xây dựng nền tảng truyện đa thiết bị cho phép độc giả khám phá và đọc nội dung nhanh; Team có công cụ cộng tác, xuất bản, phát triển cộng đồng và nhận doanh thu minh bạch; quản trị viên có thể kiểm duyệt, hỗ trợ, xử lý tài chính và đối soát bằng dữ liệu có thể kiểm toán. Hệ thống không hỗ trợ Creator hoạt động độc lập: người muốn đăng truyện phải là thành viên của một Team.

## 2. Giá trị khác biệt dự kiến

- Trải nghiệm đọc không bị chặn bởi quảng cáo chuyển hướng bắt buộc.
- “Đọc tiếp” đồng bộ web/mobile, thiết lập font/theme theo tài khoản.
- Search faceted đúng taxonomy thay vì trộn mọi thuộc tính thành thể loại.
- Dashboard Team phân biệt raw view, valid view, view bị loại và doanh thu dự kiến.
- Sổ cái xu bất biến, topup/donation/withdrawal có đối soát và lịch sử rõ ràng.
- Report cộng đồng có uy tín người báo, chống spam và SLA xử lý.
- Đề xuất nội dung giải thích được, có tùy chọn tắt cá nhân hóa.

## 3. Vai trò và phân quyền

| Vai trò | Quyền chính |
|---|---|
| Guest | Xem catalog, tìm kiếm, đọc nội dung công khai |
| Reader | Lưu tiến độ, yêu thích, theo dõi, bình luận, donate bằng xu, báo cáo |
| Team Member | Chỉ tạo/sửa/submit/publish nội dung của Team theo permission được Team Owner cấp |
| Team Owner | Thêm/xóa thành viên, cấp permission, quản lý tỷ lệ chia và tài khoản nhận tiền |
| Moderator | Duyệt nội dung, bình luận, report; không được sửa ví |
| Admin | Có toàn bộ quyền Moderator, quyền xử lý tài chính và hỗ trợ; cấu hình hệ thống có kiểm soát, không bypass audit |

Không tồn tại global role `Creator`, `Finance Operator` hoặc `Support`. Phân quyền dùng RBAC cho quyền tổng quát và ABAC/ownership cho tài nguyên. Policy xuất bản bắt buộc kiểm tra membership `active`, `story.teamId`, trạng thái tài nguyên và permission chi tiết như `story:create`, `story:edit`, `story:submit`, `story:publish`. Admin có thể thực hiện moderation ở giai đoạn scale nhỏ; mọi thao tác tài chính/hỗ trợ nhạy cảm vẫn cần MFA, re-auth, lý do và audit.

## 4. Bounded context/module

| Module | Trách nhiệm | Không phụ trách |
|---|---|---|
| Identity | Tài khoản, phiên, MFA, role/permission | Hồ sơ Team, ví |
| Profiles & Teams | Hồ sơ, Team, thành viên, follow | Xác thực mật khẩu |
| Catalog | Truyện, taxonomy, metadata, trạng thái | Nội dung chương dung lượng lớn |
| Publishing | Draft, revision, chapter, workflow duyệt/xuất bản | Search index |
| Reading | Nội dung đọc, tiến độ, bookmark, reading session | Quyết định doanh thu trực tiếp |
| Discovery | Home, search, recommendation, ranking read model | Source of truth truyện |
| Community | Comment, reaction, follow, notification intent | Moderation decision |
| Moderation | Report, case, strike, appeal, copyright takedown | Thực hiện chi trả withdrawal |
| Monetization | Wallet ledger, topup, donation xu, reward, referral, withdrawal | Tính raw view |
| Analytics | Event, valid view, aggregates, fraud score | Số dư ví chuẩn |
| Media | Upload, scan, transform, metadata | Business ownership |
| Notification | Inbox, push, email | Nghiệp vụ gốc tạo thông báo |
| Admin & Audit | Cấu hình, feature flag, immutable audit | Thay thế quyền domain |

## 5. Chức năng theo release

### MVP bắt buộc

- Đăng ký, xác minh email, đăng nhập, refresh rotation, quên mật khẩu.
- Catalog, taxonomy, tìm kiếm, lọc, truyện mới/hoàn thành/sáng tác.
- Trang truyện, danh sách chương, đọc chương, đọc tiếp.
- Team Owner thêm thành viên và cấp permission; chỉ thành viên Team được CRUD bản nháp, submit review, publish/unpublish.
- Follow, favorite, comment cơ bản, report.
- Admin moderation và audit log.
- Media upload an toàn.
- View validation mức cơ bản, ranking theo valid view.
- Notification inbox.

### Release 2

- Topup bằng QR/mã chuyển khoản, ví xu, donation xu, reward theo view, referral, withdrawal.
- Team analytics và báo cáo đối soát.
- Recommendation cá nhân hóa.
- Push/email notification.
- Audio link hoặc audio hosting.
- Copyright claim/appeal hoàn chỉnh.

### Release 3

- Subscription/premium chapter nếu có mô hình kinh doanh phù hợp.
- Revenue sharing trong Team.
- Experiment/A-B testing.
- Search service riêng và event analytics kho dữ liệu.
- Multi-region read hoặc disaster recovery nâng cao.

Không nên đưa tiền thật vào MVP trước khi ledger, idempotency, fraud controls, reconciliation và quy trình vận hành đã qua kiểm thử.

## 6. Luồng nghiệp vụ chính

### 6.1. Xuất bản truyện

```text
Draft story → Add/edit chapters → Submit review
            → Automated checks → Moderator review
            → Approved → Scheduled/Published → Outbox event
            → Cache invalidation + search indexing + follower notification
```

Trạng thái truyện: `draft`, `in_review`, `changes_requested`, `approved`, `published`, `suspended`, `archived`.  
Trạng thái chương: `draft`, `in_review`, `scheduled`, `published`, `hidden`, `archived`.

Mọi chuyển trạng thái phải kiểm tra transition hợp lệ, membership/permission của Team và ghi `content_revisions` cùng `audit_logs`. Xem đặc tả đầy đủ tại [11_PUBLISHING_WORKFLOW.md](11_PUBLISHING_WORKFLOW.md).

### 6.2. Đọc và ghi nhận view

```text
GET chapter → tạo reading session phía server
heartbeat có nhịp hợp lý → complete/timeout
→ raw event → rule/fraud validation → valid/invalid
→ aggregate theo giờ/ngày → ranking
→ khóa kỳ → reward calculation → ledger posting
```

View hợp lệ không dựa vào một tín hiệu duy nhất. Tối thiểu xét thời lượng, nhịp heartbeat, chapter visibility, user/session, duplicate window, IP/ASN risk, device signal, bot score và hành vi bất thường. Không lưu fingerprint xâm lấn vượt nhu cầu đã công bố.

### 6.3. Topup, donation và withdrawal

```text
Create topup request + idempotency key
→ server snapshot mức chiết khấu + sinh QR/nội dung chuyển khoản duy nhất
→ automation đối soát amount + transfer reference
→ credit xu hoặc chuyển pending_review
→ Admin duyệt manual có bằng chứng khi automation lỗi

Donate bằng xu: Reader wallet debit → Team wallet credit trong cùng transaction

Withdrawal: reserve xu → tính phí server-side → Admin risk review
→ chi trả đến destination → reconcile → settle/release reserve
```

- Mặc định topup chiết khấu 10%: nạp `100.000 VND` nhận `90.000 xu`, theo công thức `floor(amountVnd × (100 - discountPercent) / 100)`. Admin được cấu hình tỷ lệ; request phải snapshot version cấu hình lúc tạo.
- Donation chỉ sử dụng số dư xu, không có QR, external payment intent hoặc QR riêng của Team.
- Withdrawal tối thiểu `100.000 xu`; amount `< 1.000.000 xu` chịu phí `20.000 xu`, amount `>= 1.000.000 xu` miễn phí. `amount` là tổng xu bị trừ, `net = amount - fee`.
- Event automation đến trùng phải trả kết quả giống nhau. Duyệt manual và automation phải tranh chấp bằng atomic state transition để không credit hai lần.

### 6.4. Report và appeal

```text
User report → deduplicate → triage → case
→ moderator decision → action/strike/reject
→ notify parties → appeal window
→ appeal decision → final state
```

Report có thưởng chỉ được tạo reward sau quyết định cuối và qua anti-collusion check.

## 7. Quy tắc nghiệp vụ cần chốt trước coding

| ID | Quyết định cần Product/Legal/Finance xác nhận |
|---|---|
| BR-01 | Định nghĩa view hợp lệ và duplicate window |
| BR-02 | View của thành viên/Team có được tính không |
| BR-03 | Công thức reward, trần, kỳ khóa và điều chỉnh |
| BR-04 | Tỷ lệ quy đổi xu/VNĐ, thuế, KYC và destination validation; min/phí withdrawal đã chốt theo mục 6.3 |
| BR-05 | Quy tắc chia doanh thu giữa thành viên Team |
| BR-06 | Chính sách hoàn xu donation và xử lý ví âm khi reversal |
| BR-07 | Tuổi tối thiểu, rating và nội dung nhạy cảm |
| BR-08 | Quyền xuất bản bản dịch và bằng chứng bản quyền |
| BR-09 | Thời gian xử lý report/takedown/appeal |
| BR-10 | Retention dữ liệu, xóa tài khoản và legal hold |
| BR-11 | Giới hạn referral và xử lý self-referral/collusion |
| BR-12 | Điều kiện verified Team và thu hồi xác minh |

## 8. KPI

- Activation: tỷ lệ đọc chương đầu trong phiên đầu.
- Engagement: chapter/session, completion rate, D1/D7/D30 retention.
- Discovery: search success, zero-result rate, CTR home → story.
- Team publishing: active publishing member, publish lead time, Team retention.
- Quality: report/10k chapters, upheld report rate, moderation SLA.
- Monetization: topup conversion/reconciliation, donation xu, valid-view reward, withdrawal success.
- Trust: invalid view rate, chargeback rate, account takeover rate.
- Reliability: availability, p95/p99 latency, 5xx, queue lag.

## 9. Ngoài phạm vi ban đầu

- DRM tuyệt đối hoặc ngăn mọi hình thức sao chép.
- Blockchain/crypto token.
- Microservice cho từng module ngay từ ngày đầu.
- Multi-region active-active write.
- Tự động quyết định khóa tài khoản chỉ bằng mô hình AI không có review.
