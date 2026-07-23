# 13. Engineering, Git và Security Rulebook

## 1. Phạm vi và mức bắt buộc

Rulebook áp dụng cho backend, frontend, IaC, OpenAPI, migration, scripts và tài liệu. Từ khóa:

- **MUST**: bắt buộc; vi phạm chặn merge.
- **SHOULD**: mặc định phải làm; ngoại lệ cần ghi lý do trong PR.
- **MAY**: tùy chọn có kiểm soát.

Không có deadline nào tự động cho phép bỏ qua security, test, audit hoặc review. Emergency path có quy trình riêng nhưng vẫn để lại bằng chứng và follow-up.

## 2. Branching model

Áp dụng **integration-branch workflow với `dev` và short-lived branches**. `dev` là nhánh tích hợp; `main` là nhánh production/release.

### 2.1. Nhánh

| Nhánh | Mục đích | Tuổi tối đa |
|---|---|---:|
| `main` | luôn releasable, protected | vĩnh viễn |
| `dev` | tích hợp planned commits đã qua CI/review | vĩnh viễn |
| `feat/<ID>-<slug>` | tính năng | 3 ngày làm việc |
| `fix/<ID>-<slug>` | bug | 2 ngày |
| `test/<ID>-<slug>` | test-only | 2 ngày |
| `docs/<ID>-<slug>` | tài liệu/ADR | 2 ngày |
| `infra/<ID>-<slug>` | IaC/runtime | 3 ngày |
| `ci/<ID>-<slug>` | pipeline | 2 ngày |
| `hotfix/<incident>-<slug>` | production incident | ngắn nhất có thể |
| `release/<version>` | stabilization có thời hạn | tối đa 5 ngày |

Không tạo thêm `develop`, nhánh cá nhân dài hạn hoặc environment branch. Feature/fix/test branch MUST tách trực tiếp từ `origin/dev` sau `git fetch origin --prune`, không tách từ local `dev` có thể đã stale. Nhánh hoàn tất phải được push lên `origin` trước khi tích hợp về `dev`. Release branch tách từ `origin/dev` và mở PR vào `main`. Môi trường vẫn được xác định bằng artifact digest, tag, config và deployment record.

### 2.2. Tên nhánh

```text
feat/PUB-003-submit-review
fix/MON-004-manual-credit-race
infra/INF-003-mongodb-pitr
hotfix/INC-2026-014-disable-topup-credit
```

- ASCII lowercase cho slug, dùng dấu `-`.
- Phải có work item/incident ID, trừ maintenance nhỏ do maintainer duyệt.
- Không đưa tên khách hàng, email, ticket bí mật hoặc dữ liệu sự cố nhạy cảm vào tên nhánh.

### 2.3. Đồng bộ

- Branch thường MUST bắt đầu từ `origin/dev` mới nhất sau fetch; `hotfix` bắt đầu từ production tag/`origin/main`.
- Trình tự bắt buộc: fetch → tạo/checkout branch từ `origin/dev` → code/test/review → commit → push branch lên `origin` → tích hợp branch remote vào `dev` → push `dev` → fetch lại trước branch kế tiếp.
- Trước khi tích hợp, `git merge-base --is-ancestor origin/dev <branch>` MUST thành công; nếu không, rebase branch lên `origin/dev` và chạy lại gate.
- Tích hợp local được maintainer/automation cho phép MUST dùng `git merge --ff-only`; không tạo merge commit ngoài kế hoạch.
- Rebase lên target branch trước final approval khi branch bị lệch đáng kể.
- Không force-push sau khi review bắt đầu nếu không thông báo; khi cần chỉ dùng `--force-with-lease`.
- Không merge target branch vào feature branch để “giải conflict” trừ trường hợp được maintainer duyệt.
- Branch tự xóa sau merge; stale branch bot cảnh báo sau 3 ngày.

## 3. Protected `main` và `dev`

`main` MUST:

- cấm direct push và force push;
- cấm xóa branch;
- yêu cầu PR;
- yêu cầu status checks theo mục 9;
- yêu cầu conversation resolved;
- yêu cầu CODEOWNERS;
- yêu cầu branch up-to-date hoặc merge queue;
- yêu cầu signed commit cho merge result;
- áp dụng linear history;
- chặn secret và high/critical security finding;
- hạn chế bypass cho tối đa nhóm incident/repository admin, mọi bypass có audit.

`dev` áp dụng required checks và linear history. Approval thông thường tối thiểu một CODEOWNER; khu vực sensitive vẫn theo bảng approval ở mục 5.4. Không code/commit trực tiếp trên `dev`. Tích hợp vào remote `dev` chỉ được thực hiện từ nhánh đã push và đã qua gate, bằng squash merge trên GitHub hoặc fast-forward bởi maintainer/automation được ủy quyền; cấm force-push.

