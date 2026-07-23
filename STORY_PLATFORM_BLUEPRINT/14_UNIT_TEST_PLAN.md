# 14. Unit Test Plan

## 1. Mục tiêu

Unit test bảo vệ business rule, state transition, authorization decision, money formula và mapping logic ở tốc độ đủ nhanh để chạy trên mọi PR. Test phải:

- deterministic, chạy song song và không phụ thuộc thứ tự;
- không cần network, MongoDB, Redis, Cloudinary hoặc provider;
- chỉ test một unit/public behavior rõ ràng;
- được viết cùng commit với production code;
- tạo regression test trước khi sửa bug.

Unit test không thay integration/contract/E2E/security/load test. MongoDB transaction/index, Spring Security filter chain, serialization và provider signature cần test tầng phù hợp.

## 2. Phase Base bắt buộc

Phase Base hoàn thành trước module nghiệp vụ:

| Commit | Deliverable |
|---|---|
| C010A | JUnit 5, AssertJ, Mockito, jqwik, JaCoCo và task `unitTest` |
| C010B | Test Data Builder/Object Mother, fake Clock/ID/random và money fixtures |
| C011A | Vitest, Testing Library, user-event, MSW, jsdom và coverage |
| C011B | Naming/tagging, deterministic policy, merged report và flaky-test blocker |

### 2.1. Backend layout

```text
backend/
  src/test/java/com/storyplatform/
    unit/
      identity/
      teams/
      catalog/
      publishing/
      reading/
      community/
      moderation/
      analytics/
      monetization/
    fixtures/
      FixedClock.java
      TestIds.java
      UserBuilder.java
      TeamBuilder.java
      StoryBuilder.java
      WalletBuilder.java
    architecture/
```

### 2.2. Frontend layout

```text
frontend/
  apps/web/src/**/*.test.ts(x)
  apps/admin/src/**/*.test.ts(x)
  packages/api-client/src/**/*.test.ts
  packages/ui/src/**/*.test.tsx
  test/
    setup.ts
    fixtures/
    msw/handlers.ts
```

## 3. Toolchain chuẩn

### Java

- JUnit Jupiter 5.
- AssertJ.
- Mockito chỉ cho application ports/boundaries.
- jqwik cho property-based tests.
- ArchUnit cho dependency/module rules.
- JaCoCo cho line/branch coverage.
- PIT mutation testing theo nightly/weekly cho domain critical.

### Next.js

- Vitest.
- Testing Library + `user-event`.
- MSW cho API boundary ở component/hook test.
- `@testing-library/jest-dom`.
- `axe` hoặc equivalent cho accessibility unit/component checks trọng yếu.

Version phải pin và được dependency scan.

## 4. Lệnh chuẩn

```text
./gradlew unitTest
./gradlew test
./gradlew jacocoTestReport jacocoTestCoverageVerification
./gradlew pitest

pnpm test:unit
pnpm test:unit --changed
pnpm test:coverage
pnpm typecheck
```

Windows dùng Gradle Wrapper tương ứng. Developer không được yêu cầu cài Gradle global.

## 5. Coverage gate

Coverage là guardrail, không phải mục tiêu duy nhất.

| Phạm vi | Line | Branch | Mutation |
|---|---:|---:|---:|
| Domain critical: auth policy, publishing state, ledger/topup/donation/withdrawal | ≥ 95% | ≥ 90% | ≥ 80% |
| Backend application/domain mới hoặc thay đổi | ≥ 90% | ≥ 85% | theo risk |
| Toàn backend | ≥ 80% | ≥ 75% | theo lịch |
| Frontend hooks/components business-critical | ≥ 90% | ≥ 85% | không bắt buộc ban đầu |
| Toàn frontend | ≥ 80% | ≥ 75% | không bắt buộc |

Không tính generated code, simple DTO/configuration và framework bootstrap nếu được cấu hình exclude minh bạch. Không viết test vô nghĩa chỉ để tăng coverage.

Coverage tổng không được giảm. Exception cần owner, lý do, expiry và follow-up.

## 6. Quy tắc test

