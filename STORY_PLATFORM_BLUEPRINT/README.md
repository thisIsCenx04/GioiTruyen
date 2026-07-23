# Story Platform Blueprint

Phiên bản: 1.1  
Ngày lập: 23/07/2026  
Trạng thái: Architecture baseline để review trước khi triển khai

## Thay đổi chính ở phiên bản 1.1

- Bỏ Creator độc lập; chỉ Team Member/Owner có permission mới được xuất bản.
- Admin kế thừa quyền Moderator, Finance Operator và Support; loại bỏ hai role sau khỏi hệ thống.
- Topup sinh QR/reference, mặc định chiết khấu 10%, có automation và Admin manual fallback.
- Donation chỉ dùng xu nội bộ; cấm QR/external payment.
- Withdrawal tối thiểu 100.000 xu; dưới 1.000.000 phí 20.000 xu, từ 1.000.000 miễn phí.
- Chuyển stack sang Java Spring Boot + Next.js + MongoDB + Redis + Cloudflare + Cloudinary.
- Bổ sung đặc tả riêng cho luồng [Xuất bản truyện](11_PUBLISHING_WORKFLOW.md).

## Mục tiêu

Thư mục này là bộ hồ sơ phân tích và kế hoạch kỹ thuật cho một nền tảng đọc, đăng và khai thác truyện tương tự MonkeyD nhưng được thiết kế API-first, ưu tiên:

- phản hồi nhanh ở các luồng đọc, tìm kiếm và bảng xếp hạng;
- mở rộng ngang khi traffic tăng;
- chống gian lận lượt đọc và giao dịch xu;
- bảo vệ tài khoản, nội dung và dữ liệu cá nhân;
- dễ vận hành, quan sát, kiểm thử và khôi phục;
- không phải tách microservice quá sớm.

## Giả định quy mô tham chiếu

Các con số là mục tiêu thiết kế ban đầu, phải được xác nhận bằng load test trước production:

| Giai đoạn | MAU | Đồng thời | Origin RPS đỉnh | Nội dung |
|---|---:|---:|---:|---:|
| MVP | 100.000 | 2.000 | 300 | 100.000 truyện/chương |
| Growth | 1.000.000 | 20.000 | 2.000 | 5.000.000 chương |
| Burst qua CDN | - | - | 5.000+ | Chủ yếu GET công khai |

SLO khởi điểm:

- Availability API: 99,9%/tháng.
- API đọc đã cache: p95 dưới 150 ms tại origin.
- API đọc chưa cache: p95 dưới 300 ms.
- API ghi: p95 dưới 500 ms, trừ tác vụ bất đồng bộ.
- Search: p95 dưới 500 ms.
- Error rate 5xx dưới 0,1%.
- RPO 15 phút, RTO 60 phút ở production giai đoạn đầu.

## Cách đọc tài liệu

1. [01_PRODUCT_ANALYSIS.md](01_PRODUCT_ANALYSIS.md): phạm vi, vai trò, module và quy tắc nghiệp vụ.
2. [02_SYSTEM_ARCHITECTURE.md](02_SYSTEM_ARCHITECTURE.md): kiến trúc ứng dụng và ranh giới domain.
3. [03_INFRASTRUCTURE.md](03_INFRASTRUCTURE.md): topology, môi trường, CI/CD và mở rộng.
4. [04_DATA_MODEL_AND_QUERY.md](04_DATA_MODEL_AND_QUERY.md): mô hình dữ liệu, index và quy tắc query nhanh.
5. [05_API_CONVENTIONS_AND_CATALOG.md](05_API_CONVENTIONS_AND_CATALOG.md): chuẩn API và danh mục endpoint.
6. [06_SECURITY_BASELINE.md](06_SECURITY_BASELINE.md): security baseline và threat controls.
7. [07_PERFORMANCE_AND_SCALABILITY.md](07_PERFORMANCE_AND_SCALABILITY.md): cache, search, view pipeline và load test.
8. [08_OPERATIONS_AND_RECOVERY.md](08_OPERATIONS_AND_RECOVERY.md): logging, metrics, alert, backup và DR.
9. [09_IMPLEMENTATION_PLAN.md](09_IMPLEMENTATION_PLAN.md): kế hoạch thực hiện theo phase và Definition of Done.
10. [10_TEST_AND_RELEASE_GATE.md](10_TEST_AND_RELEASE_GATE.md): chiến lược test và điều kiện phát hành.
11. [11_PUBLISHING_WORKFLOW.md](11_PUBLISHING_WORKFLOW.md): luồng xuất bản truyện chi tiết, state machine và API.
12. [12_DETAILED_COMMIT_IMPLEMENTATION_PLAN.md](12_DETAILED_COMMIT_IMPLEMENTATION_PLAN.md): kế hoạch triển khai chi tiết theo từng merge commit.
13. [13_ENGINEERING_GIT_SECURITY_RULES.md](13_ENGINEERING_GIT_SECURITY_RULES.md): branching, commit/PR, tác phong và security rules.
14. [14_UNIT_TEST_PLAN.md](14_UNIT_TEST_PLAN.md): chiến lược, ma trận và test case unit test cho toàn hệ thống.
15. [api/story-platform.v1.yaml](api/story-platform.v1.yaml): OpenAPI contract khởi đầu.
16. [WORK_ITEMS.csv](WORK_ITEMS.csv): backlog có thể import vào công cụ quản lý công việc.
17. [COMMIT_PLAN.csv](COMMIT_PLAN.csv): kế hoạch commit dạng CSV để import/theo dõi.

