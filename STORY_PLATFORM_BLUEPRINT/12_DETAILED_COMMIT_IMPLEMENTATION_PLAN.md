# 12. Kế hoạch implementation chi tiết theo commit

## 1. Cách sử dụng

Mỗi dòng `Cxxx` là **một merge commit trên `dev`**, được tạo bằng squash merge từ một pull request ngắn hạn. `main` chỉ nhận release/hotfix đã qua gate từ `dev`. Commit phải độc lập, build được và không làm hỏng contract đang chạy. WIP commit trên feature branch được squash thành đúng message đã định trước khi merge.

Quy ước:

- Branch: `<type>/<work-item>-<slug>`, ví dụ `feat/MON-002-topup-qr`.
- Commit: Conventional Commits, ví dụ `feat(topup): create QR top-up request`.
- Một PR mục tiêu 0,5–2 ngày làm việc và dưới 400 LOC logic; vượt 800 LOC phải tách hoặc ghi lý do.
- `Verify` là bằng chứng tối thiểu trước merge; release gate đầy đủ nằm trong `10_TEST_AND_RELEASE_GATE.md`.
- Thứ tự dưới đây là baseline. Chỉ chạy song song khi dependency đã merge và hai PR không sửa cùng contract/migration.
- `COMMIT_PLAN.csv` là bản machine-readable đồng bộ với danh sách này.
- Unit test phải nằm trong cùng commit với production code; ma trận đầy đủ ở [14_UNIT_TEST_PLAN.md](14_UNIT_TEST_PLAN.md).

### Quy tắc test-first trong plan

Mỗi feature commit thực hiện theo vòng:

```text
failing unit test → implementation tối thiểu → refactor
→ integration/contract test nếu chạm boundary → evidence trong PR
```

Không tạo một “phase viết unit test bù” ở cuối. Phase Base chỉ dựng framework, fixture và quality gate; unit test nghiệp vụ đi cùng từng commit C029–C112.

## 2. Critical path

```text
ADR/contracts
  → Spring Boot/Next.js foundation
  → MongoDB transaction + outbox + security
  → Identity + Team membership
  → Catalog + Media
  → Publishing + Moderation
  → Reading + Analytics
  → Ledger
  → Topup → Donation → Withdrawal
  → Security/load/recovery gates
  → Canary launch
```

Các commit Monetization không được merge ra production-enabled nếu Identity MFA, audit, MongoDB transaction, idempotency và reconciliation foundation chưa hoàn thành.

## 3. Phase 0 — Contract, ADR và repository foundation

Phase 0 chỉ chốt quyết định và repository governance; **Phase Base A/B là phase implementation đầu tiên**. Không bắt đầu Identity, Team hoặc bất kỳ business module nào trước khi hai exit gate của Phase Base hoàn thành.

| Seq | Branch | Planned merge commit | Work item | Nội dung chính | Verify |
|---:|---|---|---|---|---|
| C001 | `docs/ARC-001-module-boundaries` | `docs(architecture): define modular monolith boundaries` | ARC-001 | Chốt module, dependency và collection ownership | Link check; architecture review |
| C002 | `docs/ARC-002-auth-contract` | `docs(auth): define web and mobile token lifecycle` | ARC-002 | BFF/cookie, mobile PKCE, refresh rotation, CSRF | Threat-model review |
| C003 | `docs/ARC-003-content-media` | `docs(media): define content and Cloudinary policy` | ARC-003 | Revision, sanitize, format/size, private asset | Security/content review |
| C004 | `docs/ARC-004-view-pipeline` | `docs(analytics): define valid-view pipeline` | ARC-004 | Raw/valid/invalid, retention, rebuild | Product + abuse review |
| C005 | `docs/ARC-005-ledger` | `docs(ledger): define double-entry invariants` | ARC-005 | Account types, debit/credit, compensation | Property examples balance |
| C006 | `docs/ARC-006-team-permissions` | `docs(teams): define team-only publishing permissions` | ARC-006 | Owner/member, permission matrix, revoke | BOLA matrix review |
| C007 | `docs/QA-001-openapi-baseline` | `docs(api): baseline versioned OpenAPI contract` | QA-001 | Lint config, breaking-change baseline, examples | Redocly lint zero error |
| C008 | `chore/repository-baseline` | `chore(repo): add workspace governance baseline` | PLT-008 | CODEOWNERS, templates, editorconfig, license policy | Repository rule review |