- Test name: `givenX_whenY_thenZ` hoặc sentence behavior.
- Một behavior chính/test; assertion bổ sung chỉ để mô tả cùng outcome.
- Dùng fixed `Instant`, `Clock`, UUID và seeded random.
- Không `Thread.sleep`, polling thời gian thật hoặc retry test.
- Không phụ thuộc locale/timezone máy; set rõ.
- Không dùng filesystem/network/database thật.
- Mock boundary, không mock class đang test hoặc value object đơn giản.
- Không verify implementation detail nếu không phải contract.
- Không dùng reflection để test private method.
- Test exception bằng stable domain code/type, không dựa nguyên message tự do.
- Property tests phải ghi seed khi fail để reproduce.
- Test phải chạy được theo file/class riêng và cả suite.

## 7. Backend unit-test matrix

### 7.1. Shared/API

| Unit | Case bắt buộc |
|---|---|
| Problem mapper | domain/validation/conflict/precondition → đúng HTTP/code; không lộ exception |
| Cursor codec | encode/decode, signature tamper, version, expiry, malformed payload |
| Idempotency request hash | same body stable; field/order normalization; same key/different body conflict |
| Redaction | token/cookie/email/bank/reference bị mask |
| Money/Xu value object | positive/non-negative rule, overflow, add/subtract/compare |

### 7.2. Identity

| Unit | Case bắt buộc |
|---|---|
| Email normalizer | case/space/Unicode policy, invalid form |
| Password policy | length, breached-password port result, no raw log |
| Verification token | valid, expired, consumed, wrong hash |
| Login use case | active/pending/locked, generic failure, risk deny/challenge |
| Refresh family | normal rotation, reused token revokes family, concurrent retry |
| Session revoke | current/specific/all; securityVersion invalidates |
| Reset password | one-time, expiry, revoke sessions |
| MFA policy | Admin required, recovery use-once, missing scoped grant |
| Re-auth grant | scope, actor, target, expiry, replay |

### 7.3. Team và authorization

| Unit | Case bắt buộc |
|---|---|
| Create Team | creator becomes sole Owner atomically in command model |
| Add member | only Owner, duplicate idempotent/conflict, blocked user/Team |
| Change permission | allowlist, cannot grant beyond owner policy, increments version |
| Remove member | cannot remove last owner, revoke access |
| Publishing policy | active user + Team + membership + permission + ownership |
| Admin/moderator policy | Admin inherits moderation; Moderator cannot finance/config |
| Cache key/version | permission revoke changes authorization version |

### 7.4. Catalog/search

| Unit | Case bắt buộc |
|---|---|
| Slug service | normalize Unicode, collision suffix, reserved slug |
| Catalog filter mapper | allowlisted sort/filter only; unknown rejected |
| Public projection | excludes draft/private/admin fields |
| Home section builder | ordering, empty fallback, payload cap |
| Search query builder | normalized terms, max facet/page, no raw operator/regex |
| Ranking calculator | valid views only, deterministic tie-break, `asOf` |

### 7.5. Publishing

| Unit | Case bắt buộc |
|---|---|
| Story state machine | every allowed/forbidden transition |
| Chapter state machine | draft/review/approved/scheduled/published/hidden/archive |
| Draft create | membership/permission, revision 1, server-owned fields |
| Draft update | editable state, stale version, ownership |
| Revision | immutable snapshot/checksum/revision number |
| Submit validator | required metadata, taxonomy, cover/media, rights evidence |
| Submit use case | freezes revision, creates one review/outbox on retry |
| Automated check result | pass/fail/manual pending/timeout |
| Review decision | one decision, correct revision, reason required |
| Schedule policy | approved only, future bounds, UTC conversion, cancel |
| Publish | revision unchanged, Team/story active, transition once |
| Hide/suspend/reinstate | actor permission, reason/audit/outbox |
| Propagation event | minimal payload, correct version/correlation |

Property-based test sinh chuỗi action ngẫu nhiên để chứng minh state machine không đi vào trạng thái không hợp lệ.

### 7.6. Reading

| Unit | Case bắt buộc |
|---|---|
| Chapter navigator | previous/next boundaries và hidden chapters |
| Progress merge | version/LWW/device timestamp rule |
| Reading session token | sign/verify/expiry/chapter binding/tamper |
| Heartbeat sequence | normal, duplicate, gap, too fast/slow, batch limit |
| Completion | once-only, timeout, no direct valid-view increment |

### 7.7. Community/moderation

| Unit | Case bắt buộc |
|---|---|
| Comment sanitizer/policy | allowed markup, XSS/link/size, edit window |
| Favorite/reaction | idempotent add/remove, unique actor-target |
| Report dedupe | same actor/target/window, trust/limit |
| Case state machine | claim/release/decide/appeal/final state |
| Copyright policy | evidence requirement, temporary hold, appeal window |
| External donation QR policy | detect/route review; documented false-positive handling |

