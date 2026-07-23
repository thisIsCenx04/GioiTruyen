# 08. Vận hành, quan sát và khôi phục

## 1. Observability

Mọi request/job/event có `traceId`, `correlationId`, actor/Team đã pseudonymize và deployment version. Dùng OpenTelemetry cho trace, metric và structured log.

### Metric bắt buộc

- RED: request rate, error, duration theo route/status.
- JVM: heap, GC pause, thread, CPU, file descriptor.
- MongoDB: connection, operation latency, examined/returned, lock/transaction abort, replication lag, oplog window.
- Redis: latency, hit ratio, memory, eviction, rejected connection.
- Outbox: backlog, oldest age, throughput, retry, dead-letter.
- Cloudflare: cache hit, WAF action, origin error/bypass attempt.
- Cloudinary: upload failure, moderation pending, webhook lag/signature failure.
- Business: publish lead time, topup match/manual ratio, double-credit conflict, donation posted/reversed, withdrawal aging/failure, ledger invariant.

Không đưa token, password, QR payload đầy đủ, bank account, transfer secret, chapter private content hoặc raw PII vào log.

## 2. Alert

| Mức | Ví dụ | Phản ứng |
|---|---|---|
| P1 | API unavailable, ledger imbalance, nghi double-credit/data leak | page ngay, freeze monetization nếu cần |
| P2 | p95/SLO kéo dài, Mongo lag, outbox age, topup mismatch tăng | on-call xử lý trong SLA |
| P3 | cache hit giảm, dead-letter nhỏ, disk/cost trend | ticket/capacity review |

Alert phải actionable, có owner, runbook, dashboard và tránh cảnh báo theo một spike ngắn.

## 3. Backup và DR

- MongoDB managed snapshot + PITR; backup tách project/account khi khả thi.
- IaC, OpenAPI, config schema và migration version trong Git.
- Redis không coi là backup; phải rebuild được.
- Cloudinary asset dùng version/public ID; bật backup/versioning theo gói và lưu metadata mapping trong MongoDB.
- Secret/key có escrow/rotation procedure, không đưa secret vào backup ứng dụng.
- Ledger/audit có append protection và export kiểm toán theo retention policy.

| Thành phần | RPO | RTO khởi điểm |
|---|---:|---:|
| MongoDB production | ≤ 15 phút | ≤ 60 phút |
| Redis | có thể mất cache | ≤ 30 phút |
| Next.js/Spring Boot | artifact immutable | ≤ 30 phút |
| Cloudinary public media | theo provider/SLA | ≤ 4 giờ |

Restore drill hàng quý: dựng isolated environment, restore snapshot/PITR, chạy integrity checks, smoke test và ghi thời gian thực.

## 4. Integrity checks

Chạy định kỳ và sau restore:

- tổng debit = tổng credit mỗi ledger transaction;
- wallet projection = tổng ledger đã posted;
- topup `credited` có đúng một ledger reference và payment/manual evidence;
- bank/provider event chỉ map tối đa một topup;
- donation posted có đúng debit Reader + credit Team;
- withdrawal reserved/paid/rejected khớp ledger và fee rule;
- story/chapter published trỏ đến immutable revision tồn tại;
- Team permission không có owner cuối bị mất;
- outbox/inbox không mất event.

Sai lệch tạo incident/case; không tự sửa ledger bằng update trực tiếp.

## 5. Runbook bắt buộc

- Cloudflare/origin outage hoặc cache poisoning.
- Spring Boot latency/thread/connection exhaustion.
- MongoDB primary failover, replication lag, transaction abort, restore/PITR.
- Redis outage/eviction/rate-limit degraded mode.
- Cloudinary upload/webhook/moderation outage.
- Outbox backlog/dead-letter/replay.
- Publishing stuck ở review/schedule/indexing.
- Topup automation lỗi, chuyển manual mode và chống xử lý trùng.
- Ledger imbalance/donation double-spend/withdrawal stuck.
- Account takeover/Admin credential compromise.
- Data leak, secret rotation và user notification.

Mỗi runbook nêu detection, containment, safe command, rollback/forward-fix, validation, communication và evidence retention.

## 6. Monetization reconciliation

- Bank/provider event ↔ topup request ↔ ledger: liên tục và tổng hợp hàng ngày.
- Donation ↔ ledger debit/credit: kiểm tra invariant hàng ngày.
- Withdrawal provider ↔ state ↔ reserved/settled ledger: hàng ngày.
- Reward period ↔ valid view aggregate ↔ ledger: mỗi kỳ.
- Mismatch không tự credit/settle mù; chuyển `pending_review` và tạo audit/case.

Khi automation topup lỗi:

1. bật kill switch automation credit nhưng vẫn ingest/dedupe event;
2. xác định time window/provider;
3. Admin review từng request bằng bank transaction ref + amount + reference;
4. manual approval atomic với ledger/outbox;
5. replay automation ở dry-run, chứng minh không double-credit;
6. mở lại và post-incident review.

## 7. Deployment và rollback

- Canary/rolling; readiness, error rate, p95, MongoDB pool và business invariant là gate.
- Document/index migration theo expand → migrate/backfill → switch → contract.
- Rollback code không xóa field/index vừa được version mới dùng cho đến hết compatibility window.
- Monetization lỗi dùng feature flag/kill switch và compensating entry; không rollback data đã posted.
- Cloudflare rule/config thay đổi versioned và có rollback.

## 8. Data lifecycle

- Session/invitation/idempotency cache dùng TTL.
- Raw view/risk signal giữ tối thiểu theo fraud/privacy policy rồi aggregate/archive/delete.
- Ledger/topup/donation/withdrawal/audit giữ theo nghĩa vụ pháp lý/kế toán.
- Account deletion pseudonymize dữ liệu không còn cần thiết nhưng giữ legal record tối thiểu.
- Cloudinary asset orphan được đánh dấu, qua grace period và job xóa có audit.

## 9. Operational ownership

| Domain | Owner chính | Escalation |
|---|---|---|
| Identity/Security | Backend + Security | Incident commander |
| Publishing/Moderation | Product Ops + Admin | Legal khi copyright |
| MongoDB/Redis/Runtime | Platform | Provider support |
| Topup/Donation/Withdrawal | Monetization Ops (Admin role) | Finance/Legal/Provider |
| Media | Platform + Content Ops | Cloudinary |

Role ứng dụng `Admin` có thể gom moderation, support và finance ở quy mô nhỏ; quy trình vận hành vẫn phân actor, MFA, reason và audit để giảm insider risk.