Exit gate: ADR được duyệt; role, money formula, state machine và API không còn blocker.

## 4. Phase Base A — Codebase, unit-test foundation và CI

| Seq | Branch | Planned merge commit | Work item | Nội dung chính | Verify |
|---:|---|---|---|---|---|
| C009 | `feat/PLT-001-spring-bootstrap` | `feat(platform): bootstrap Spring Boot modular monolith` | PLT-001 | Gradle Kotlin DSL + Wrapper, module/package skeleton, Java toolchain | Build + unit smoke |
| C010 | `test/PLT-001-architecture-rules` | `test(architecture): enforce clean layer dependencies` | PLT-001 | ArchUnit rules và module isolation | Negative fixture fails |
| C010A | `test/base-java-unit-stack` | `test(base): configure Java unit-test stack and coverage` | BASE-001 | JUnit 5, AssertJ, Mockito, jqwik, JaCoCo; unit task không cần network/DB | Seed test + coverage report |
| C010B | `test/base-java-fixtures` | `test(base): add deterministic domain test fixtures` | BASE-002 | Object Mother/Test Data Builder, fixed clock/UUID/random, money helpers | Parallel/repeat test stable |
| C011 | `feat/frontend-next-workspace` | `feat(web): bootstrap Next.js web and admin apps` | PLT-001 | pnpm workspace, App Router, TypeScript strict, shared config/UI/API client | Build + typecheck |
| C011A | `test/base-web-unit-stack` | `test(web): configure Vitest Testing Library and MSW` | BASE-003 | jsdom, component/hook tests, API mock handlers, coverage | Seed component test |
| C011B | `test/base-test-conventions` | `test(base): enforce deterministic test conventions` | BASE-004 | Naming/tagging, no sleep/network, flaky retry forbidden, report merge | Seed flaky test is blocked |
| C012 | `feat/PLT-002-problem-details` | `feat(api): add problem details and correlation ids` | PLT-002 | RFC problem response, trace/correlation propagation | Integration error tests |
| C013 | `feat/PLT-003-validation` | `feat(api): enforce request validation and limits` | PLT-003 | Bean Validation, unknown field/body/depth limits | Fuzz invalid payload |
| C014 | `feat/PLT-004-mongodb` | `feat(persistence): configure MongoDB replica-set access` | PLT-004 | Spring Data MongoDB, pool, timeout, concern/preference | Testcontainer replica set |
| C015 | `feat/PLT-004-migrations` | `feat(persistence): add document and index migrations` | PLT-004 | Versioned migration runner, lock, dry-run | Repeat/rollback-safe test |
| C016 | `feat/PLT-005-outbox-model` | `feat(events): persist transactional outbox messages` | PLT-005 | Outbox/inbox schema, event envelope/version | Transaction integration test |
| C017 | `feat/PLT-005-outbox-worker` | `feat(events): process outbox with lease retry and dlq` | PLT-005 | Atomic claim, backoff, idempotent consumer contract | Multi-worker/replay test |
| C018 | `feat/PLT-006-observability` | `feat(observability): add OpenTelemetry logs metrics traces` | PLT-006 | HTTP/Mongo/Redis/worker instrumentation, redaction | Trace smoke + no-secret test |
| C019 | `feat/PLT-007-health` | `feat(platform): add safe health and readiness probes` | PLT-007 | Liveness/readiness, dependency state không lộ public | Dependency failure test |
| C020 | `ci/PLT-008-quality-gates` | `ci: enforce build test lint and OpenAPI gates` | PLT-008, QA-001 | Java/TS tests, Redocly, coverage, formatting | PR pipeline green |
| C021 | `ci/PLT-008-security-gates` | `ci(security): add secret SAST SCA IaC and image scans` | PLT-008 | Gitleaks, CodeQL/Semgrep, dependency/image/IaC scan | Seeded finding blocks |
| C022 | `feat/redis-building-blocks` | `feat(cache): add Redis namespaces and resilience policy` | INF-004 | Cache/rate/session prefixes, TTL, timeout, degraded mode | Redis outage test |

