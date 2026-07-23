# 02. Kiến trúc hệ thống

## 1. Sơ đồ tổng thể

```text
Next.js SSR/ISR ─┐
Flutter App ─────┼─> Cloudflare CDN/WAF ─> Spring Boot API ─┬─ MongoDB replica set + PITR
Admin Web ───────┘          │                modular         ├─ Redis
                            │                monolith        ├─ MongoDB outbox
                            │                                └─ Cloudinary
                            └─ cached public responses               │
                                                               Background workers
                                                               ├─ Search/read-model
                                                               ├─ Media webhook/validation
                                                               ├─ Notification
                                                               ├─ Ranking/view-fraud
                                                               └─ Topup/reward/withdrawal
```

## 2. Lý do chọn modular monolith

- Transaction nhất quán cho workflow xuất bản và ledger dễ hơn distributed transaction.
- Ít deployment, ít network hop, chi phí vận hành thấp.
- Vẫn tách code, collection ownership và contract theo module.
- Worker scale độc lập cho workload CPU/I/O nặng.
- Có thể tách Search, Analytics, Notification hoặc Monetization về sau từ outbox contract.

Không được cho phép module truy cập bảng riêng của module khác tùy tiện. Giao tiếp đồng bộ qua application contract; giao tiếp bất đồng bộ qua integration event có version.

## 3. Cấu trúc backend đề xuất

```text
backend/
  src/main/java/com/storyplatform/
    bootstrap/
    shared/{security,persistence,observability,web}
    identity/
      domain/
      application/{port,usecase,dto}
      infrastructure/{mongo,redis,integration}
      presentation/rest/
    teams/ catalog/ publishing/ reading/ discovery/
    community/ moderation/ monetization/ analytics/ media/ notifications/
    workers/{outbox,analytics,media}
  src/test/{unit,integration,architecture,contract}
frontend/
  apps/{web,admin}/                 # Next.js
  packages/{api-client,ui,config}/
deploy/{docker,terraform,kubernetes-or-app-platform}/
```

## 4. Quy tắc dependency

```text
Presentation → Application → Domain
Infrastructure → Application + Domain
Contracts → không phụ thuộc Infrastructure
Module A → chỉ dùng Contracts của Module B
```

- Domain không tham chiếu Spring, HTTP, MongoDB, Redis hay message broker.
- Controller/endpoint chỉ map request, authorize, gọi use case, map response.
- Validation nghiệp vụ nằm ở application/domain, không chỉ ở UI.
- Query trả read model tối ưu, không tải aggregate lớn khi không cần.
- Architecture tests chặn dependency sai trong CI.

## 5. Đồng bộ và bất đồng bộ

### Đồng bộ

Dùng cho thao tác cần phản hồi ngay: login, lấy truyện, lấy chương, lưu tiến độ, tạo draft.

### Bất đồng bộ

Dùng cho:

- cập nhật search index;
- resize/scan media;
- gửi email/push;
- aggregate view/ranking;
- fraud scoring;
- reward settlement;
- analytics export;
- cache invalidation diện rộng.

Sự kiện phải có `eventId`, `eventType`, `version`, `occurredAt`, `correlationId`, `tenant/actor` nếu áp dụng và payload tối thiểu. Consumer lưu inbox/dedup key để xử lý ít nhất một lần mà không tạo tác dụng phụ trùng.

## 6. Transactional outbox

Trong cùng transaction với thay đổi nghiệp vụ:

1. cập nhật aggregate;
2. ghi bản ghi `outbox_messages`;
3. commit;
4. worker claim bằng atomic `findOneAndUpdate` với lease/lock expiry;
5. xử lý trực tiếp hoặc publish broker khi đã bổ sung broker;
6. đánh dấu processed; lỗi thì retry có backoff và chuyển dead-letter collection.

Không gọi broker, email, search hoặc payment provider trong transaction database.

## 7. Read/write path

### Public read

```text
Client → Cloudflare cache → Next.js/API output cache → Redis → MongoDB read model
```

Các trang truyện/chương đã publish có cache key theo `storyId:version` hoặc `chapterId:revision`. Khi publish, thay version thay vì quét xóa wildcard cache.

### Write

```text
Client → Cloudflare WAF/rate limit → authn/authz → validation
→ MongoDB transaction (replica set) → outbox collection → response
```

Không read-after-write từ secondary nếu người dùng cần thấy ngay thay đổi vừa ghi. API trả representation mới hoặc dùng `readPreference=primary` trong consistency window.

## 8. Search architecture

### MVP/Growth

- MongoDB Atlas Search index cho title, aliases, Team, synopsis và autocomplete.
- Facet từ field chuẩn hóa (`status`, `categories`, `language`, `rating`), chỉ index field có access pattern.
- Search query có `maxTimeMS`, page size cứng và cursor/search-after; không dùng `$skip` sâu.
- Nếu môi trường chưa có Atlas Search, dùng MongoDB text index có giới hạn và feature flag; không dùng `$regex` không neo cho search công khai.
- MongoDB collection chuẩn vẫn là source of truth; search index/read model cập nhật qua outbox, hỗ trợ rebuild theo version.

## 9. Media pipeline

```text
POST upload signature → server xác thực ownership/type/size
→ client signed upload vào Cloudinary restricted folder
→ verify webhook signature + resource type/magic bytes
→ moderation/scan + decode/re-encode + strip metadata
→ approve private/authenticated asset + generate variants
→ lưu `publicId`, `version`, metadata → Cloudinary CDN
```

- Không tin `Content-Type` hoặc extension từ client.
- SVG chỉ cho phép sau sanitize nghiêm ngặt; mặc định từ chối với upload người dùng.
- Asset private dùng Cloudinary authenticated URL ngắn hạn.
- Không proxy arbitrary URL do người dùng nhập để tránh SSRF.

## 10. Tính nhất quán dữ liệu

| Dữ liệu | Mức nhất quán |
|---|---|
| Tài khoản, quyền | Strong/primary |
| Trạng thái publish | Strong/primary |
| Wallet ledger, topup, donation, withdrawal | Strong + MongoDB multi-document transaction/optimistic version |
| Search index | Eventual, mục tiêu dưới 30 giây |
| Ranking | Eventual, 1–5 phút |
| Notification | Eventual |
| View counter hiển thị | Eventual |
| Reading progress | Last-write-wins có version/device timestamp |

## 11. Điều kiện tách service

Chỉ tách khi ít nhất một điều đúng:

- workload cần scale khác biệt trên 3 chu kỳ capacity review;
- blast radius hoặc yêu cầu compliance cần cô lập;
- deploy frequency/team ownership độc lập rõ ràng;
- database contention không giải quyết hợp lý bằng schema/query/cache;
- có contract và observability đủ để vận hành phân tán.

Ưu tiên tách theo thứ tự: Media/Notification → Search → Analytics/View → Monetization. Identity chỉ tách khi có năng lực vận hành security riêng. Mọi service tách ra phải coi MongoDB collection của module khác là private, không truy cập chéo trực tiếp.
