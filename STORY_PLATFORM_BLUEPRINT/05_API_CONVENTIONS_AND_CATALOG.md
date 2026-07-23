# 05. Quy ước và danh mục API

OpenAPI khởi đầu nằm tại [api/story-platform.v1.yaml](api/story-platform.v1.yaml).

## 1. Chuẩn chung

- Base path: `/api/v1`.
- JSON UTF-8, tên trường `camelCase`.
- Thời gian ISO-8601 UTC, ví dụ `2026-07-23T08:30:00Z`.
- ID là string UUID, client không suy luận thứ tự.
- API version major nằm trong path; additive change không tăng major.
- Error theo `application/problem+json` với `type`, `title`, `status`, `code`, `detail`, `traceId`, `errors`.
- Header `X-Request-Id` nhận từ client nếu hợp lệ hoặc server sinh mới.
- Write có tác dụng tài chính/publish dùng `Idempotency-Key`.
- Public GET trả `ETag`, hỗ trợ `If-None-Match` và cache policy rõ.
- Pagination bằng opaque cursor: `items`, `nextCursor`, `hasMore`.
- `limit` mặc định 20, tối đa 100.
- Không cho client truyền tên cột tùy ý; `sort` là enum allowlist.

## 2. HTTP semantics

| Trường hợp | Status |
|---|---:|
| Tạo thành công | 201 |
| Command xử lý bất đồng bộ | 202 |
| Thành công không body | 204 |
| Validation | 400 |
| Chưa xác thực/token hết hạn | 401 |
| Đã xác thực nhưng thiếu quyền | 403 |
| Không thấy hoặc cố tình che tài nguyên | 404 |
| Version/idempotency conflict | 409 |
| Preconditions/ETag thất bại | 412 |
| Business rule không xử lý được | 422 |
| Rate limit | 429 |
| Lỗi không dự kiến | 500, không lộ stack/SQL |
| Dependency tạm lỗi | 503 |

Không trả `200` kèm `{ success: false }`.

## 3. Authentication

### Web

Khuyến nghị BFF/session cookie `HttpOnly; Secure; SameSite=Lax/Strict` thay vì để JavaScript giữ token. State-changing request bảo vệ CSRF bằng same-site strategy và anti-CSRF token khi cần.

### Mobile

OIDC Authorization Code + PKCE nếu dùng identity provider; nếu identity nội bộ, access token 5–10 phút và refresh token rotation. Refresh token chỉ lưu secure storage, server lưu hash và phát hiện reuse theo token family.

### Password

- Hash Argon2id với tham số benchmark theo hạ tầng, tối thiểu theo OWASP hiện hành.
- Cho phép password dài, kiểm tra breached/common password.
- Không bắt đổi định kỳ nếu không có dấu hiệu compromise.
- MFA/passkey cho admin, finance và Team quan trọng.

## 4. Concurrency và idempotency

- Entity mutable trả `version` hoặc ETag.
- Update dùng `If-Match`; mismatch trả 412/409.
- `Idempotency-Key` scope theo actor + route; server lưu request hash.
- Cùng key, cùng body trả response cũ; cùng key, body khác trả 409.
- Webhook topup/Cloudinary dedupe bằng provider event ID + signature verification.

## 5. Danh mục endpoint

Ký hiệu auth: `P` public, `U` user, `T` Team Member/Owner có permission tương ứng, `M` moderator, `A` admin. Admin kế thừa quyền Moderator và quyền tài chính/hỗ trợ; không có role Creator, Finance Operator hoặc Support.

### Identity

| Method | Endpoint | Auth | Chức năng |
|---|---|---:|---|
| POST | `/auth/register` | P | Tạo tài khoản pending verification |
| POST | `/auth/email/verify` | P | Xác minh email bằng token một lần |
| POST | `/auth/login` | P | Login, risk/rate check |
| POST | `/auth/refresh` | P | Rotate refresh token |
| POST | `/auth/logout` | U | Thu hồi phiên hiện tại |
| GET | `/auth/sessions` | U | Danh sách phiên đã mask |
| DELETE | `/auth/sessions/{sessionId}` | U | Thu hồi một phiên |
| POST | `/auth/password/forgot` | P | Luôn trả response không lộ email tồn tại |
| POST | `/auth/password/reset` | P | Token một lần, thu hồi phiên cũ |
| POST | `/auth/mfa/challenge` | U | Khởi tạo challenge |
| POST | `/auth/mfa/verify` | U | Xác minh/enroll MFA |

### User/Profile/Team

