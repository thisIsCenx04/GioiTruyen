# 06. Security baseline

Mục tiêu kiểm thử: OWASP ASVS 5.0 Level 2 cho toàn hệ thống; Identity, Admin, Publishing permission và Monetization áp dụng thêm control Level 3 theo risk assessment. API threat model bao phủ OWASP API Security Top 10.

## 1. Trust boundary và rủi ro

| Vùng | Rủi ro chính | Control |
|---|---|---|
| Cloudflare → origin | bypass edge, DDoS, bot | origin allowlist/mTLS, WAF, rate limit |
| Browser/mobile → API | token theft, BOLA, injection | secure session/JWT, ABAC, DTO allowlist |
| Team publishing | sửa/đăng truyện Team khác | active membership + permission + ownership |
| MongoDB | NoSQL injection, query abuse, data exposure | private network, least privilege, fixed query builders |
| Redis | cache poisoning, stale auth | key namespace, TTL, source-of-truth check |
| Cloudinary | arbitrary upload, XSS, webhook spoof | signed upload, restricted format, signature/replay check |
| Topup | QR/reference spoof, fake receipt, double credit | unique reference, provider dedupe, atomic transition, audit |
| Donation | double spend, donate ngoài hệ thống | MongoDB transaction, wallet-only XU, idempotency |
| Withdrawal | account takeover, insider abuse | MFA/re-auth, reserve atomically, risk review, audit |
| Admin | privilege abuse, mass export | MFA, JIT access, masked data, reason, immutable audit |

## 2. Identity và session

- Password hash Argon2id theo benchmark; không log password/token/OTP.
- Email verification và reset token ngẫu nhiên, single-use, hash khi lưu và TTL ngắn.
- Refresh token rotation theo family; reuse detection thu hồi cả family.
- Web ưu tiên HttpOnly + Secure + SameSite cookie; access token không lưu `localStorage`.
- CSRF token cho cookie-auth write request; kiểm tra Origin/Referer hợp lệ.
- MFA bắt buộc cho Admin và Team Owner khi thay destination/permission nhạy cảm.
- Re-auth cho đổi email, disable MFA, thêm owner, topup manual, đổi discount và approve withdrawal.
- Login, reset, MFA có rate limit theo account hash + IP + risk, response chống enumeration.

## 3. Authorization

Global role chỉ gồm `USER`, `MODERATOR`, `ADMIN` (cùng các trạng thái hệ thống cần thiết). `ADMIN` kế thừa quyền Moderator và đảm nhận tài chính/hỗ trợ ở giai đoạn hiện tại.

Không có `CREATOR`, `FINANCE_OPERATOR`, `SUPPORT`. Quyền xuất bản là ABAC:

```text
allow(action, user, team, resource) =
  user.active
  AND membership(user, team).state == active
  AND membership.permissions contains action
  AND resource.teamId == team.id
  AND transitionAllowed(resource.state, action)
```

- Team Owner mới được thêm/xóa member và cấp permission; không được xóa owner cuối.
- Owner không thể cấp permission cao hơn tập quyền của mình.
- Mọi endpoint lấy ID phải test BOLA/BFLA; query luôn include `teamId`/owner predicate.
- Admin override chỉ qua endpoint/use case rõ ràng, có reason và audit; không có hidden bypass.
- Cache authorization ngắn hạn phải gắn `membershipVersion/securityVersion` để revoke có hiệu lực.

## 4. Input/output và NoSQL injection

- JSON schema/DTO reject unknown field cho command nhạy cảm.
- Không map request thành `org.bson.Document`, raw criteria, aggregation pipeline hoặc SpEL.
- Filter/sort dùng enum allowlist; không nhận `$where`, `$regex`, JavaScript hoặc field path tùy ý.
- Giới hạn body, nesting depth, array length, string/HTML length và decompression ratio.
- Sanitize chapter/comment theo allowlist; CSP nghiêm, encode output theo context.
- Error theo `application/problem+json`, không lộ stack trace, Mongo query, secret hoặc internal ID nhạy cảm.
- Export đặt quota, async job, auth lại khi tải và URL download ngắn hạn.

## 5. Cloudinary/media