Repository admin không dùng quyền bypass cho công việc bình thường.

## 4. Commit rules

### 4.1. Merge strategy

- Mặc định **squash merge**: một PR tạo một planned merge commit trên `dev`.
- PR title trở thành commit subject và MUST theo Conventional Commits.
- WIP/fixup commits được squash trước merge.
- Không dùng merge commit; rebase merge chỉ dùng cho chuỗi commit migration đã được duyệt cần bảo toàn.
- Không rewrite lịch sử `main` hoặc `dev`.

### 4.2. Conventional Commits

```text
<type>(<scope>): <imperative summary>

<why / design constraints / migration impact>

Refs: MON-004
Security-Impact: high
```

Type:

- `feat`, `fix`, `refactor`, `perf`, `test`, `docs`, `build`, `ci`, `chore`, `revert`.

Scope chuẩn:

- `auth`, `teams`, `catalog`, `publishing`, `reading`, `community`, `moderation`;
- `media`, `notifications`, `analytics`, `ledger`, `topup`, `donation`, `withdrawal`;
- `web`, `admin`, `api`, `persistence`, `cache`, `events`, `security`, `infra`, `release`.

Subject:

- tối đa 72 ký tự, imperative, không dấu chấm cuối;
- mô tả outcome, không dùng “update”, “fix stuff”, “WIP”;
- breaking change dùng `!` và footer `BREAKING CHANGE:`.

### 4.3. Atomic commit

Một planned commit MUST:

- build và test được;
- gồm production code + unit test + contract/docs/migration cần thiết;
- chỉ có một outcome nghiệp vụ/kỹ thuật;
- không chứa format toàn repo cùng feature;
- không chứa generated binary, credential, dump hoặc dependency cache;
- có rollback/forward-fix rõ khi chạm schema, ledger, edge hoặc deployment.

## 5. Pull request rules

### 5.1. Kích thước

- Mục tiêu dưới 400 LOC logic thay đổi.
- 400–800 LOC cần giải thích tại sao không thể tách.
- Trên 800 LOC logic phải tách, trừ generated code/migration/data fixture được reviewer lọc riêng.
- PR tối đa một planned commit outcome.

### 5.2. Template bắt buộc

```text
## Outcome
## Work item / planned commit
## Changes
## API / data / event impact
## Security and privacy impact
## Test evidence
## Query/index evidence
## Observability/audit
## Rollout, feature flag and rollback
## Screenshots/accessibility (UI)
## Checklist
```

PR description không được chỉ link ticket. Reviewer phải hiểu được “why”, boundary và failure mode ngay trong PR.

### 5.3. Draft và review

- Mở Draft sớm nếu cần feedback contract.
- Chỉ chuyển Ready khi self-review xong, CI xanh, không có TODO không có ticket.
- Author phản hồi bằng evidence/code, không chỉ “done”.
- Reviewer phân loại comment: `blocking`, `security`, `question`, `nit`, `follow-up`.
- Không resolve comment của reviewer khi chưa xử lý hoặc thống nhất.
- Sau thay đổi lớn, request re-review rõ ràng.

### 5.4. Approval

| Khu vực | Approval tối thiểu |
|---|---:|
| Docs/UI thông thường | 1 |
| Backend business rule/API/migration | 1 CODEOWNER |
| Auth/authorization/session | 2, gồm security owner |
| Ledger/topup/donation/withdrawal | 2, gồm domain + security owner |
| Cloudflare/secret/IaC production | 2, gồm platform owner |
| Emergency hotfix | 1 incident approver trước deploy + review thứ hai trong 24h |

Author không tự approve. Reviewer có conflict lợi ích hoặc là requester của thao tác tài chính phải được thay thế.

## 6. Tác phong kỹ thuật

### 6.1. Trước khi code

- Đọc acceptance criteria, API, state machine, threat/data classification.
- Xác nhận dependency và query/access pattern.
- Viết/điều chỉnh test trước hoặc cùng implementation.
- Nêu assumption có ảnh hưởng tiền/quyền/trạng thái trong PR.
- Nếu contract chưa rõ, dừng code và tạo ADR/question; không tự invent business rule im lặng.

### 6.2. Trong khi code

- Tập trung một work item; không “tiện tay” refactor xa phạm vi.
- Không comment-out code, bỏ TODO không có ID hoặc swallow exception.
- Không copy code nhạy cảm từ nguồn không rõ license.
- Không đưa dữ liệu production vào local/test/screenshot.
- Không chạy destructive command trên production từ laptop cá nhân.
- Pair/mob review khuyến nghị cho auth, ledger và race condition.

### 6.3. Khi bị block