| Method | Endpoint | Auth | Chức năng |
|---|---|---:|---|
| GET/PATCH | `/me` | U | Xem/cập nhật hồ sơ của mình |
| DELETE | `/me` | U | Yêu cầu đóng/xóa tài khoản có grace period |
| GET | `/users/{id}` | P | Hồ sơ công khai tối thiểu |
| GET/POST | `/teams` | P/U | Danh sách/tạo Team; người tạo thành Owner |
| GET/PATCH | `/teams/{teamId}` | P/T | Chi tiết/cập nhật theo ownership |
| GET/POST | `/teams/{teamId}/members` | T | Owner xem/thêm hoặc mời thành viên |
| PATCH/DELETE | `/teams/{teamId}/members/{userId}` | T | Owner đổi permission/xóa thành viên |
| POST | `/team-invitations/{token}/accept` | U | Chấp nhận lời mời idempotent |
| PUT/DELETE | `/teams/{teamId}/follow` | U | Follow/unfollow idempotent |

### Catalog/Discovery

| Method | Endpoint | Auth | Chức năng |
|---|---|---:|---|
| GET | `/home` | P | Các section home versioned/cacheable |
| GET | `/stories` | P | Filter/sort/keyset pagination |
| GET | `/stories/{storyIdOrSlug}` | P | Chi tiết truyện published |
| GET | `/stories/{storyId}/chapters` | P | Danh sách chương published |
| GET | `/categories` | P | Taxonomy theo group |
| GET | `/search` | P | Search + facet |
| GET | `/search/suggestions` | P | Autocomplete rate-limited |
| GET | `/rankings/stories` | P | Ranking theo kỳ và metric |
| GET | `/rankings/teams` | P | Ranking Team theo kỳ |
| GET | `/recommendations` | U | Đề xuất cá nhân; có fallback public |

### Publishing

| Method | Endpoint | Auth | Chức năng |
|---|---|---:|---|
| POST/GET | `/teams/{teamId}/stories` | T | Tạo/list draft; cần membership active |
| GET/PATCH | `/teams/{teamId}/stories/{storyId}` | T | Xem/sửa draft với version và ownership |
| POST | `/teams/{teamId}/stories/{storyId}/submit` | T | Submit review idempotent |
| POST | `/teams/{teamId}/stories/{storyId}/schedule` | T | Lên lịch nếu approved và có `story:publish` |
| POST | `/teams/{teamId}/stories/{storyId}/chapters` | T | Tạo chapter draft |
| GET/PATCH | `/teams/{teamId}/chapters/{chapterId}` | T | Xem/sửa chapter draft |
| POST | `/teams/{teamId}/chapters/{chapterId}/submit` | T | Submit chapter review |
| POST | `/teams/{teamId}/chapters/{chapterId}/publish` | T/M/A | Publish theo policy |
| POST | `/teams/{teamId}/chapters/{chapterId}/unpublish` | T/M/A | Ẩn có reason/audit |
| GET | `/teams/{teamId}/analytics/overview` | T | Valid/raw view và conversion |

### Reading

| Method | Endpoint | Auth | Chức năng |
|---|---|---:|---|
| GET | `/chapters/{chapterIdOrSlug}` | P | Nội dung chương cacheable theo revision |
| POST | `/reading-sessions` | P/U | Tạo phiên đọc có signed session token |
| POST | `/reading-sessions/{id}/heartbeats` | P/U | Batch heartbeat sequence idempotent |
| POST | `/reading-sessions/{id}/complete` | P/U | Hoàn tất; không hứa view hợp lệ ngay |
| GET/PUT | `/me/reading-progress/{storyId}` | U | Đồng bộ tiến độ với optimistic version |
| GET | `/me/reading-history` | U | Lịch sử keyset pagination |
| DELETE | `/me/reading-history/{storyId}` | U | Xóa một lịch sử |
| PUT/DELETE | `/stories/{storyId}/favorite` | U | Favorite/unfavorite idempotent |

### Community/Moderation

| Method | Endpoint | Auth | Chức năng |
|---|---|---:|---|
| GET/POST | `/comments` | P/U | List theo target/tạo comment |
| PATCH/DELETE | `/comments/{commentId}` | U/M | Owner sửa/xóa; moderator action có audit |
| PUT/DELETE | `/reactions/{targetType}/{targetId}` | U | Toggle reaction idempotent |
| POST | `/reports` | U | Tạo report và deduplicate |
| GET | `/me/reports` | U | Theo dõi trạng thái report của mình |
| GET/PATCH | `/moderation/cases` | M | Queue và claim case |
| POST | `/moderation/cases/{id}/decisions` | M | Quyết định có reason code |
| POST | `/moderation/cases/{id}/appeals` | U/T | Kháng nghị trong thời hạn |

### Monetization

