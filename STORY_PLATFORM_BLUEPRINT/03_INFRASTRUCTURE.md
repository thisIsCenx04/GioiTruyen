# 03. Hạ tầng hệ thống

## 1. Mục tiêu

- Public read đi qua edge cache để giảm tải origin.
- Spring Boot API và worker stateless, có thể scale ngang.
- MongoDB là source of truth; Redis có thể xóa và dựng lại.
- Cloudinary quản lý media; Cloudflare bảo vệ và cache web/API.
- Dev, staging và production tách biệt tài khoản, secret, database và media folder.

## 2. Topology production

```text
Internet
  → Cloudflare DNS/TLS/CDN/WAF/Bot/Rate Limit
    ├─ Next.js web/admin
    ├─ Spring Boot API load balancer → API instances
    │                              ├─ MongoDB managed replica set
    │                              ├─ Redis managed
    │                              └─ Cloudinary API
    └─ cache public GET/SSR/ISR

Scheduler/worker instances
  ├─ outbox + retry/dead-letter
  ├─ ranking/view validation
  ├─ notification
  ├─ Cloudinary webhook processing
  └─ topup reconciliation/reward/withdrawal

Observability
  → OpenTelemetry collector → log/metric/trace backend + alerting
```

Không public MongoDB hoặc Redis. Chỉ API/worker trong private network được truy cập bằng identity/secret riêng. Cloudflare Origin Certificate hoặc mTLS bảo vệ đường edge → origin; origin từ chối kết nối bypass Cloudflare nếu topology cho phép.

## 3. Môi trường

| Môi trường | Dữ liệu | Mục đích | Quy tắc |
|---|---|---|---|
| Local | giả lập | phát triển | Docker Compose, không dùng secret thật |
| Dev | synthetic | tích hợp liên tục | tự động deploy nhánh chính |
| Staging | synthetic/anonymized | UAT, load/security test | topology gần production |
| Production | thật | người dùng | least privilege, MFA, audit, backup/PITR |

Không sao chép raw production data về môi trường thấp. Cloudinary dùng cloud/folder và upload preset tách riêng theo môi trường.

## 4. Thành phần hạ tầng

### 4.1. Next.js

- SSR/ISR cho trang catalog, truyện và chapter public; route cá nhân hóa không cache public.
- Artifact immutable; cấu hình runtime qua secret manager.
- Security headers và CSP được kiểm thử ở edge và ứng dụng.
- Admin app có hostname riêng, SSO/MFA và hạn chế truy cập nếu phù hợp.

### 4.2. Spring Boot API

- Java container non-root, read-only filesystem, resource limit và health endpoint riêng.
- Readiness chỉ pass khi dependency bắt buộc sẵn sàng; liveness không phụ thuộc dịch vụ ngoài.
- Graceful shutdown ngừng nhận request mới, hoàn tất request đang chạy và nhả outbox lease.
- Connection pool, request timeout, max body và concurrency limit có cấu hình.

### 4.3. MongoDB

- Managed replica set tối thiểu 3 voting members, TLS, encryption at rest, private endpoint/IP allowlist.
- Bật point-in-time recovery; backup phải được restore-test định kỳ.
- Transaction tài chính yêu cầu `writeConcern: majority`, `readConcern: snapshot` và retry transaction đúng chuẩn driver.
- Index được quản lý bằng migration job versioned; build index theo chiến lược tránh ảnh hưởng production.
- Slow query profiler có sampling; alert replication lag, connection saturation, cache pressure và transaction abort.
- Sharding chỉ triển khai sau benchmark/capacity review; shard key phải dựa trên access pattern và chống hotspot.

### 4.4. Redis

- Managed Redis, TLS/auth, private network, eviction policy theo loại workload.
- Tách prefix hoặc instance cho cache, session/revocation, rate limit và job coordination.
- Không lưu số dư ví, trạng thái topup/withdrawal hoặc permission chuẩn chỉ trong Redis.
- Lock luôn có TTL, fencing/version và không thay thế MongoDB transaction.

