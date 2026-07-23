# 04. Mô hình dữ liệu MongoDB và query

## 1. Nguyên tắc

- MongoDB là source of truth; Redis và search/read model đều có thể rebuild.
- Thiết kế collection/index từ access pattern, không chuyển nguyên mô hình SQL sang document.
- Embed dữ liệu nhỏ, có vòng đời chung; reference dữ liệu tăng không giới hạn hoặc có quyền sở hữu riêng.
- Không để array thành viên, chapter, comment, ledger entry tăng vô hạn trong một document.
- Mọi document có `_id`, `createdAt`; document mutable có `updatedAt`, `version`.
- Lưu thời gian UTC, số tiền/xu bằng integer 64-bit hoặc `Decimal128`; không dùng floating point.
- API không trả MongoDB document trực tiếp.

## 2. Collection cốt lõi

### Identity và Team

| Collection | Field chính |
|---|---|
| `users` | `_id`, emailNormalized, passwordHash, globalRoles, state, securityVersion |
| `sessions` | `_id`, userId, refreshHash, familyId, expiresAt, revokedAt, device |
| `teams` | `_id`, slug, name, ownerUserId, state, verification, walletAccountId |
| `team_memberships` | `_id`, teamId, userId, role, permissions, state, invitedBy, joinedAt |
| `team_invitations` | `_id`, teamId, targetUserId/emailHash, permissions, tokenHash, expiresAt, state |

`Creator` không phải role/document độc lập. Quyền đăng truyện được suy ra từ `team_memberships.state=active` và permission. Unique index `{teamId:1,userId:1}` ngăn membership trùng.

### Catalog và Publishing

| Collection | Field chính |
|---|---|
| `stories` | `_id`, teamId, slug, title, aliases, synopsis, taxonomy, status, currentRevision, publishedAt, version |
| `story_revisions` | `_id`, storyId, revisionNo, snapshot, createdBy, checksum, createdAt |
| `chapters` | `_id`, storyId, teamId, number, slug, title, status, currentRevision, scheduledAt, publishedAt, version |
| `chapter_revisions` | `_id`, chapterId, revisionNo, content, plainText, checksum, createdBy, createdAt |
| `moderation_reviews` | `_id`, targetType, targetId, submittedRevision, state, checks, assigneeId, decision |
| `publishing_schedules` | `_id`, targetType, targetId, revision, publishAt, state, leaseUntil |

Snapshot revision là immutable. `stories`/`chapters` chỉ giữ pointer và metadata nóng. Nội dung chapter không được nhúng vào story. HTML phải sanitize server-side; ưu tiên lưu structured JSON/plain text đã kiểm tra.

### Reading, community và analytics

| Collection | Field chính |
|---|---|
| `reading_progress` | userId, storyId, chapterId, position, deviceUpdatedAt, version |
| `reading_sessions` | sessionId, userId?, chapterId, startedAt, heartbeats, completedAt, verdict |
| `comments` | targetType, targetId, userId, parentId?, body, state, createdAt |
| `reports` | reporterId, targetType, targetId, reasonCode, state, risk, createdAt |
| `view_events` | bucket, eventId, user/session/device signals, chapterId, verdict |
| `story_stats_daily` | storyId, date, rawViews, validViews, invalidViews, donationsXu |
| `home_read_models` | segment, version, sections, generatedAt, expiresAt |

Heartbeat raw lớn không nhúng vô hạn vào session; lưu rolling summary hoặc time-bucket event collection. PII/risk signal được tối thiểu hóa và retention riêng.

### Media

`media_assets`: `_id`, ownerType`, ownerId, purpose, cloudinaryPublicId, cloudinaryVersion, resourceType, bytes, width, height, checksum, visibility, moderationState, createdAt`.

### Monetization

| Collection | Field chính |
|---|---|
| `wallet_accounts` | `_id`, ownerType, ownerId, currency=`XU`, status |
| `wallet_balances` | accountId, availableXu, reservedXu, version, updatedAt |
| `ledger_transactions` | `_id`, type, referenceType, referenceId, state, entries[], idempotencyKeyHash, createdAt |
| `topup_requests` | `_id`, userId, amountVnd, discountPercent, discountConfigVersion, creditedXu, transferReference, qrPayload, state, expiresAt |
| `payment_events` | provider, providerEventId, bankTransactionRef, amountVnd, transferReference, signatureState, matchedTopupId |
| `donations` | `_id`, donorAccountId, teamAccountId, amountXu, ledgerTransactionId, message, state |
| `withdrawals` | `_id`, teamId, accountId, grossAmountXu, feeXu, netAmountXu, destinationSnapshot, state, requestedBy |
| `monetization_configs` | key, version, value, effectiveAt, changedBy |

`ledger_transactions.entries` được embed vì có số lượng nhỏ và commit cùng vòng đời. Tổng debit luôn bằng tổng credit. Ledger transaction đã posted là immutable; sửa sai bằng compensating transaction.

## 3. Chính sách tiền/xu

### Topup

```text
creditedXu = floor(amountVnd × (100 - discountPercent) / 100)
```

- Default `discountPercent=10`; ví dụ 100.000 VND → 90.000 xu.
- Tỷ lệ và config version được snapshot khi tạo request; thay cấu hình không đổi request cũ.
- `transferReference` ngẫu nhiên, duy nhất, dễ đọc, không chứa user ID tuần tự.
- Automation chỉ credit khi amount + reference + provider transaction hợp lệ và request còn có thể chuyển trạng thái.
- Manual approval bắt buộc Admin MFA/re-auth, reason, evidence và unique bank transaction reference.
- Unique index trên provider transaction/bank reference và atomic compare-and-set state ngăn automation credit lại sau manual approval.