Exit gate: Java và Next.js unit test chạy offline, deterministic và song song; coverage report/gate hoạt động; staging artifact build được; integration test dùng MongoDB replica set thật; CI chặn vi phạm layer/security.

## 5. Phase Base B — IaC, edge và media foundation

| Seq | Branch | Planned merge commit | Work item | Nội dung chính | Verify |
|---:|---|---|---|---|---|
| C023 | `infra/INF-001-environments` | `feat(infra): provision isolated dev and staging` | INF-001 | Network, runtime, service identity, secret references | IaC plan + policy scan |
| C024 | `infra/INF-003-mongodb` | `feat(infra): provision managed MongoDB with PITR` | INF-003 | Replica set, private access, backup, alerts | Restore smoke |
| C025 | `infra/INF-004-redis` | `feat(infra): provision private managed Redis` | INF-004 | TLS/auth, memory/eviction, metrics | Connectivity + failover smoke |
| C026 | `infra/INF-005-cloudinary` | `feat(infra): configure restricted Cloudinary environments` | INF-005 | Folder/preset, private delivery, webhook secret | Invalid unsigned upload fails |
| C027 | `infra/INF-002-cloudflare` | `feat(edge): configure Cloudflare TLS WAF and rate limits` | INF-002 | DNS/TLS, managed rules, origin protection | Bypass/cache security tests |
| C028 | `ci/deploy-staging` | `ci(cd): deploy signed immutable artifacts to staging` | INF-001, PLT-008 | Digest pin, SBOM, provenance, rolling deploy | Deploy + rollback smoke |

Exit gate: dev/staging tách biệt; Mongo restore, Cloudinary signature và Cloudflare private-cache test pass.

## 6. Phase 1 — Identity và session

| Seq | Branch | Planned merge commit | Work item | Nội dung chính | Verify |
|---:|---|---|---|---|---|
| C029 | `feat/AUTH-001-registration` | `feat(auth): register pending users` | AUTH-001 | Normalize email, consent version, Argon2 placeholder flow | Enumeration/duplicate tests |
| C030 | `feat/AUTH-001-email-verification` | `feat(auth): verify email with single-use tokens` | AUTH-001 | Token hash, expiry, consumed state, outbox email | Replay/expiry test |
| C031 | `feat/AUTH-002-login` | `feat(auth): authenticate with Argon2id and risk limits` | AUTH-002 | Login, generic error, lock/backoff signals | Timing/rate tests |
| C032 | `feat/AUTH-003-refresh-rotation` | `feat(auth): rotate refresh token families` | AUTH-003 | Hash, family, reuse detection, session version | Concurrent replay test |
| C033 | `feat/AUTH-004-session-management` | `feat(auth): revoke current and remote sessions` | AUTH-004 | Logout, list masked sessions, revoke specific/all | Revocation integration |
| C034 | `feat/AUTH-005-password-reset` | `feat(auth): implement safe password reset` | AUTH-005 | Uniform forgot response, one-time reset, revoke sessions | Enumeration/replay test |
| C035 | `feat/AUTH-006-mfa` | `feat(auth): enforce MFA for privileged users` | AUTH-006 | TOTP/passkey adapter, recovery, security version | MFA bypass negative tests |
| C036 | `feat/auth-reauth` | `feat(auth): issue scoped reauthentication grants` | AUTH-006 | Short-lived grant for sensitive actions | Scope/expiry/replay tests |
| C037 | `feat/web-auth-ui` | `feat(web): add accessible authentication journeys` | AUTH-001..AUTH-006 | Register/login/verify/reset/MFA/session UI | E2E + accessibility |

Exit gate: token lifecycle, CSRF/cookie policy, MFA/re-auth và session revoke pass threat tests.

## 7. Phase 2 — Team, membership và authorization