### 7.8. Analytics/view

| Unit | Case bắt buộc |
|---|---|
| Event bucket | deterministic bucket/time boundary |
| Duplicate detector | session/user/window rules |
| View validator | duration, sequence, visibility, self-view, bot/risk combinations |
| Fraud scorer | stable weights/version, reason codes, no one-signal auto-ban |
| Aggregate reducer | commutative/idempotent input, raw=valid+invalid where applicable |
| Reward input | locked period và valid view only |

### 7.9. Notification/media

| Unit | Case bắt buộc |
|---|---|
| Notification dedupe | same event/recipient/type only once |
| Preference policy | opt-in/out, mandatory security notification |
| Retry classifier | retryable/permanent error, max attempt/DLQ |
| Upload signature request | owner/purpose/type/size/folder/TTL |
| Cloudinary webhook verifier | signature/timestamp/replay |
| Asset visibility policy | pending/rejected/private/public transition |

## 8. Monetization unit-test matrix

### 8.1. Ledger và wallet

- Mỗi transaction có ít nhất hai entry.
- Tổng debit bằng tổng credit.
- Amount integer positive; overflow bị chặn.
- Posted transaction immutable.
- Cùng reference/idempotency không post lại.
- Compensation tham chiếu transaction gốc; không sửa entry cũ.
- Balance projection từ mọi permutation hợp lệ cho cùng kết quả.
- Available/reserved không âm.

Property tests sinh transaction/entry để kiểm invariant và projection rebuild.

### 8.2. Topup formula/config

```text
creditedXu = floor(amountVnd × (100 - discountPercent) / 100)
```

| Amount | Discount | Expected |
|---:|---:|---:|
| 100.000 | 10% | 90.000 |
| 10.001 | 10% | 9.000 |
| 100.000 | 0% | 100.000 |
| 100.000 | 12,5% | 87.500 |

Test thêm:

- amount ≤ 0/outside configured range rejected;
- discount ngoài range rejected;
- config version snapshot không đổi khi Admin update;
- calculation không dùng float/double và không overflow;
- transfer reference unique format, không lộ sequential user ID;
- QR payload bind amount/reference/destination/expiry.

### 8.3. Topup state/race

- `awaiting_payment → credited` khi amount/reference/event hợp lệ.
- mismatch → `pending_review`, không credit.
- expired/rejected/credited không credit lần nữa.
- same provider event và bank reference idempotent.
- manual approval cần Admin + MFA/re-auth + reason + evidence.
- manual approval và automation cùng lúc: chỉ một command decision được commit; loser trả kết quả hiện có.
- config thay đổi giữa create/credit không đổi `creditedXu`.
- reject sau credited bị từ chối.

Race được unit-test ở decision/state layer; atomicity thực tế phải có integration test MongoDB.

### 8.4. Donation

- Chỉ currency `XU`; model không có provider/QR/callback.
- amount > 0 và trong limit.
- recipient Team active.
- self/team policy theo product.
- insufficient balance rejected, không tạo entries.
- valid donation tạo debit Reader + credit Team cân bằng.
- retry same key same body trả kết quả cũ; khác body conflict.
- reversal tạo compensation.

Property test: với mọi balance/amount hợp lệ, `readerAfter + teamAfter = readerBefore + teamBefore`.

### 8.5. Reward/referral

- Period chỉ settle sau lock.
- Rule version snapshot.
- Chỉ valid view.
- Cap/rounding deterministic.
- Retry không trả thưởng lại.
- Correction tạo adjustment.
- Self-referral/collusion rule và one-attribution.

### 8.6. Withdrawal

```text
minimum = 100_000
fee = gross < 1_000_000 ? 20_000 : 0
net = gross - fee
```

| Gross | Kết quả | Fee | Net |
|---:|---|---:|---:|
| 0 | reject | - | - |
| 99.999 | reject | - | - |
| 100.000 | accept | 20.000 | 80.000 |
| 999.999 | accept | 20.000 | 979.999 |
| 1.000.000 | accept | 0 | 1.000.000 |
| 1.000.001 | accept | 0 | 1.000.001 |

Test thêm:

- fee/net luôn tính server-side;
- available thiếu thì không reserve;
- create chuyển gross available → reserved;
- approve không do requester tự thực hiện;
- reject/fail release đúng một lần;
- paid settle reserved đúng một lần;
- approve/reject/callback retry idempotent;
- destination unverified/cooling-off rejected;
- integer overflow/max limit.

Property test quanh boundary với input ngẫu nhiên và invariant `gross = fee + net`.

## 9. Frontend unit/component matrix

### Shared/API

- Problem code → message/form field đúng.
- Refresh/retry không loop vô hạn.
- Cursor/filter serialization ổn định.
- Money/XU formatter không đổi giá trị và locale đúng.
- Permission guard chỉ ẩn UI; denied API vẫn render đúng.

### Auth

- Login/register/verify/reset validation.
- Generic error không enumeration.
- MFA/re-auth dialog scope/expiry.
- Session revoke confirmation và optimistic state rollback.

### Team/publishing

- Member permission table; owner-only control.
- Editor dirty state/autosave/version conflict.
- Submit disabled khi prerequisite thiếu.
- Review state/reason rendering.
- Schedule timezone/UTC preview.
- Publish/hide destructive confirmation.

### Reader/community

- Font/theme/navigation/progress.
- Keyboard/focus/accessibility.
- Comment sanitize display và rate/error.
- Report form reason/evidence limits.

### Wallet

- Topup shows server-returned discount/xu; không tự tính làm source of truth.
- QR expiry/countdown và refresh state.
- Donation form chỉ XU, insufficient balance/error.
- Withdrawal fee/net preview lấy từ response/quote server và boundary display.
- Admin manual review requires re-auth/reason/evidence.
- Sensitive destination/reference masked.

## 10. Unit và integration boundary

| Nội dung | Unit | Integration |
|---|---:|---:|
| Formula/state/policy | Có | Có critical happy/race |
| MongoDB unique index | Không | Có |
| MongoDB transaction/atomic CAS | Mock port decision | Có replica set |
| Redis TTL/cache invalidation | Policy/key unit | Có Redis |
| JSON serialization/validation | Mapper unit | Có MockMvc/API |
| Spring Security filter | Policy unit | Có filter chain |
| Cloudinary/provider signature | Verifier unit | Có recorded/sandbox contract |
| OpenAPI conformance | Không | Contract |
| Cloudflare behavior | Không | Staging security test |

Không dùng mock để “chứng minh” MongoDB transaction hoặc unique index.

## 11. CI execution

### Pull request

1. affected unit tests;
2. full backend/frontend unit suite;
3. coverage verification;
4. architecture test;
5. affected integration/contract tests.

### Main/nightly

- full unit/integration/E2E;
- mutation test cho publishing/ledger/topup/donation/withdrawal;
- randomized property tests với sample lớn hơn;
- flaky detector chạy lặp/shuffle.

Target:

- backend unit suite dưới 3 phút;
- frontend unit suite dưới 2 phút;
- không test đơn lẻ quá 1 giây nếu không có lý do;
- test chậm chuyển integration tag, không tăng timeout để che vấn đề.

## 12. Flaky test policy

- Flaky test là defect P1 của pipeline.
- Không auto-retry để biến đỏ thành xanh.
- Nếu buộc quarantine vì blocker ngoài kiểm soát: ticket, owner, root-cause evidence, expiry tối đa 3 ngày.
- Test critical auth/monetization không được quarantine.
- CI lưu seed, order và environment để reproduce.

## 13. Review checklist cho unit test

- Test có thể fail khi behavior bị phá thật không?
- Có test boundary/negative/race decision không?
- Có dùng clock/random/ID deterministic không?
- Mock có đúng boundary không?
- Assertion có kiểm outcome/invariant thay vì implementation detail không?
- Test name có mô tả business behavior không?
- Formula/state transition có property/boundary test không?
- Bug fix có regression test fail trước fix không?
- Coverage/mutation có giảm không?
- Production code và test nằm cùng commit không?

## 14. Exit gate Phase Base

Không bắt đầu Identity/Team feature trước khi:

- Java và Next.js unit suite seed pass local/CI;
- coverage report/gate hoạt động và có baseline;
- fake clock/ID/random + fixture builders sẵn sàng;
- test chạy offline, song song, không phụ thuộc order;
- seeded bad test chứng minh lint/coverage/flaky gate chặn được;
- template PR yêu cầu test evidence.