## Kiến trúc được chọn

**Modular monolith theo Clean Layered Architecture + worker bất đồng bộ** là lựa chọn mặc định:

- Backend Java Spring Boot là một API deployable, domain tách module và dependency đi từ presentation → application → domain.
- Một hoặc nhiều worker xử lý indexing, notification, media, ranking, view validation và settlement.
- MongoDB là nguồn dữ liệu chuẩn; Redis chỉ là cache/coordination, không phải source of truth.
- MongoDB transaction + outbox collection bảo đảm thay đổi nghiệp vụ và sự kiện được ghi nguyên tử.
- Cloudflare đứng trước Next.js/API để cung cấp CDN, WAF, bot management và DDoS protection.
- Cloudinary lưu và phân phối media bằng signed upload, authenticated delivery và transformation.
- Search bắt đầu bằng MongoDB Atlas Search; có thể dùng text index giới hạn cho môi trường MVP không có Atlas Search.
- Chỉ tách service khi một module có nhu cầu scale, bảo mật hoặc vòng đời triển khai khác biệt rõ rệt.

## Stack khuyến nghị

| Lớp | Khuyến nghị | Ghi chú |
|---|---|---|
| Web | Next.js stable, SSR/ISR | SEO và cache trang truyện/chương |
| Mobile | Flutter hiện có | Chuyển repository local sang API client theo phase |
| API | Java Spring Boot, Spring Security | Clean Layered Architecture, REST API-first |
| Database | MongoDB managed replica set | Transaction cho ledger, index theo access pattern, PITR |
| Cache | Redis managed compatible | Cache, rate limit, session/revocation và lock ngắn hạn |
| Async | MongoDB outbox + worker | Có thể thêm broker khi throughput đo được yêu cầu |
| Media | Cloudinary | Signed upload, private/authenticated asset, transform và webhook |
| Search | MongoDB Atlas Search | Text index giới hạn chỉ dùng làm fallback MVP |
| Edge | Cloudflare | CDN, WAF, TLS, rate limiting, bot/DDoS protection |
| Observability | OpenTelemetry + metrics/log/trace backend | Không khóa vào một vendor |
| IaC | Terraform/OpenTofu | Dev/staging/prod cùng module, khác biến cấu hình |

Không khóa cứng phiên bản minor trong blueprint. Khi triển khai phải dùng phiên bản còn hỗ trợ, pin image bằng digest và có lịch nâng cấp.

## Nguyên tắc bắt buộc

- Không tin dữ liệu, role, số tiền hoặc trạng thái do client gửi.
- Không tăng trực tiếp `story.view_count` từ request đọc chương.
- Không sửa/xóa giao dịch ví đã ghi sổ; chỉ tạo bút toán bù.
- Không dùng offset pagination cho bảng lớn hoặc feed nóng.
- Không để API trả entity database trực tiếp.
- Không giữ access token web trong `localStorage`.
- Không gọi dịch vụ ngoài trong transaction database.
- Mọi API ghi có ảnh hưởng tài chính phải hỗ trợ idempotency và audit log.
- Mọi thay đổi document schema/index phải có migration tương thích ngược hoặc kế hoạch forward-fix được kiểm thử.
- Không có Creator độc lập: chỉ Team Member có permission xuất bản hoặc Team Owner mới được tạo/đăng truyện.
- Donation chỉ dùng xu trong ví; không tạo QR hay nhận thanh toán ngoài hệ thống.

## Chuẩn tham chiếu

- [OWASP ASVS 5.0.0](https://github.com/OWASP/ASVS/tree/v5.0.0_release)
- [OWASP API Security Top 10 2023](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)
- [NIST SP 800-63B-4](https://pages.nist.gov/800-63-4/sp800-63b.html)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [MongoDB - Indexes](https://www.mongodb.com/docs/manual/indexes/)
- [MongoDB - Transactions](https://www.mongodb.com/docs/manual/core/transactions/)
- [Spring Boot Reference](https://docs.spring.io/spring-boot/index.html)
- [Cloudinary Upload API](https://cloudinary.com/documentation/image_upload_api_reference)
- [Cloudflare WAF](https://developers.cloudflare.com/waf/)