- Backend kiểm tra owner, purpose, MIME allowlist và quota trước khi ký.
- Signature chứa timestamp/folder/public ID prefix/transformation; TTL ngắn và không ký client parameter tùy ý.
- Chặn executable, HTML, SVG mặc định; kiểm tra magic bytes và decode/re-encode ảnh.
- Strip EXIF/GPS; scan/moderation trước khi asset được gắn public.
- Webhook xác minh signature/timestamp và dedupe notification ID.
- Evidence/copyright document dùng private/authenticated delivery; URL ký ngắn hạn.
- Không fetch arbitrary URL do người dùng cung cấp để tránh SSRF.

## 6. Topup

- QR và transfer reference chỉ sinh server-side, chứa reference không dự đoán được và expiry.
- Response hiển thị chính xác amount phải chuyển, discount snapshot và xu sẽ nhận.
- Automation match ít nhất provider transaction ID, amount và transfer reference.
- Unique index `{provider, providerEventId}` và `{bankTransactionRef}` ngăn replay.
- Manual approval chỉ cho Admin đã MFA/re-auth; phải có reason, evidence và bank transaction ref.
- Manual/automation dùng compare-and-set state trong cùng transaction với ledger; loser đọc kết quả đã credit thay vì credit lại.
- Không credit từ ảnh biên lai đơn thuần nếu chưa qua quy trình review.
- Thay discount tạo version mới; request cũ giữ snapshot. Mọi thay đổi config có before/after audit.
- Kill switch tắt automation nhưng vẫn giữ webhook/event trong pending review để không mất giao dịch.

## 7. Donation bằng xu

- Chỉ `POST /donations` từ ví đã xác thực; cấm provider/QR/external URL.
- Kiểm tra recipient Team active, amount integer > 0, limit/risk và available balance server-side.
- Debit Reader + credit Team + donation + outbox trong một MongoDB transaction.
- Idempotency key gắn user + route + request hash.
- Team profile/content không cho đăng QR donation; moderation rule phát hiện/cảnh báo link/QR thanh toán nếu chính sách cấm.
- Hoàn donation bằng compensating transaction, không sửa ledger cũ.

## 8. Withdrawal

- Rule server-side: min `100.000 xu`; `<1.000.000` phí `20.000`; `>=1.000.000` phí `0`.
- Gross amount được reserve nguyên tử khi request; không tin fee/net từ client.
- Destination phải verified, encrypted/masked; thay destination có MFA và cooling-off.
- Admin không approve request do chính tài khoản mình tạo; thao tác có reason và audit.
- Có velocity/amount/device/account-age/risk checks; high-risk có thể yêu cầu review thứ hai dù cùng role Admin.
- Provider call ngoài transaction; state `processing` có idempotency/provider reference và reconciliation.
- Reject/fail chỉ release reserve một lần qua transaction bù.

## 9. Secret, crypto và transport

- TLS mọi kết nối; HSTS ở Cloudflare.
- Secret trong managed secret store, rotate; tách key ký JWT, encryption và webhook.
- MongoDB/Redis private endpoint, TLS và credential riêng theo service.
- PII/destination mã hóa field-level khi risk assessment yêu cầu; log luôn masked.
- Ký cursor, upload params và internal callback bằng key có version.

## 10. Audit

Audit bắt buộc cho:

- role/permission/membership/owner change;
- moderation decision và Admin override;
- topup manual/reject, discount config, payment-event rematch;
- donation reversal, withdrawal approve/reject/settle;
- destination, MFA, secret/feature flag và production access.

Audit gồm actor, effective actor, action, target, before/after đã mask, reason, request/correlation ID, IP/device risk, timestamp. Audit append-only; không cho actor tự xóa.

## 11. Secure SDLC

- PR chạy SAST, dependency/secret/IaC/container scan, SBOM và artifact signing.
- Dependency pin/renovation có test; base image tối thiểu và non-root.
- Architecture test chặn domain phụ thuộc Spring/infrastructure và module truy cập collection của nhau.
- Code review tối thiểu 2 người cho auth, authorization, ledger, topup, donation, withdrawal và policy.
- DAST/API fuzz trên staging; pentest trước bật giao dịch thật và định kỳ.
- Incident playbook riêng cho account takeover, data leak, topup double-credit và wallet mismatch.

## 12. Security release gate

- Không có critical/high unresolved hoặc exception có owner/expiry được phê duyệt.
- BOLA/BFLA suite pass cho Team, story, chapter, wallet, topup, withdrawal và Admin.
- Concurrency/idempotency test chứng minh không double credit/double spend.
- Restore MongoDB, revoke session/permission, rotate secret và Cloudinary webhook verification đã diễn tập.
- Cloudflare không cache private response và origin không bị bypass theo thiết kế.