| Method | Endpoint | Auth | Chức năng |
|---|---|---:|---|
| GET | `/wallets/me` | U | Balance xu projection và account state |
| GET | `/wallets/me/transactions` | U | Ledger history keyset |
| POST/GET | `/wallets/me/topups` | U | Tạo/list topup; response có QR và transfer reference |
| POST | `/webhooks/topups/{provider}` | Provider | Nhận giao dịch, verify và match topup tự động |
| POST | `/admin/topups/{topupId}/approve-manual` | A | Duyệt manual có evidence/reason, chống double-credit |
| POST | `/admin/topups/{topupId}/reject` | A | Từ chối có reason |
| POST | `/donations` | U | Chuyển xu từ ví Reader sang Team, bắt buộc idempotency |
| GET | `/teams/{teamId}/rewards` | T | Reward theo kỳ và rule version |
| POST/GET | `/teams/{teamId}/withdrawals` | T | Tạo/list withdrawal, Owner hoặc permission tài chính |
| POST | `/admin/withdrawals/{id}/approve` | A | Risk review và thực hiện chi trả; không approve request của chính mình |
| POST | `/admin/withdrawals/{id}/reject` | A | Từ chối, hoàn reserved xu đúng một lần |
| GET | `/referrals/me` | U | Code, attribution, reward summary |
| GET/PATCH | `/admin/configuration/topup-discount` | A | Cấu hình tỷ lệ chiết khấu versioned/audited |

### Notification/Media/Admin

| Method | Endpoint | Auth | Chức năng |
|---|---|---:|---|
| GET | `/notifications` | U | Inbox keyset |
| POST | `/notifications/{id}/read` | U | Mark read idempotent |
| POST | `/notifications/read-all` | U | Mark read theo watermark |
| POST | `/media/upload-signatures` | U/T | Cấp Cloudinary signed upload params ngắn hạn |
| POST | `/webhooks/cloudinary` | Cloudinary | Verify/dedupe và cập nhật moderation/asset state |
| GET | `/admin/audit-logs` | A | Tra cứu audit, field nhạy cảm masked |
| GET | `/admin/jobs` | A | Queue/DLQ trạng thái tổng hợp |
| POST | `/admin/jobs/{id}/retry` | A | Retry có reason và audit |
| GET/PATCH | `/admin/configuration/{key}` | A | Cấu hình allowlist, versioned |

## 6. Filter `/stories`

```text
GET /api/v1/stories
  ?query=...
  &category=co-dai,ngot
  &ending=he
  &status=completed
  &origin=translated
  &teamId=...
  &minChapters=10
  &maxChapters=40
  &sort=updated_desc
  &cursor=...
  &limit=20
```

Server giới hạn số filter value, kiểm tra category tồn tại và không đưa raw MongoDB operator/pipeline/sort vào query.

## 7. Response mẫu

```json
{
  "items": [
    {
      "id": "0190d2d4-5e32-7b2f-8d87-2f44bf987abc",
      "slug": "tue-tue-giai-nghi",
      "title": "Tuế Tuế Giai Nghi",
      "cover": { "url": "https://cdn.example/...", "width": 400, "height": 600 },
      "completionStatus": "completed",
      "latestChapter": { "number": 17, "title": "Ngoại truyện" },
      "stats": { "views": 185698, "favorites": 47 }
    }
  ],
  "nextCursor": "eyJ2IjoxLCJ0IjoiLi4uIn0.signature",
  "hasMore": true
}
```

## 8. Problem Details mẫu

```json
{
  "type": "https://api.example.com/problems/version-conflict",
  "title": "Dữ liệu đã được cập nhật bởi phiên khác",
  "status": 409,
  "code": "CONCURRENCY_CONFLICT",
  "detail": "Hãy tải phiên bản mới nhất trước khi lưu lại.",
  "traceId": "00-4bf92f3577b34da6-00f067aa0ba902b7-01"
}
```

## 9. Rate limit tham chiếu

| Nhóm | Giới hạn khởi điểm |
|---|---|
| Login | 5/phút/account + 20/phút/IP, tăng backoff |
| Forgot password | 3/giờ/email-hash + IP, response đồng nhất |
| Search suggestion | 30/phút/IP hoặc user |
| Comment | 10/phút/user, thêm trust score |
| Report | 10/ngày/user, điều chỉnh theo uy tín |
| Reading heartbeat | Theo session cadence, reject sequence bất thường |
| Upload intent | 20/giờ/user, quota byte riêng |
| Topup/donation/withdrawal | Thấp, risk rule và idempotency bắt buộc |

Giới hạn thực tế phải dựa trên telemetry; trả 429 cùng `Retry-After` nhưng không tiết lộ rule chống gian lận chi tiết.

## 10. Contract governance

- OpenAPI là source of truth và được lint trong CI.
- Generate typed client cho web/mobile khi phù hợp.
- PR API phải có example, error cases, auth scope, rate limit và cache policy.
- Kiểm tra breaking change tự động.
- Field deprecated phải có thời hạn và telemetry usage.
- Không tái sử dụng field cũ với ý nghĩa mới.