- Sau 30–60 phút không tiến triển, ghi rõ blocker, evidence đã thử và người cần hỗ trợ.
- Không che failure bằng skip test, tăng timeout tùy ý hoặc retry vô hạn.
- Không merge workaround không có expiry/owner.

### 6.4. Handoff

Handoff gồm branch/PR, trạng thái, quyết định, test đã chạy, known risk, migration/flag và next action. Không handoff bằng “gần xong”.

## 7. Coding rules — Java Spring Boot

- Dùng Gradle Wrapper; Java toolchain và dependency version pin.
- Dependency: `presentation → application → domain`; infrastructure implement application ports.
- Domain không import Spring, MongoDB, Redis, HTTP hoặc Cloudinary SDK.
- Controller chỉ parse/authorize/call use case/map response.
- Không trả Mongo document/entity trực tiếp.
- Request DTO reject unknown field ở command nhạy cảm.
- Không nhận raw `Document`, Criteria, aggregation pipeline, sort field hoặc SpEL từ client.
- Money/xu dùng `long` có overflow guard hoặc value object; không `float/double`.
- Time dùng `Instant` + injected `Clock`; ID/random dùng injectable generator trong domain.
- State transition tập trung tại aggregate/domain service, không rải trong controller/worker.
- Exception không dùng để điều khiển luồng bình thường.
- Transaction ngắn; không gọi HTTP/Cloudinary/email/provider trong MongoDB transaction.
- Logging parameterized và redacted; không log token, QR payload đầy đủ, destination, chapter private content.
- Public class/method có contract rõ; tránh `Util`, `Helper`, god service.
- Checkstyle/Spotless, static analysis và nullness rule chạy CI.

## 8. Coding rules — Next.js/TypeScript

- TypeScript `strict`; không `any` nếu không có wrapper/justification.
- Generated API types/client là source contract; không duplicate DTO thủ công.
- Server Component mặc định; Client Component chỉ khi cần interaction/browser API.
- Secret/provider credential chỉ ở server runtime.
- Không đưa token vào `localStorage`; cookie/session theo auth design.
- Không dùng `dangerouslySetInnerHTML` ngoài component sanitize-reviewed duy nhất.
- UI phải có loading/empty/error/denied states.
- Permission ở UI chỉ cải thiện UX; API vẫn authorize.
- Form validate client và server; server error map theo stable code.
- Accessibility: keyboard, focus, label, contrast, reduced motion; test critical flow.
- Image dùng Cloudinary versioned responsive URL/component; không proxy byte qua Next/Spring.
- ESLint/Prettier/typecheck/Vitest chạy CI.

## 9. CI required checks

Mọi PR:

1. commit/PR naming;
2. format/lint;
3. Java compile + unit test + coverage;
4. Next.js typecheck + unit/component test + coverage;
5. architecture test;
6. OpenAPI lint + breaking check;
7. affected integration/contract tests;
8. SAST;
9. dependency/license scan;
10. secret scan;
11. IaC/container scan nếu affected;
12. build immutable artifact;
13. CODEOWNERS/approval policy.

Nightly/main:

- full integration/E2E;
- DAST/fuzz;
- dependency deep scan;
- load smoke;
- mutation test cho domain critical theo lịch;
- backup/restore smoke theo lịch riêng.

Test thất bại phải sửa root cause. Cấm disable/skip/quarantine mà không có ticket, owner và expiry.

## 10. Unit-test rules

- Unit test không network, filesystem thật, MongoDB, Redis, Cloudinary hoặc real clock.
- Không `sleep`; dùng fake clock/scheduler.
- Tên test mô tả `given_when_then` hoặc sentence behavior.
- Arrange/Act/Assert rõ; một behavior chính/test.
- Không test private method; test public behavior.
- Mock chỉ boundary/port; không mock value object/aggregate đang test.
- Không over-specify call order trừ khi order là contract.
- Fixture deterministic; không dùng random không seed.
- Test production code cùng commit.
- Coverage là guardrail, không thay review; mức cụ thể ở `14_UNIT_TEST_PLAN.md`.

## 11. API, MongoDB và migration rules

### API

- OpenAPI là source of truth.
- Breaking change cần version/deprecation plan và consumer telemetry.
- Command tài chính cần `Idempotency-Key`.
- Error dùng stable problem code; không lộ stack/query.
- Cursor ký/versioned; page limit cứng.

### MongoDB

- Collection thuộc một module; module khác dùng contract/event.
- Query mới phải có access pattern, projection, index và `explain` nếu hot path.
- Cấm unbounded array, deep `$skip`, unanchored regex và client-supplied operator.
- Financial transaction dùng majority/snapshot concern và retry có idempotency.
- Redis không là source of truth.

### Migration

```text
expand → deploy compatible code → backfill → switch read/write → observe → contract
```