| Seq | Branch | Planned merge commit | Work item | Nội dung chính | Verify |
|---:|---|---|---|---|---|
| C038 | `feat/TEAM-001-profile` | `feat(profile): add private and public user profiles` | TEAM-001 | DTO tách public/private, optimistic version | Field exposure test |
| C039 | `feat/TEAM-001-team-crud` | `feat(teams): create and manage teams` | TEAM-001 | Creator becomes Owner membership atomically | Ownership integration |
| C040 | `feat/TEAM-002-membership` | `feat(teams): let owners add and invite members` | TEAM-002 | Unique membership/invitation, notification | Idempotency + last-owner test |
| C041 | `feat/TEAM-002-permissions` | `feat(teams): manage versioned member permissions` | TEAM-002 | Permission allowlist, revoke/version cache | Cross-Team BOLA suite |
| C042 | `feat/TEAM-003-follow` | `feat(teams): follow teams idempotently` | TEAM-003 | Unique relation, eventual counter/outbox | Concurrent toggle test |
| C043 | `feat/admin-authorization-policy` | `feat(security): centralize moderator and admin policies` | QA-002, AUTH-006 | Admin inherits moderation/finance/support capabilities | BFLA matrix |
| C044 | `feat/web-team-workspace` | `feat(web): add team and membership workspace` | TEAM-001..TEAM-003 | Team settings/member/permission/follow UI | Owner/member E2E |

Exit gate: không actor nào truy cập draft/team khác; revoke permission có hiệu lực trong SLO.

## 8. Phase 3 — Catalog, discovery và public Next.js

| Seq | Branch | Planned merge commit | Work item | Nội dung chính | Verify |
|---:|---|---|---|---|---|
| C045 | `feat/CAT-001-taxonomy` | `feat(catalog): add grouped taxonomy` | CAT-001 | Category model, admin seed/migration, cache | Duplicate/group tests |
| C046 | `feat/CAT-002-story-model` | `feat(catalog): add story metadata and indexes` | CAT-002 | Story collection, slug, compound indexes, projection | Explain + uniqueness tests |
| C047 | `feat/CAT-002-story-api` | `feat(catalog): expose public story catalog` | CAT-002 | Filter/sort/cursor/ETag/cache headers | Contract + cursor tests |
| C048 | `feat/CAT-003-chapter-list` | `feat(catalog): expose published chapter lists` | CAT-003 | Public-only projection and index | Hidden chapter negative test |
| C049 | `feat/CAT-004-home-model` | `feat(discovery): build versioned home read models` | CAT-004 | Sections, fallback, outbox rebuild | Payload/query budget |
| C050 | `feat/CAT-005-atlas-search` | `feat(search): add Atlas Search and facets` | CAT-005 | Search mapping, highlight/facet, timeout | Relevance + abuse test |
| C051 | `feat/CAT-005-suggestions` | `feat(search): add bounded autocomplete suggestions` | CAT-005 | Rate limit, normalized input, search-after | Latency/load test |
| C052 | `feat/web-public-catalog` | `feat(web): render home catalog search and story pages` | CAT-002..CAT-005 | SSR/ISR, metadata/SEO, loading/error states | Lighthouse + E2E |
| C053 | `feat/edge-public-cache` | `feat(edge): cache versioned public catalog safely` | INF-002, CAT-004 | Cloudflare cache rules, Vary/ISR policy | Auth-cookie cache negative |

Exit gate: catalog/search p95 đạt mục tiêu và không có collection scan ngoài exception đã duyệt.

## 9. Phase 4 — Cloudinary và publishing

