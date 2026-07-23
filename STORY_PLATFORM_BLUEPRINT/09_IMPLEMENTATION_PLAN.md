# 09. Kế hoạch triển khai

## 1. Nguyên tắc

- API/OpenAPI và business rule được chốt trước khi UI phụ thuộc.
- Làm vertical slice chạy được, không xây toàn bộ tầng rồi mới tích hợp.
- Security, observability, migration và test là một phần của feature.
- Không bật tiền thật trước khi ledger/idempotency/reconciliation/concurrency gate pass.
- Mọi estimate trong `WORK_ITEMS.csv` là story point tham chiếu, cần refinement.

## 2. Team đề xuất

- 1 Product Owner/Business Analyst.
- 1 Tech Lead/Architect.
- 2–4 Java Spring Boot engineers.
- 1–2 Next.js engineers.
- 1 QA automation.
- Platform/SRE và Security part-time từ đầu, tăng trước production.
- Content/Monetization Ops tham gia UAT workflow và runbook.

## 3. Phase 0 — Discovery và contract (1–2 tuần)

- Chốt taxonomy, content policy, publishing transition và Team permission matrix.
- Chốt 1 xu = 1 VND trong các rule hiện tại, topup discount 10%, min/phí withdrawal và KYC/tax.
- Threat model, data classification, retention và abuse cases.
- Review OpenAPI, state machine và error/idempotency contract.
- PoC MongoDB transaction replica set, Atlas Search, Cloudinary signed upload và Cloudflare cache/WAF.

Exit gate: không còn quyết định chưa rõ làm thay đổi auth, ledger hoặc workflow cơ bản.

## 4. Phase Base — Platform, test foundation và hạ tầng (2–4 tuần)

- Scaffold Spring Boot Clean Layered modular monolith và Next.js web/admin.
- Dựng Java unit-test stack (JUnit 5, AssertJ, Mockito, jqwik, JaCoCo) và deterministic fixtures.
- Dựng Next.js unit/component stack (Vitest, Testing Library, MSW) và coverage gate.
- Áp dụng test-first: production code và unit test nằm cùng feature commit; không dồn test về cuối.
- Auth, Spring Security, error model, validation, trace/correlation và audit base.
- MongoDB migration/index job, transaction helper, outbox/inbox worker.
- Redis cache/rate-limit/session building blocks.
- Cloudflare/Cloudinary dev-staging setup, secret manager, CI/CD, SBOM/scans.
- Testcontainers MongoDB replica set + Redis; OpenAPI lint/contract test.

Exit gate: unit test Java/Next.js chạy offline/deterministic/song song, coverage gate hoạt động; deploy staging tự động, restore smoke và architecture/security baseline pass. Chi tiết ở [12_DETAILED_COMMIT_IMPLEMENTATION_PLAN.md](12_DETAILED_COMMIT_IMPLEMENTATION_PLAN.md) và [14_UNIT_TEST_PLAN.md](14_UNIT_TEST_PLAN.md).

## 5. Phase 1 — Team, catalog và reading MVP (4–6 tuần)

- Account, session, verification/reset/MFA baseline.
- Team Owner add/invite member, permission/revoke tests.
- Catalog, taxonomy, Atlas Search, home read model.
- Public story/chapter, reading session/progress.
- Cloudinary upload signature, moderation state và responsive media.
- Cloudflare cache policy/ISR, cache invalidation theo revision.

Exit gate: public read/search đạt SLO baseline; Team khác không truy cập chéo.

## 6. Phase 2 — Publishing và moderation (3–5 tuần)

- Story/chapter draft, immutable revision, optimistic concurrency.
- Submit/check/review/changes requested/approve/schedule/publish/unpublish.
- Moderator queue; Admin dùng chung moderation permission.
- Comment/report/appeal, notification và audit.
- Worker indexing/read-model/cache/notification propagation.
- UAT theo [11_PUBLISHING_WORKFLOW.md](11_PUBLISHING_WORKFLOW.md).