### Donation

- Chỉ chuyển xu hiện có: debit ví Reader và credit ví Team trong cùng MongoDB transaction.
- Không có QR, payment intent, webhook hoặc tài khoản nhận tiền riêng của Team.
- Idempotency key scope theo user + endpoint; không đủ số dư trả `422`.

### Withdrawal

```text
minimum = 100_000 xu
fee = grossAmountXu < 1_000_000 ? 20_000 : 0
netAmountXu = grossAmountXu - fee
```

Boundary bắt buộc test: `99.999` từ chối; `100.000` phí `20.000`; `999.999` phí `20.000`; `1.000.000` phí `0`. Khi tạo request, chuyển `grossAmountXu` từ available sang reserved trong cùng transaction. Approve/settle giải phóng reserve đúng một lần; reject/fail hoàn available bằng transaction bù.

## 4. Index theo access pattern

| Query | Index đề xuất |
|---|---|
| User login | `users {emailNormalized:1}` unique |
| Membership check | `team_memberships {teamId:1,userId:1}` unique |
| Team của user | `team_memberships {userId:1,state:1,teamId:1}` |
| Story public mới | `stories {status:1,publishedAt:-1,_id:-1}` |
| Story theo Team | `stories {teamId:1,status:1,updatedAt:-1,_id:-1}` |
| Chapter public | `chapters {storyId:1,status:1,number:1}` |
| Review queue | `moderation_reviews {state:1,createdAt:1,_id:1}` |
| Schedule worker | `publishing_schedules {state:1,publishAt:1,leaseUntil:1}` |
| Reading progress | `reading_progress {userId:1,storyId:1}` unique |
| Comment | `comments {targetType:1,targetId:1,state:1,createdAt:-1,_id:-1}` |
| Ledger reference | `ledger_transactions {referenceType:1,referenceId:1}` unique/partial |
| Idempotency | `ledger_transactions {idempotencyKeyHash:1}` unique/partial |
| Topup pending | `topup_requests {state:1,expiresAt:1}` |
| Transfer reference | `topup_requests {transferReference:1}` unique |
| Payment dedupe | `payment_events {provider:1,providerEventId:1}` unique |
| Withdrawal queue | `withdrawals {state:1,createdAt:1,_id:1}` |
| Outbox claim | `outbox_messages {status:1,nextAttemptAt:1,leaseUntil:1}` |

Chỉ tạo index đã có query owner và evidence. Review `explain("executionStats")`, `totalDocsExamined/totalDocsReturned`, sort-in-memory, index size và write amplification trước release.

## 5. Pagination và projection

- Dùng keyset cursor, ví dụ sort `{publishedAt:-1,_id:-1}` và điều kiện trang sau theo tuple.
- Cursor base64url, versioned và ký HMAC; client không gửi raw sort/operator.
- Không dùng `$skip` sâu cho catalog, comments, reviews, ledger, topup hoặc withdrawal.
- List chỉ projection field cần thiết; không đọc `chapter_revisions.content` trong list.
- Page size mặc định 20, tối đa 100; hard timeout/maxTimeMS cho search/admin export.

## 6. Chống query chậm và NoSQL injection

- Controller map input vào DTO allowlist; không nhận `Document`, `$where`, `$expr`, regex hoặc pipeline từ client.
- Sort/filter map từ enum server-side sang field cố định.
- Không dùng unanchored `$regex`; autocomplete qua Atlas Search.
- Tránh `$lookup` trên hot path; denormalize Team name/cover/status vào read model có version.
- Không để query thiếu tenant/team predicate ở Team dashboard.
- Có query budget, circuit breaker và concurrency limit cho export/report.

## 7. Transaction và concurrency

- MongoDB phải chạy replica set/sharded cluster hỗ trợ transaction.
- Dùng transaction cho: donation, reserve/settle withdrawal, credit topup, reward posting và ownership transfer.
- Optimistic concurrency bằng `version`; update filter gồm `{_id, version}` và `$inc:{version:1}`.
- State transition dùng filter state hiện tại; `matchedCount=0` trả conflict/idempotent result theo contract.
- Retry transient transaction error với giới hạn; cùng idempotency key luôn trả cùng kết quả nghiệp vụ.
- Không gọi Cloudinary, bank provider, email hoặc webhook outbound trong transaction.

## 8. Outbox, TTL và retention

- Trong cùng transaction, ghi aggregate + `outbox_messages`.
- Worker claim atomic bằng lease; consumer dedupe qua `inbox_messages`.
- TTL index cho session hết hạn, invitation, idempotency response và dữ liệu tạm; TTL không dùng để xóa ledger/audit.
- Ledger, topup, donation, withdrawal và audit giữ theo chính sách pháp lý/kế toán.
- View/risk raw dùng time-bucket + lifecycle archive/delete; aggregate giữ lâu hơn.

## 9. Sharding

Chỉ shard khi index/caching/read model/scale tier đã tối ưu và benchmark cho thấy cần. Không chọn khóa tăng đơn điệu như `createdAt` đơn lẻ cho write-heavy collection. Candidate phải được thử với phân bố thật:

- event time-bucket: hashed subject/session bucket + time bucket;
- ledger: hashed account/reference nếu query/reconciliation vẫn định tuyến được;
- stories/chapters: thường chưa cần shard ở MVP/Growth.

Mỗi kế hoạch shard phải chứng minh cardinality, frequency, monotonicity, reshard/backup/restore và ảnh hưởng transaction.