| Seq | Branch | Planned merge commit | Work item | Nội dung chính | Verify |
|---:|---|---|---|---|---|
| C054 | `feat/MED-001-upload-signature` | `feat(media): issue restricted Cloudinary upload signatures` | MED-001 | Ownership, purpose, folder, type/size/hash, TTL | Tamper/expiry tests |
| C055 | `feat/MED-002-webhook` | `feat(media): verify Cloudinary webhook events` | MED-002 | Signature/timestamp/dedupe, asset state | Replay/signature test |
| C056 | `feat/MED-002-processing` | `feat(media): moderate transform and publish assets` | MED-002 | Re-encode, metadata strip, variants/private delivery | Malicious file suite |
| C057 | `feat/PUB-001-story-draft` | `feat(publishing): create team-owned story drafts` | PUB-001 | `story:create`, revision 1, idempotency/outbox | Cross-Team/duplicate test |
| C058 | `feat/PUB-001-story-update` | `feat(publishing): update drafts with optimistic locking` | PUB-001 | `If-Match`, editable states, new revision | Lost-update test |
| C059 | `feat/PUB-002-chapter-draft` | `feat(publishing): create immutable chapter revisions` | PUB-002 | Sanitize/normalize/checksum/size limit | XSS/large-content tests |
| C060 | `feat/PUB-003-submit` | `feat(publishing): submit frozen revisions for review` | PUB-003 | Completeness checks, review case, state/outbox | Retry/stale revision test |
| C061 | `feat/PUB-003-automated-checks` | `feat(publishing): run automated content prechecks` | PUB-003 | Media/policy/link/QR/spam results | Timeout/manual fallback |
| C062 | `feat/MOD-002-review-queue` | `feat(moderation): claim publishing reviews safely` | MOD-002 | Queue/cursor/lease/assignee/priority | Multi-reviewer race |
| C063 | `feat/MOD-002-review-decision` | `feat(moderation): decide publishing reviews with audit` | MOD-002, PUB-003 | Approve/request changes/reject + policy version | One-decision invariant |
| C064 | `feat/PUB-004-scheduling` | `feat(publishing): schedule approved revisions` | PUB-004 | UTC schedule, cancel/reschedule, revision pin | Time/race tests |
| C065 | `feat/PUB-004-publish-worker` | `feat(publishing): publish due revisions exactly once` | PUB-004, PLT-005 | Worker lease, state transition, outbox | Multi-worker/replay test |
| C066 | `feat/PUB-004-unpublish` | `feat(publishing): hide suspend and reinstate content` | PUB-004, MOD-002 | Team hide, moderator/Admin suspend, reason | Cache visibility test |
| C067 | `feat/publishing-propagation` | `feat(publishing): propagate search cache and notifications` | PUB-004, CAT-004 | Read model, Atlas Search, Cloudflare purge, ISR event | Consumer idempotency |
| C068 | `feat/web-publishing-workspace` | `feat(web): add story and chapter editor workflow` | PUB-001..PUB-004 | Autosave/version conflict, review/schedule status | Publishing E2E |
| C069 | `feat/admin-review-ui` | `feat(admin): add moderation review console` | MOD-002 | Claim, compare revision, decisions/audit | Moderator/Admin E2E |

Exit gate: toàn bộ acceptance criteria trong `11_PUBLISHING_WORKFLOW.md` pass.

## 10. Phase 5 — Reading, community và moderation

| Seq | Branch | Planned merge commit | Work item | Nội dung chính | Verify |
|---:|---|---|---|---|---|
| C070 | `feat/READ-001-chapter-detail` | `feat(reading): serve revisioned published chapters` | READ-001 | Sanitized content, prev/next, ETag/cache | Hidden/stale revision test |
| C071 | `feat/READ-002-progress` | `feat(reading): synchronize reading progress` | READ-002 | LWW/version/device time rule | Cross-device conflict |
| C072 | `feat/READ-002-history` | `feat(reading): add cursor-based history controls` | READ-002 | List/delete, privacy, keyset | Pagination/privacy test |
| C073 | `feat/READ-003-session` | `feat(reading): start privacy-bounded reading sessions` | READ-003 | Signed session, anonymous support, quota | Forgery/expiry test |
| C074 | `feat/READ-004-heartbeat` | `feat(reading): ingest sequenced heartbeat batches` | READ-004 | Sequence/dedupe/cadence, async event | Replay/load test |
| C075 | `feat/READ-004-complete` | `feat(reading): complete sessions asynchronously` | READ-004 | Completion/timeout, no direct view increment | Retry test |
| C076 | `feat/web-reader` | `feat(web): add accessible reader and progress sync` | READ-001..READ-004 | Theme/font/nav, progress, responsive/SEO | E2E + accessibility |
| C077 | `feat/COM-001-favorites` | `feat(community): add idempotent favorites and follows` | COM-001 | Unique relations/counters/reconcile | Concurrent toggle |
| C078 | `feat/COM-002-comments` | `feat(community): add safe comment lifecycle` | COM-002 | Sanitize, thread limit, edit/delete, spam | XSS/rate/BOLA tests |
| C079 | `feat/COM-003-reactions` | `feat(community): add idempotent reactions` | COM-003 | Unique actor-target-type, counter | Concurrent test |
| C080 | `feat/MOD-001-reports` | `feat(moderation): create deduplicated reports` | MOD-001 | Reason/evidence/trust/rate rules | Abuse/dedupe test |
| C081 | `feat/MOD-003-appeals` | `feat(moderation): add appeal workflow` | MOD-003 | Window, reviewer separation, final state | Transition tests |
| C082 | `feat/MOD-004-copyright` | `feat(moderation): add copyright takedown cases` | MOD-004 | Evidence/private media, hold/SLA/appeal | Access/SLA tests |

