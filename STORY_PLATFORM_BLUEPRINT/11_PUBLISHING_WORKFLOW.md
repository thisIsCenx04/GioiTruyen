# 11. Luồng nghiệp vụ Xuất bản truyện

## 1. Mục tiêu và phạm vi

Tài liệu này đặc tả từ lúc một thành viên Team tạo truyện đến khi nội dung được công khai, cập nhật search/cache và gửi thông báo. Luồng áp dụng cho story metadata và chapter content.

Nguyên tắc bắt buộc:

- Không có Creator hoạt động độc lập.
- Chỉ Team Owner hoặc Team Member `active` có permission tương ứng mới được thao tác.
- Revision gửi duyệt là immutable; chỉnh sửa tiếp theo tạo revision mới.
- State transition, audit và outbox được commit nguyên tử trong MongoDB transaction.
- Search, cache invalidation và notification là eventual, không chặn transaction publish.

## 2. Actor và quyền

| Actor | Quyền |
|---|---|
| Team Owner | Toàn quyền nội dung Team; add/remove member và cấp permission |
| Team Member | Theo permission: `story:create`, `story:edit`, `story:submit`, `story:publish` |
| Moderator | Review/approve/request changes/reject/suspend; không sửa nội dung thay Team |
| Admin | Có toàn bộ quyền Moderator và override khẩn cấp có reason/audit |
| Scheduler Worker | Publish revision đã approved đến hạn; dùng service identity |
| Outbox Workers | Search/read model/cache/notification; không thay đổi quyết định moderation |

`analytics:read` và `finance:request` độc lập với quyền xuất bản. Revoke membership/permission có hiệu lực ngay trên source of truth; Redis authorization cache gắn `membershipVersion`.

## 3. Điều kiện tiên quyết

Trước khi tạo story:

1. user ở trạng thái active và email đã xác minh;
2. Team ở trạng thái active;
3. membership active;
4. có `story:create`;
5. Team không bị publishing hold/strike vượt ngưỡng;
6. quota draft/media chưa vượt giới hạn.

Trước submit:

- story có title, synopsis, type, language, taxonomy hợp lệ;
- cover đã qua Cloudinary validation/moderation nếu cover bắt buộc;
- truyện dịch có metadata/bằng chứng bản quyền theo policy;
- ít nhất một chapter hợp lệ nếu product yêu cầu;
- không còn upload/pending scan bắt buộc;
- revision checksum được tạo server-side.

## 4. Aggregate và collection

```text
stories
  ├─ currentRevision → story_revisions (immutable)
  ├─ teamId
  ├─ workflowStatus
  └─ version

chapters
  ├─ currentRevision → chapter_revisions (immutable)
  ├─ storyId + teamId
  ├─ workflowStatus
  ├─ scheduledAt?
  └─ version

moderation_reviews
publishing_schedules
audit_logs
outbox_messages
```

Story/chapter document không nhúng danh sách revision hoặc chapter tăng vô hạn. Mọi query quản trị include `teamId`.

## 5. State machine

### 5.1. Story

```text
draft ──submit──> in_review
  ↑                 ├─request_changes──> changes_requested ──edit──> draft
  │                 ├─approve──────────> approved
  │                 └─reject/archive───> archived
  │
approved ──publish now/schedule──> published
published ──suspend──────────────> suspended
suspended ──reinstate────────────> published
published/suspended ──archive────> archived
```

### 5.2. Chapter

```text
draft ──submit──> in_review
  ↑                 ├─request_changes──> changes_requested ──edit──> draft
  │                 ├─approve──────────> approved
  │                 └─reject/archive───> archived
approved ──schedule──> scheduled ──due worker──> published
approved ──publish now─────────────────> published
published ──hide───────────────────────> hidden
hidden ──reinstate─────────────────────> published
```

`approved` được bổ sung rõ trong chapter state để không trộn quyết định duyệt với lịch publish.

### 5.3. Transition table

| From | Action | To | Actor | Điều kiện chính |
|---|---|---|---|---|
| draft/changes_requested | edit | draft | T | `story:edit`, optimistic version |
| draft | submit | in_review | T | `story:submit`, validation pass |
| in_review | request changes | changes_requested | M/A | reason code + note |
| in_review | approve | approved | M/A | đúng revision đang review |
| approved | schedule | scheduled | T | `story:publish`, future time |
| approved | publish | published | T/M/A | policy cho phép, permission/reason |
| scheduled | publish due | published | Worker | lease + revision unchanged |
| published | hide/suspend | hidden/suspended | T/M/A | scope + reason/audit |

