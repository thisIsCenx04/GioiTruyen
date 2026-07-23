# 07. Hiệu năng và khả năng chịu tải

## 1. SLO khởi điểm

| Luồng | p95 origin | Ghi chú |
|---|---:|---|
| Public GET cache hit | < 150 ms | đo tại origin/edge riêng |
| Public GET cache miss | < 300 ms | projection/read model |
| Search | < 500 ms | Atlas Search, hard timeout |
| Team draft save | < 500 ms | optimistic concurrency |
| Wallet command | < 700 ms | transaction + idempotency |
| Async publish propagation | < 30 giây | search/cache/notification |

Error 5xx dưới 0,1%; các SLO phải được xác nhận bằng load test trên staging gần production.

## 2. Cache

| Dữ liệu | Vị trí | TTL/invalidation |
|---|---|---|
| Home/catalog public | Cloudflare + Redis | 30–120 giây, stale-while-revalidate |
| Story/chapter published | Cloudflare | key theo revision/version; purge/tag khi publish |
| Taxonomy | Edge/Redis | 15–60 phút |
| Team permission | Redis | ngắn, gắn membershipVersion |
| Wallet/topup/withdrawal | không public cache | primary read/short private cache nếu chứng minh an toàn |

- Cache key gồm API version, locale, normalized query và representation version.
- Không cache response có `Authorization`, session cookie hoặc dữ liệu cá nhân ở shared cache.
- Chống cache stampede bằng request coalescing/soft TTL/jitter.
- Redis miss phải luôn có đường đọc đúng từ MongoDB.

## 3. MongoDB query performance

- Projection tối thiểu; list không đọc chapter content/revision lớn.
- Compound index theo equality → sort → range và access pattern đã ghi ở tài liệu dữ liệu.
- Keyset pagination; cấm `$skip` sâu.
- Atlas Search cho full-text/autocomplete; cấm unanchored regex public.
- `maxTimeMS`, page cap và concurrency limit cho search/export.
- Dashboard/home/ranking dùng denormalized read model cập nhật qua outbox.
- Query nóng có `explain("executionStats")`; theo dõi examined/returned, in-memory sort và working set.
- Secondary read chỉ cho eventual read; read-after-write, permission và tiền dùng primary.

## 4. Write và transaction

- Giữ transaction ngắn; không gọi Cloudinary/provider/email trong transaction.
- Wallet account phân tán theo Team/user; optimistic version và idempotency giảm contention.
- Topup automation/manual state transition atomic; payment event unique.
- Donation ghi debit/credit một lần trong transaction.
- Withdrawal reserve gross amount khi tạo; settle/release bằng state transition một lần.
- Outbox worker dùng batch nhỏ, lease và backpressure; retry có jitter/DLQ.

## 5. Publishing propagation

```text
MongoDB commit story/chapter revision + outbox
  → response thành công
  → worker cập nhật search/read model
  → đổi cache version/purge Cloudflare tag
  → notification fan-out
```

User-facing publish không chờ notification/search. API trả propagation status/correlation ID; dashboard có thể hiển thị `published` và `indexing` riêng.

## 6. View/ranking pipeline

- Reading request không `$inc` trực tiếp counter hot.
- Event được batch/time-bucket; worker validate và aggregate theo giờ/ngày.
- Dedupe dùng event/session ID; Redis hỗ trợ window nhưng MongoDB giữ verdict/aggregate chuẩn.
- Ranking precompute top N theo period/segment; API không group/sort raw event.
- Backpressure ưu tiên không làm ảnh hưởng public reading.

## 7. Cloudflare và Cloudinary

- Next.js ISR + Cloudflare cache giảm origin RPS; Brotli/HTTP2/3 theo edge support.
- Asset Cloudinary dùng immutable version URL, responsive breakpoints, WebP/AVIF và kích thước đúng viewport.
- Không proxy media byte qua Spring Boot.
- Private asset dùng signed/authenticated delivery; public cover/avatar cache dài theo version.

## 8. Load profile

Traffic mix tham chiếu:

| Nhóm | Tỷ trọng |
|---|---:|
| Home/catalog/story/chapter GET | 70% |
| Reading session/progress | 15% |
| Search/ranking | 8% |
| Community | 5% |
| Team/Admin/Monetization | 2% |

Kịch bản bắt buộc:

- ramp bình thường 30 phút;
- spike 5–10 lần khi chapter mới;
- soak 8–24 giờ;
- cache cold và cache stampede;
- MongoDB primary failover/replication lag;
- Redis unavailable/eviction;
- outbox backlog;
- đồng thời topup webhook + manual approval;
- concurrent donation/withdrawal trên cùng ví.

## 9. Capacity guardrail

- API replica scale không vượt MongoDB connection budget.
- Circuit breaker/bulkhead cho Cloudinary, email và payment reconciliation provider.
- Admission control trả 429/503 có `Retry-After` trước khi làm cạn pool.
- Worker concurrency cấu hình theo dependency budget, không chỉ CPU.
- Queue/outbox lag có kill switch cho workload không thiết yếu.

## 10. Khi nào shard/tách service

Chỉ quyết định bằng telemetry và benchmark. Trước khi shard:

1. sửa query/index/projection;
2. tăng edge/Redis cache và read model;
3. archive dữ liệu lạnh;
4. scale MongoDB tier;
5. kiểm chứng shard key không hotspot và transaction/recovery vẫn đạt SLO.

Tách Search, Analytics/View hoặc Monetization khi có ownership, contract, observability và nhu cầu scale/blast-radius rõ ràng; không tách chỉ theo tên module.