Exit gate: reader cache an toàn; comment/report abuse test và moderation SLA instrumentation pass.

## 11. Phase 6 — Notification và analytics

| Seq | Branch | Planned merge commit | Work item | Nội dung chính | Verify |
|---:|---|---|---|---|---|
| C083 | `feat/NOT-001-inbox` | `feat(notifications): add cursor-based notification inbox` | NOT-001 | Inbox, watermark mark-read, dedupe | Pagination/replay test |
| C084 | `feat/NOT-002-delivery` | `feat(notifications): deliver consent-aware email and push` | NOT-002 | Preferences, retry/DLQ/unsubscribe | Provider failure test |
| C085 | `feat/VIEW-001-events` | `feat(analytics): store bucketed reading events` | VIEW-001 | Append path, retention, PII minimization | Target ingest load |
| C086 | `feat/VIEW-002-validation` | `feat(analytics): classify valid reading views` | VIEW-002 | Versioned rules, duplicate/self/bot signals | Replay/property tests |
| C087 | `feat/VIEW-003-fraud` | `feat(analytics): score suspicious traffic for review` | VIEW-003 | Explainable score, hold/case, no auto-ban single signal | Bias/threshold tests |
| C088 | `feat/VIEW-004-aggregates` | `feat(analytics): build hourly and daily aggregates` | VIEW-004 | Idempotent rollup, backfill/rebuild/reconcile | Raw-to-aggregate match |
| C089 | `feat/CAT-006-rankings` | `feat(discovery): serve precomputed story and team rankings` | CAT-006 | Period/asOf/metric/fallback | Valid-view-only tests |
| C090 | `feat/web-team-analytics` | `feat(web): add team view and quality analytics` | VIEW-004 | Raw/valid/invalid, period/reason summary | Permission + chart E2E |

Exit gate: event ingest chịu tải; aggregate/ranking rebuild và reconciliation chính xác.

## 12. Phase 7 — Wallet, topup, donation, reward và withdrawal