`T` là Team Member/Owner có permission và ownership hợp lệ.

## 6. Luồng A — Team Owner thêm người đăng truyện

```text
Owner → POST /teams/{teamId}/members
API → authn + MFA policy → verify Owner + target user
API → create invitation/membership + permissions
API → audit + outbox notification
Member → accept invitation (nếu dùng invite)
Membership → active
```

Business rules:

- unique `(teamId,userId)`;
- không thêm user bị khóa hoặc Team suspended;
- permission phải thuộc allowlist;
- chỉ Owner đổi `story:publish`/`finance:request`;
- không xóa/demote owner cuối;
- add/revoke permission phát security notification;
- request idempotent, retry không tạo membership trùng.

## 7. Luồng B — Tạo và chỉnh sửa story draft

### API

```http
POST /api/v1/teams/{teamId}/stories
Idempotency-Key: <key>
```

Request không nhận `ownerId`, `createdBy`, `status` hoặc `teamId` trong body; server lấy từ path/context.

```text
1. Authenticate.
2. Load membership từ MongoDB/validated cache version.
3. Authorize story:create.
4. Validate DTO/taxonomy/media.
5. MongoDB transaction:
   - insert stories(status=draft, version=1);
   - insert story_revisions(revisionNo=1, immutable snapshot);
   - insert audit;
   - insert outbox StoryDraftCreated.v1.
6. Return 201 + TeamStory + ETag/version.
```

Update:

```http
PATCH /api/v1/teams/{teamId}/stories/{storyId}
If-Match: "7"
```

Filter update phải chứa `_id`, `teamId`, state editable và `version=7`. Mismatch version trả `412`; sai ownership trả `404/403` theo policy.

Chapter draft làm tương tự qua `POST /teams/{teamId}/stories/{storyId}/chapters`. Nội dung được normalize/sanitize, giới hạn byte/word/node và tạo checksum.

## 8. Luồng C — Submit review

```http
POST /api/v1/teams/{teamId}/stories/{storyId}/submit
Idempotency-Key: <key>
```

```text
Member
  → authorize membership + story:submit + ownership
  → validate completeness/policy prerequisites
  → freeze submittedRevision
  → automated checks
  → transaction: draft → in_review
                + moderation_review
                + audit
                + outbox
  → 202 reviewId/status
```

Automated checks:

- schema/required field/taxonomy;
- sanitized content và unsafe link/QR policy;
- plagiarism/copyright signal (không tự kết luận nếu chưa đủ tin cậy);
- malware/media moderation state;
- duplicate/spam/rate/quota;
- age rating/content warning.

Failure xác định trả `422` với field/rule code. Check bất đồng bộ có thể để review ở `automated_check_pending`; quá timeout chuyển manual queue, không tự publish.

Sau submit, Team có thể soạn revision mới nhưng revision đang review không đổi. Muốn rút submit dùng command riêng có transition/audit, chỉ trước khi reviewer quyết định.

## 9. Luồng D — Moderator/Admin review

```text
GET /moderation/cases?type=publishing&state=open
PATCH /moderation/cases/{id}        # claim bằng optimistic version/lease
POST /moderation/cases/{id}/decisions
```

Decision:

- `approve`: revision đạt policy, chuyển target `approved`;
- `request_changes`: reason code + note actionable, chuyển `changes_requested`;
- `reject/archive`: vi phạm không thể sửa theo case hiện tại;
- `suspend`: dùng với nội dung đã publish, có severity/reason.

Reviewer không sửa title/chapter thay Team. Quyết định gồm reviewer, policy version, submitted revision/checksum, reason code, note, evidence ref, timestamp. Admin dùng cùng endpoint/permission moderation; override cần flag + lý do.

Race control:

- decision filter gồm review `state=open`, `version` và `submittedRevision`;
- chỉ một decision thắng;
- retry cùng idempotency key trả decision cũ;
- decision cho revision stale bị 409 và case cần refresh.

## 10. Luồng E — Publish ngay hoặc lên lịch

### Publish ngay

```text
POST /teams/{teamId}/chapters/{chapterId}/publish
  → authorize story:publish (hoặc M/A policy)
  → validate approved revision, Team/story active
  → MongoDB transaction:
       approved → published
       set publishedAt/currentPublishedRevision/version
       audit + outbox ChapterPublished.v1
  → return 200 published + propagationStatus=pending
```

### Lên lịch