- Migration idempotent, resumable, có batch/rate limit và metric.
- Không rename/drop field/index cùng commit consumer switch.
- Không chạy backfill lớn trong application startup.
- Destructive migration cần backup/restore evidence và 2 approvals.

## 12. Security rules

### 12.1. Secret

- Secret chỉ trong managed secret store/CI OIDC.
- Cấm secret trong Git, `.env` commit, image, log, screenshot, PR/comment.
- Local dùng `.env.example` không giá trị thật.
- Phát hiện secret: dừng, revoke/rotate trước, sau đó purge theo incident procedure; xóa commit không đủ.
- CI/deploy ưu tiên short-lived identity, không long-lived cloud key.

### 12.2. Dependency và supply chain

- Dependency pin qua lockfile/version catalog; không floating version.
- Package mới cần owner, license, maintenance/security review và lý do.
- Không chạy install script không tin cậy ở môi trường có secret.
- Build tạo SBOM, provenance; container ký và pin digest.
- High/critical CVE chặn merge trừ exception time-bound được Security duyệt.

### 12.3. Data

- Phân loại public/internal/confidential/restricted.
- Thu thập PII/risk signal tối thiểu; retention/TTL rõ.
- Log/metric/trace/audit dùng masking/pseudonym.
- Test fixture synthetic; production export chỉ qua quy trình approved/audited.
- Bank destination/evidence dùng encryption và private delivery.

### 12.4. Auth và quyền

- Deny-by-default.
- RBAC + Team ownership/permission; không tin role/teamId client.
- Admin có MFA/re-auth/reason/audit cho thao tác nhạy cảm.
- Không tạo hidden admin endpoint hoặc debug bypass.
- Authorization cache gắn version và revoke.

### 12.5. Monetization

- Ledger posted immutable; sửa bằng compensating entry.
- Topup automation/manual dùng unique provider reference + atomic state transition.
- Donation chỉ XU nội bộ; không QR/provider.
- Withdrawal reserve trước, fee server-side, settle/release một lần.
- Mọi thay đổi formula/threshold/config cần 2 approvals, property test và audit.

## 13. Feature flag và rollout

- Flag có owner, mục đích, default, expiry và removal ticket.
- Security/authorization invariant không được “tắt” bằng flag.
- Monetization flag default off production cho đến gate.
- Rollout theo internal → canary → percentage → full.
- Metric/auto-halt và rollback/kill switch phải được test trước bật.
- Flag cũ phải xóa; không giữ permanent branching trong code.

## 14. Release và tag

- Semantic Versioning cho public API/artifact.
- Release candidate: `v1.0.0-rc.1`; production: `v1.0.0`.
- Tag production annotated + signed, trỏ đúng commit đã build.
- Artifact build một lần, promote cùng digest qua môi trường; không rebuild per environment.
- Release notes gồm feature, breaking/deprecated, migration, security, flag, known issue và rollback.
- Không deploy Friday/ngoài support window cho thay đổi rủi ro cao nếu không có phê duyệt/on-call.

## 15. Hotfix và incident

1. Mở incident, chỉ định commander/severity.
2. Nếu cần, dùng kill switch trước thay code.
3. Branch `hotfix/<incident>-<slug>` từ production commit/tag.
4. Thay đổi nhỏ nhất, có regression test chứng minh lỗi.
5. Ít nhất một incident approver; security/ledger hotfix cần domain owner nếu có thể.
6. Deploy canary, theo dõi invariant.
7. Merge về `main`, tag mới, sau đó back-merge/cherry-pick reviewed fix về `dev`.
8. Review thứ hai và postmortem/follow-up trong 24–72 giờ.

Không commit evidence nhạy cảm vào public/normal repository. Security incident dùng kênh hạn chế và private advisory.

## 16. Không được làm

- Direct push/force push `main` hoặc `dev`.
- Commit secret, dump, PII, media production hoặc access token.
- `git reset --hard`/rewrite shared history để che sự cố.
- Disable test/security gate để merge.
- Approve PR của chính mình.
- Merge code chưa build/test.
- Update trực tiếp ledger/topup credited/withdrawal paid bằng script không qua reviewed runbook.
- Dùng Admin để bypass business invariant.
- Gọi provider trong MongoDB transaction.
- Dùng `latest` image/dependency floating.
- Log raw auth header, cookie, QR reference đầy đủ hoặc bank destination.

## 17. Exception process

Exception MUST có:

- rule bị ngoại lệ;
- business/incident reason;
- risk và compensating control;
- owner;
- start/expiry tối đa 30 ngày;
- approver phù hợp;
- ticket khắc phục;
- audit evidence.

Exception hết hạn tự chặn merge/deploy nếu chưa gia hạn chính thức.