| Seq | Branch | Planned merge commit | Work item | Nội dung chính | Verify |
|---:|---|---|---|---|---|
| C091 | `feat/MON-001-ledger-model` | `feat(ledger): add immutable balanced transactions` | MON-001 | Accounts, entries, references, idempotency | Balanced property tests |
| C092 | `feat/MON-001-balance` | `feat(wallet): project available and reserved xu balances` | MON-001 | Versioned projection/rebuild, wallet API | Ledger-to-balance reconcile |
| C093 | `feat/MON-002-discount-config` | `feat(topup): add versioned discount configuration` | MON-002 | Default 10%, Admin MFA/re-auth/reason/audit | 100k→90k + version test |
| C094 | `feat/MON-002-topup-request` | `feat(topup): create QR top-up requests` | MON-002 | Amount, discount snapshot, unique reference, expiry | Formula/QR/idempotency |
| C095 | `feat/MON-003-topup-webhook` | `feat(topup): ingest signed payment events` | MON-003 | Raw verification, provider/event/bank-ref dedupe | Replay/signature tests |
| C096 | `feat/MON-003-topup-match` | `feat(topup): match and credit automated top-ups` | MON-003 | Amount+reference, ledger/outbox atomic | Concurrent event tests |
| C097 | `feat/MON-004-manual-topup` | `feat(topup): approve failed automation manually` | MON-004 | Admin re-auth, evidence/reason, atomic CAS | Manual-vs-auto race |
| C098 | `feat/MON-004-topup-reject` | `feat(topup): reject and audit unmatched top-ups` | MON-004 | Review states, notify, no credit | Transition/idempotency |
| C099 | `feat/MON-005-donation` | `feat(donation): transfer xu atomically to teams` | MON-005 | Reader debit, Team credit, no QR/provider | Overdraft/concurrency |
| C100 | `feat/MON-005-donation-policy` | `feat(moderation): block external donation QR content` | MON-005, MOD-001 | Link/QR policy signal and moderation case | Policy false-positive UAT |
| C101 | `feat/MON-006-rewards` | `feat(rewards): settle versioned reward periods` | MON-006 | Lock period, valid-view basis, compensation | Replay/reconcile tests |
| C102 | `feat/MON-007-referrals` | `feat(referrals): attribute referrals with fraud controls` | MON-007 | One attribution, self/collusion checks | Concurrency/abuse tests |
| C103 | `feat/MON-008-withdrawal-request` | `feat(withdrawal): reserve xu for team withdrawals` | MON-008 | Permission, destination snapshot, min/gross reserve | Insufficient/concurrency |
| C104 | `feat/MON-008-withdrawal-fee` | `feat(withdrawal): enforce fee boundaries` | MON-008 | 99.999 reject; 100k/999.999 fee 20k; 1m fee 0 | Boundary table tests |
| C105 | `feat/MON-009-withdrawal-review` | `feat(withdrawal): let admins approve or reject safely` | MON-009 | Re-auth/risk/reason, requester separation, release once | Approve-reject race |
| C106 | `feat/MON-009-withdrawal-provider` | `feat(withdrawal): process idempotent external payouts` | MON-009 | Provider adapter, processing/paid/failed state | Timeout/callback/retry |
| C107 | `feat/MON-010-reconciliation` | `feat(monetization): reconcile top-ups and withdrawals` | MON-010 | Daily match, mismatch case, compensating action | Fixture reconciliation |
| C108 | `feat/MON-010-kill-switch` | `feat(monetization): add audited transaction kill switches` | MON-010 | Stop credit/withdraw while ingesting evidence | Incident drill |
| C109 | `feat/web-wallet-topup` | `feat(web): add wallet and QR top-up journeys` | MON-001..MON-004 | Balance/history/QR/expiry/manual status | User E2E |
| C110 | `feat/web-donation` | `feat(web): add xu-only donation journey` | MON-005 | Amount/message/confirmation/no external pay | Insufficient balance E2E |
| C111 | `feat/web-team-withdrawal` | `feat(web): add team withdrawal workspace` | MON-008, MON-009 | Fee preview from server, destination, history/status | Boundary/permission E2E |
| C112 | `feat/admin-monetization` | `feat(admin): add top-up and withdrawal review console` | MON-004, MON-009, MON-010 | Masked evidence, re-auth, reason, reconciliation | Admin audit E2E |

Exit gate: QA-005 property/failure suite, manual-vs-automation race, restore/reconcile và security review đều pass trước khi bật tiền thật.

## 13. Phase 8 — Operations, quality gates và launch