### 4.5. Cloudinary

- API secret chỉ ở backend/secret manager; client chỉ nhận signed upload params thời hạn ngắn.
- Upload preset restricted: folder, format, kích thước, transformation và moderation policy.
- Webhook bắt buộc xác minh chữ ký/timestamp, chống replay và deduplicate notification ID.
- Media nhạy cảm dùng `private`/`authenticated` delivery; không lưu URL có chữ ký dài hạn.
- Lưu `publicId`, `version`, `resourceType`, hash, owner và moderation state trong MongoDB.

### 4.6. Cloudflare

- Managed rules + custom WAF rules cho auth, search, comment, upload signature, topup và admin.
- Rate limit phân tầng theo IP, account, session và endpoint; challenge bot thay vì chỉ block cứng.
- Chỉ cache GET public không có user-specific data; tôn trọng `Cache-Control` và `Vary`.
- Không cache response có cookie/token, wallet, Team dashboard hoặc admin.
- Log edge phải gửi về nơi lưu tập trung để điều tra sự cố.

## 5. CI/CD

```text
pull request
  → format/lint/unit/architecture tests
  → SAST + dependency/secret/container scan
  → integration test với MongoDB replica set + Redis
  → OpenAPI lint/contract test
  → build image + SBOM + ký artifact
  → deploy staging
  → smoke/security/load gate
  → approval → canary/rolling production
```

- Image pin bằng digest, không dùng `latest`.
- MongoDB migration phải backward-compatible trong cửa sổ rolling deploy.
- Feature flag cho topup automation, donation, withdrawal, publishing schedule và worker.
- Rollback code không được rollback ledger; dùng forward-fix/compensating entry.

## 6. Secret và quyền truy cập

- Secret lưu trong managed secret manager, rotate theo lịch và sau incident.
- Service identity tách cho web, API, worker, CI và backup.
- MongoDB role theo database/collection cần thiết; CI migration dùng identity riêng có thời hạn.
- Cloudinary API key/secret, webhook secret và bank reconciliation credential không đưa vào source/log.
- Production access theo just-in-time, MFA, ticket/reason và audit.

## 7. Autoscaling và capacity

- API scale theo concurrent request, CPU, p95 latency và queue/connection saturation.
- Worker scale theo outbox lag, oldest message age và processing latency.
- MongoDB scale dọc/index/read model trước; chỉ shard khi working set hoặc write throughput thực sự vượt ngưỡng.
- Redis scale theo memory, eviction, hit ratio và command latency.
- Cloudflare cache hit ratio là capacity metric bắt buộc cho public read.

Không scale API đến mức vượt MongoDB connection budget. Mỗi replica có pool cap; deployment controller tính `replicas × maxPoolSize + worker pools + headroom`.

## 8. Baseline production MVP

- Cloudflare trước mọi public hostname.
- Next.js web/admin tách deployment.
- Spring Boot API tối thiểu 2 replica, worker tối thiểu 1 replica có lease.
- MongoDB managed replica set + PITR.
- Redis managed có TLS.
- Cloudinary signed upload/private delivery.
- OpenTelemetry, centralized log/metric/trace và alert on-call.
- Terraform/OpenTofu quản lý DNS, network, runtime, database policy và secret reference.

## 9. Điều kiện nâng cấp

| Tín hiệu | Hành động ưu tiên |
|---|---|
| Public read origin tăng | tăng Cloudflare/ISR cache, tối ưu projection/read model |
| MongoDB examined/returned cao | sửa query/index trước khi tăng tài nguyên |
| Working set vượt RAM ổn định | archive/cold tier, scale tier, đánh giá shard |
| Outbox lag vượt SLO | scale worker, tách queue/broker nếu cần |
| Media latency/cost tăng | tối ưu Cloudinary variants, format và cache |
| Blast radius module lớn | đánh giá tách worker/service bằng contract hiện có |