```text
POST /teams/{teamId}/stories/{storyId}/schedule
body: { publishAt, timeZone, revision }
```

- Server lưu `publishAt` UTC và time zone để hiển thị/audit.
- Chỉ schedule revision đã approved; giới hạn quá khứ/tương lai theo policy.
- Worker claim bằng atomic lease trên `{state:scheduled,publishAt<=now}`.
- Trước commit worker kiểm tra Team/story active, revision không đổi và schedule chưa cancel.
- Nhiều worker/retry chỉ một transition thắng.

Nếu dependency search/notification lỗi, content vẫn `published`; outbox retry và dashboard hiển thị propagation delay. Nếu write source-of-truth lỗi, không trả publish thành công.

## 11. Sau publish

Outbox event tối thiểu:

```json
{
  "eventId": "uuid",
  "eventType": "ChapterPublished",
  "version": 1,
  "occurredAt": "UTC",
  "correlationId": "uuid",
  "payload": {
    "teamId": "uuid",
    "storyId": "uuid",
    "chapterId": "uuid",
    "revision": 8,
    "publishedAt": "UTC"
  }
}
```

Consumer độc lập:

1. Catalog/read-model cập nhật public projection.
2. Atlas Search index/upsert document.
3. Cloudflare purge cache tag hoặc đổi version key.
4. Next.js ISR revalidation.
5. Notification fan-out follower.
6. Analytics ghi mốc bắt đầu tính view/reward.

Mỗi consumer lưu inbox/dedupe theo `eventId`. Event chỉ chứa ID/version cần thiết, không nhúng toàn chapter.

## 12. Edit nội dung đã publish

- Không sửa trực tiếp published revision.
- Team tạo revision mới từ bản hiện tại.
- Minor metadata có thể dùng fast-track policy nếu được cấu hình; content/chapter change phải review lại.
- Đến khi revision mới publish, độc giả vẫn đọc revision cũ.
- Publish revision mới đổi pointer nguyên tử và phát event version mới.
- Rollback nội dung là repoint đến revision sạch đã duyệt hoặc tạo compensating revision; không xóa audit.

## 13. Hide, suspend và unpublish

| Hành động | Actor | Hành vi |
|---|---|---|
| Team hide | T có `story:publish` | ẩn chủ động, reason, có thể reinstate theo policy |
| Moderator suspend | M/A | vi phạm/report, case + reason + notification |
| Emergency takedown | A | ưu tiên cao, re-auth/reason/audit |
| Archive | Owner/M/A | kết thúc vòng đời; không hard delete ledger/audit |

Takedown transaction cập nhật visibility + audit + outbox. Cache/search phải ưu tiên event hide/suspend; public API vẫn kiểm tra source/read-model visibility và không dựa riêng vào CDN.

## 14. API response và lỗi

| HTTP | Khi dùng |
|---:|---|
| 201 | tạo draft/revision |
| 202 | submit/check async |
| 400 | request sai schema |
| 401 | chưa xác thực |
| 403 | biết tài nguyên nhưng thiếu permission |
| 404 | không tồn tại hoặc policy chống enumeration |
| 409 | transition/idempotency/revision review conflict |
| 412 | `If-Match` stale |
| 422 | business validation/policy prerequisite |
| 429 | quota/rate limit |

Problem response có stable `code`, `title`, `status`, `traceId`, field errors và transition hiện tại nếu an toàn.

## 15. Audit và metric

Audit: membership/permission change, draft create, revision create, submit/withdraw submit, review claim/decision, schedule/cancel, publish/hide/suspend/reinstate và Admin override.

Metric:

- submit → first review và submit → final decision;
- approval/change/reject rate theo policy version;
- schedule delay và publish propagation lag;
- outbox retry/DLQ;
- permission denied/BOLA signal;
- revision conflict rate;
- stale public cache incident.

## 16. Acceptance criteria

- User không thuộc Team không thể tạo/đọc draft/sửa/submit/publish.
- Member chỉ làm đúng permission; revoke có hiệu lực trong giới hạn đã định.
- Revision đang review/published không bị mutate.
- Mọi transition không hợp lệ bị từ chối và không có side effect.
- Concurrent submit/decision/schedule/publish chỉ tạo một kết quả.
- Publish source-of-truth thành công dù consumer tạm lỗi; consumer tự retry/dedupe.
- Suspend/hide loại nội dung khỏi public path trong SLO.
- Mọi hành động nhạy cảm truy vết được actor, Team, revision, reason và correlation ID.

