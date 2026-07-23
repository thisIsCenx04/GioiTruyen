# 10. Kiểm thử và release gate

## 1. Test pyramid

| Tầng | Phạm vi |
|---|---|
| Unit/property | domain rule, state machine, fee/discount, ledger invariant |
| Architecture | dependency Clean Layered, module/collection ownership |
| Integration | MongoDB replica set, Redis, outbox, Cloudinary/provider adapter |
| Contract | OpenAPI schema, backward compatibility, webhook signatures |
| E2E | Next.js/API theo hành trình người dùng/Admin |
| Load/soak | cache, query, worker, transaction contention |
| Security | SAST/DAST/fuzz/BOLA/BFLA/abuse/concurrency |
| Recovery | backup restore, failover, replay/reconciliation |

Không mock database cho test index/query/transaction. Integration dùng MongoDB replica set tương thích production và Redis thật qua container/service test.

## 2. Critical E2E

1. Register → verify → login → rotate refresh → revoke session.
2. User tạo Team → trở thành Owner → add member → cấp/revoke publishing permission.
3. Team Member tạo story/chapter → submit → Moderator hoặc Admin review → publish.
4. Changes requested → sửa revision mới → resubmit; revision cũ vẫn audit được.
5. Schedule publish → worker claim đúng một lần → cache/search/notification eventual.
6. Reader đọc → progress → view verdict/aggregate.
7. Comment/report → moderation → appeal.
8. Topup tạo QR/reference → automation match → nhận đúng xu sau discount.
9. Automation lỗi → Admin manual approve → event đến muộn không credit lại.
10. Donation XU-only → Reader debit, Team credit; không đủ xu bị từ chối.
11. Withdrawal reserve → Admin approve/reject → settle/release đúng fee rule.

## 3. Permission/BOLA matrix

Thay lần lượt `teamId`, `storyId`, `chapterId`, `membershipId`, `walletId`, `topupId`, `withdrawalId` bằng tài nguyên khác owner:

- Reader không tạo/sửa/submit/publish.
- Member Team A không đọc draft hoặc sửa Team B.
- Member không có `story:publish` không schedule/publish.
- Team Owner chỉ quản lý member Team mình.
- Moderator không sửa ledger/config tài chính.
- Admin có moderation + finance/support nhưng thao tác nhạy cảm thiếu MFA/re-auth/reason phải fail.
- User không đọc wallet/topup của người khác.

Kết quả trả 403/404 nhất quán theo anti-enumeration policy và không lộ metadata.

## 4. Publishing state/concurrency

- Transition không hợp lệ trả 409/422.
- `If-Match` cũ trả 412; không mất update.
- Submit cùng idempotency key chỉ tạo một review.
- Edit sau submit không thay revision đang review.
- Schedule worker nhiều replica chỉ publish một lần.
- Suspend/unpublish tranh chấp publish xử lý theo priority policy.
- Outbox replay không gửi duplicate notification có tác dụng phụ.

## 5. Topup

### Công thức

- discount 10%: `100.000 VND → 90.000 xu`.
- config version mới không đổi topup cũ.
- rounding dùng floor và integer, không floating point.

### Automation/manual race

- cùng provider event gửi 100 lần chỉ có một ledger transaction;
- hai event khác ID nhưng cùng bank transaction ref chỉ một event được chấp nhận;
- manual approve và automation chạy đồng thời chỉ một state transition thắng;
- event đến sau topup expired/pending review đi đúng policy, không tự credit sai;
- evidence/reason/ref thiếu hoặc Admin thiếu re-auth bị từ chối;
- amount/reference mismatch chuyển review, không credit.

## 6. Donation XU-only

- API schema không có provider, currency VND, QR hoặc callback URL.
- Available 100, donate 101 bị từ chối và balance không đổi.
- Concurrent donations tổng vượt balance: chỉ tập hợp transaction hợp lệ được commit.
- Retry cùng key trả donation cũ; cùng key/body khác trả 409.
- Tổng debit = tổng credit; reversal tạo bút toán bù.
- Team/content cố đăng QR thanh toán được policy/moderation xử lý theo yêu cầu.

## 7. Withdrawal

Boundary table:

| Gross xu | Kết quả | Fee | Net |
|---:|---|---:|---:|
| 99.999 | reject | - | - |
| 100.000 | accept | 20.000 | 80.000 |
| 999.999 | accept | 20.000 | 979.999 |
| 1.000.000 | accept | 0 | 1.000.000 |

- Concurrent request không reserve vượt available.
- Fee/net client gửi (nếu có) bị ignore/reject; server tính lại.
- Approve/reject/retry/provider callback không settle/release hai lần.
- Đổi destination trong cooling-off bị block.
- Admin không approve request do chính mình khởi tạo.

## 8. MongoDB/query

- `explain("executionStats")` cho hot query; không collection scan ngoài exception có owner.
- Keyset cursor không trùng/mất item khi insert xen kẽ theo contract.
- Không `$skip` sâu/unanchored regex/raw operator từ request.
- Large chapter/list projection không vượt response/memory budget.
- Transaction retry không tạo duplicate ledger/outbox.
- TTL không áp dụng nhầm ledger/audit.

## 9. Cloudflare/Cloudinary

- Shared cache không lưu wallet, Team draft, admin hoặc response có auth/cookie.
- Cache purge/version sau publish/unpublish; stale content không vượt policy.
- Origin reject bypass khi control được bật.
- Upload signature hết hạn/folder/type/size sai bị từ chối.
- Webhook Cloudinary signature/replay sai bị từ chối.
- HTML/SVG/executable/polyglot và metadata GPS test theo media policy.

## 10. Performance gate

- Ramping, spike, soak và cache-cold theo profile ở tài liệu hiệu năng.
- p95/p99, error, MongoDB connections/lag, Redis hit/eviction, outbox oldest age trong budget.
- Fail MongoDB primary, Redis, Cloudinary/provider; hệ thống degrade có kiểm soát.
- Topup/donation/withdrawal contention test không vi phạm invariant.

## 11. Security gate

- Không critical/high unresolved.
- Auth/session/CSRF/CORS/CSP/cookie/TLS tests pass.
- BOLA/BFLA, NoSQL injection, XSS, SSRF, upload abuse, mass assignment pass.
- Rate/automation abuse cho login/search/comment/report/topup/upload pass.
- Secret scan/SBOM/dependency/container/IaC policy pass.
- Audit đủ actor/reason/before-after cho hành động nhạy cảm.

## 12. Recovery/operations gate

- MongoDB PITR restore drill và integrity checks pass.
- Redis flush không làm mất dữ liệu chuẩn.
- Outbox/DLQ replay có kiểm soát.
- Topup automation kill switch + manual runbook diễn tập.
- Ledger mismatch freeze/reconcile runbook diễn tập.
- Cloudflare rule rollback và secret rotation diễn tập.

## 13. Release checklist

- OpenAPI lint/reference validation và breaking-change check pass.
- Document/index migration backward-compatible.
- Feature flag/kill switch/rollback owner rõ ràng.
- Dashboard/alert/runbook/on-call sẵn sàng.
- Product, QA, Security, Platform và Ops ký gate tương ứng.
- Monetization chỉ bật sau reconciliation sandbox/pilot và theo dõi manual.