| Seq | Branch | Planned merge commit | Work item | Nội dung chính | Verify |
|---:|---|---|---|---|---|
| C113 | `feat/OPS-001-dashboards` | `feat(operations): add SLO and domain dashboards` | OPS-001 | RED/JVM/Mongo/Redis/outbox/business invariants | Synthetic signal visible |
| C114 | `docs/OPS-002-runbooks` | `docs(operations): add alert and incident runbooks` | OPS-002 | SEV matrix, owner, topup/ledger/security runbooks | Tabletop exercise |
| C115 | `test/OPS-003-restore` | `test(recovery): automate MongoDB restore verification` | OPS-003 | PITR isolated restore + integrity checks | RPO/RTO evidence |
| C116 | `test/QA-002-authorization` | `test(security): complete cross-role authorization matrix` | QA-002 | BOLA/BFLA all resource IDs/roles | Suite green |
| C117 | `test/QA-003-load` | `test(performance): add ramp spike soak and cold-cache suites` | QA-003 | Traffic mix, failover/backpressure, thresholds | SLO report |
| C118 | `test/QA-004-security` | `test(security): add DAST fuzz and malicious upload suites` | QA-004 | Injection/XSS/SSRF/CSRF/CORS/media/rate | No high/critical |
| C119 | `test/QA-005-monetization` | `test(monetization): prove ledger and payment invariants` | QA-005 | Replay/race/failure/restore/boundary property suite | Invariants pass |
| C120 | `fix/hardening-findings` | `fix(security): resolve release-blocking findings` | QA-004, QA-005 | Remediation only, no feature scope | Rescan + regression |
| C121 | `docs/LAUNCH-001-readiness` | `docs(release): record production readiness evidence` | LAUNCH-001 | Sign-offs, capacity, legal/privacy/ops checklist | All gates signed |
| C122 | `release/1.0.0-rc1` | `chore(release): prepare 1.0.0 release candidate` | LAUNCH-001 | Version, migration manifest, SBOM, notes | Staging smoke |
| C123 | `release/1.0.0` | `chore(release): publish signed 1.0.0 artifacts` | LAUNCH-002 | Signed tag/image/provenance, disabled flags default | Artifact verification |
| C124 | `ops/LAUNCH-002-canary` | `feat(release): enable production canary` | LAUNCH-002 | 1–5% rollout, auto-halt thresholds | Canary observation |
| C125 | `ops/LAUNCH-002-rollout` | `feat(release): complete progressive production rollout` | LAUNCH-002 | 25→50→100%, on-call/rollback ready | SLO + business invariants |
| C126 | `docs/post-launch-review` | `docs(operations): record launch review and follow-ups` | LAUNCH-002 | Actual capacity, incidents, debt, next milestones | Owner/due date assigned |

## 14. Quy tắc song song hóa

Có thể chạy song song:

- Next.js shell với Spring Boot foundation sau C007.
- IaC với Identity sau C020, miễn secret/contract không thay đổi.
- Catalog public UI sau contract C047, dùng generated mock/client.
- Notification và Analytics sau outbox C017.

Không chạy song song nếu chưa có coordination owner:

- MongoDB migration/index cùng collection.
- OpenAPI breaking change và frontend consumer của endpoint đó.
- Publishing state machine/review/scheduler.
- Wallet ledger/topup/donation/withdrawal trên cùng account model.
- Cloudflare cache rule và private response contract.

## 15. Quy tắc thay đổi kế hoạch

Một commit được reorder/tách/gộp chỉ khi PR cập nhật:

1. `COMMIT_PLAN.csv`;
2. dependency và work item liên quan;
3. API/migration/security impact;
4. lý do trong ADR hoặc PR description;
5. evidence rằng critical path/release gate không bị bỏ qua.

Không gộp hai commit tài chính nhạy cảm chỉ để “merge cho nhanh”. Nếu commit migration không tương thích ngược, phải tách thành expand → backfill → switch → contract và ghi rõ rollback/forward-fix.

## 16. Definition of Done cho từng planned commit

- Branch/commit/PR đúng rulebook.
- Build, format, lint, unit và test liên quan pass.
- Contract, migration/index, generated client và docs cùng commit khi cần.
- Security/privacy/threat delta được đánh giá.
- Log/metric/trace/audit không lộ secret/PII.
- Query mới có index/projection và evidence `explain` khi là hot path.
- Feature có flag/rollback/forward-fix khi rủi ro.
- Reviewer đúng CODEOWNERS; 2 approvals cho sensitive area.
- PR squash thành đúng một planned merge commit, trừ chuỗi migration bắt buộc đã được plan riêng.