Exit gate: state transition, permission, revision, schedule race và rollback content pass.

## 7. Phase 3 — Analytics/view validation (2–4 tuần)

- Reading event validation, anti-abuse và daily aggregate.
- Ranking precompute, Team raw/valid/invalid dashboard.
- Reward period/rule version nhưng chưa bật cash-out nếu gate tài chính chưa pass.

Exit gate: load/soak pass, aggregate có thể rebuild và fraud rule explainable.

## 8. Phase 4 — Monetization (4–6 tuần)

Thứ tự bắt buộc:

1. wallet double-entry ledger + balance projection;
2. topup QR/reference + discount config snapshot;
3. automation ingest/match/dedupe;
4. Admin manual approval/reject + anti-double-credit;
5. donation XU-only atomic;
6. reward/referral;
7. withdrawal reserve + fee boundary;
8. Admin approve/reject/provider/reconciliation;
9. kill switch, runbook, incident drill.

Exit gate:

- property/concurrency/idempotency tests pass;
- topup automation/manual race không double-credit;
- donation không thể dùng QR/provider và không âm ví;
- withdrawal boundary `99.999/100.000/999.999/1.000.000` đúng;
- reconciliation/restore/pentest/ops sign-off.

## 9. Phase 5 — Hardening và launch (2–3 tuần)

- Production-like load, chaos/dependency failure, DAST/fuzz/pentest.
- Cloudflare WAF/bot/rate rule tuning.
- MongoDB index/query/capacity review, restore drill.
- Privacy/legal/content/monetization checklist.
- Canary, launch dashboard, on-call và rollback/kill switch rehearsal.

## 10. Definition of Ready

Work item chỉ vào sprint khi có:

- outcome và acceptance criteria đo được;
- actor/role/Team permission;
- API/request/response/error/state impact;
- MongoDB collection/index/migration impact;
- security/privacy/abuse cases;
- idempotency/concurrency/cache/event behavior;
- test evidence và observability;
- dependency/rollout/rollback owner.

## 11. Definition of Done

- Code review và architecture test pass.
- Unit/integration/contract/E2E/security test phù hợp pass.
- OpenAPI/client/examples cập nhật.
- Index/migration backward-compatible; `explain` cho query mới.
- Log/metric/trace/audit/runbook/dashboard đã có.
- Không lộ secret/PII; dependency/container scan đạt gate.
- Staging smoke/load phù hợp pass.
- Product/Ops UAT cho luồng nghiệp vụ.

## 12. ADR cần duy trì

- ADR-001 Modular monolith trước microservices.
- ADR-002 Java Spring Boot + Clean Layered Architecture.
- ADR-003 MongoDB source of truth; Redis disposable.
- ADR-004 MongoDB transaction + outbox cho nghiệp vụ/event.
- ADR-005 Next.js SSR/ISR sau Cloudflare.
- ADR-006 Cloudinary signed upload/private media.
- ADR-007 Atlas Search trước search service riêng.
- ADR-008 Wallet double-entry immutable.
- ADR-009 Team membership permission; không Creator độc lập.
- ADR-010 Donation XU-only.
- ADR-011 Topup discount snapshot và manual fallback.
- ADR-012 Withdrawal gross/fee/reserve policy.

## 13. Rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| Scope creep | release gate và feature flag |
| Team permission sai | centralized policy + BOLA suite |
| MongoDB query/index kém | access-pattern review + explain/load gate |
| Double-credit topup | unique provider ref + atomic state/ledger |
| Double-spend donation/withdrawal | transaction + version + idempotency |
| Admin gom nhiều quyền | MFA, re-auth, reason, audit, high-risk second review |
| Media abuse | Cloudinary restricted signed upload + moderation |
| Edge cache lộ private data | explicit cache policy + automated tests |
