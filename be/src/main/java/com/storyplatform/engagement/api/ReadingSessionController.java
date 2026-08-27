package com.storyplatform.engagement.api;

import com.storyplatform.shared.api.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles reading sessions, heartbeats, chapter completion, and reading progress.
 */
@RestController
public class ReadingSessionController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ReadingSessionController.class);

    /** Stories per page of reading history. */
    private static final int HISTORY_PAGE_SIZE = 20;

    private final NamedParameterJdbcTemplate jdbc;
    private final com.storyplatform.engagement.application.StoryViewRecorder viewRecorder;
    /**
     * Phiên đọc đang mở, giữ trong bộ nhớ tiến trình.
     *
     * <p>Bản đồ này chỉ co lại khi ai đó gọi {@code /complete}. Kẻ tấn công thì
     * không bao giờ gọi, mà {@code POST /reading-sessions} lại mở công khai — nên
     * nó từng là một đường làm cạn bộ nhớ: mỗi lần gọi thêm một dòng, không
     * gì dọn, máy chủ cứ thế phình cho đến khi hết bộ nhớ.
     */
    private final Map<String, SessionRecord> activeSessions = new ConcurrentHashMap<>();

    /** Phiên cũ hơn mức này coi như người đọc đã đi, dù không ai báo kết thúc. */
    private static final Duration SESSION_TTL = Duration.ofHours(3);

    /** Trần cứng, phòng khi quét theo tuổi vẫn không kịp. */
    private static final int MAX_ACTIVE_SESSIONS = 50_000;

    /** Khoảng cách tối thiểu giữa hai lần quét, để không quét trên mọi lần gọi. */
    private static final long SWEEP_INTERVAL_MS = 60_000L;

    private volatile long lastSweepAt = System.currentTimeMillis();

    /**
     * Dọn phiên quá hạn. Gọi từ chỗ tạo phiên, không cần bộ hẹn giờ riêng:
     * bản đồ chỉ lớn lên ở đúng chỗ đó.
     */
    private void sweepStaleSessions() {
        long now = System.currentTimeMillis();
        boolean overCap = activeSessions.size() >= MAX_ACTIVE_SESSIONS;
        if (!overCap && now - lastSweepAt < SWEEP_INTERVAL_MS) {
            return;
        }
        lastSweepAt = now;
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        activeSessions.values().removeIf(session -> session.createdAt().isBefore(cutoff));

        // Vẫn chạm trần sau khi dọn theo tuổi: bỏ hết để máy chủ sống, thay vì
        // giữ một đống phiên giả rồi chết vì hết bộ nhớ. Mất phiên chỉ làm
        // nhịp tim mất hiệu lực, không mất dữ liệu nào đã ghi xuống CSDL.
        if (activeSessions.size() >= MAX_ACTIVE_SESSIONS) {
            log.warn("Phiên đọc chạm trần {} sau khi dọn theo tuổi — xóa toàn bộ",
                    MAX_ACTIVE_SESSIONS);
            activeSessions.clear();
        }
    }

    public ReadingSessionController(
            NamedParameterJdbcTemplate jdbc,
            com.storyplatform.engagement.application.StoryViewRecorder viewRecorder
    ) {
        this.jdbc = jdbc;
        this.viewRecorder = viewRecorder;
    }

    public record StartSessionRequest(String anonymousId, String chapterId, String storyId) {}
    public record ReadingSessionGrant(String sessionId, String sessionToken, int heartbeatIntervalSeconds) {}
    public record HeartbeatItem(int activeSeconds, String occurredAt, double position, int sequence) {}
    public record HeartbeatRequest(String batchId, List<HeartbeatItem> heartbeats) {}
    public record HeartbeatResponse(int nextSequence) {}
    public record CompleteRequest(String completionId, int finalSequence, String occurredAt, double position) {}
    public record StatusResponse(String status) {}
    public record ReadingProgressRequest(String chapterId, String deviceUpdatedAt, double position) {}
    public record ReadingHistoryResponse(List<Map<String, Object>> items, String nextCursor) {}

    private record SessionRecord(String sessionId, String sessionToken, String storyId, String chapterId, int lastSequence, Instant createdAt) {}

    @PostMapping("/reading-sessions")
    public ReadingSessionGrant startSession(
            @RequestBody StartSessionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        sweepStaleSessions();

        String sessionId = UUID.randomUUID().toString();
        String sessionToken = UUID.randomUUID().toString();
        
        activeSessions.put(sessionId, new SessionRecord(
                sessionId,
                sessionToken,
                request.storyId(),
                request.chapterId(),
                1,
                Instant.now()
        ));

        // The view is counted here, and only here.
        //
        // This used to raise view_count_cache with a bare +1 and no dedupe, on
        // top of the counting PublicCatalogController already did when the
        // chapter itself was fetched - so one open counted twice, and a refresh
        // counted twice again. Routing it through StoryViewRecorder gives it the
        // same 30-minute per-reader window every other view goes through, and
        // keeps story_views and story_daily_stats in step with the cache.
        //
        // The client only opens a session once the reader has stayed three
        // seconds, so a bounce off the page never reaches this.
        if (request.storyId() != null && !request.storyId().isBlank()
                && request.chapterId() != null && !request.chapterId().isBlank()) {
            viewRecorder.recordChapterView(
                    request.storyId(),
                    request.chapterId(),
                    jwt == null ? null : jwt.getSubject(),
                    clientIp(httpRequest));
        }

        return new ReadingSessionGrant(sessionId, sessionToken, 15);
    }

    @PostMapping("/reading-sessions/{sessionId}/heartbeats")
    public HeartbeatResponse heartbeat(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @RequestBody HeartbeatRequest request
    ) {
        int maxSeq = 1;
        if (request.heartbeats() != null && !request.heartbeats().isEmpty()) {
            for (HeartbeatItem item : request.heartbeats()) {
                if (item.sequence() >= maxSeq) {
                    maxSeq = item.sequence() + 1;
                }
            }
        }

        SessionRecord current = requireSession(sessionId, sessionToken);
        if (current != null) {
            activeSessions.put(sessionId, new SessionRecord(
                    current.sessionId(),
                    current.sessionToken(),
                    current.storyId(),
                    current.chapterId(),
                    maxSeq,
                    current.createdAt()
            ));
        }

        return new HeartbeatResponse(maxSeq);
    }

    @PostMapping("/reading-sessions/{sessionId}/complete")
    public StatusResponse complete(
            @PathVariable String sessionId,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @RequestBody CompleteRequest request
    ) {
        requireSession(sessionId, sessionToken);
        activeSessions.remove(sessionId);
        return new StatusResponse("COMPLETED");
    }

    /**
     * Phiên này có đúng là của người đang gọi không.
     *
     * <p>{@code X-Session-Token} được cấp lúc mở phiên và trình duyệt vẫn gửi
     * kèm — nhưng máy chủ trước đây đọc rồi bỏ, nên bất kỳ ai đoán đúng
     * sessionId đều đóng được phiên của người khác. Một tham số sinh ra để
     * chứng thực mà không ai kiểm thì chỉ là trấn an.
     *
     * <p>Phiên không còn trong bộ nhớ (hết hạn, hoặc máy chủ vừa khởi động lại)
     * thì trả về null chứ không ném lỗi: nhịp tim muộn không phải là tấn công, và
     * làm vỡ trang đọc vì chuyện đó là vô lý.
     */
    private SessionRecord requireSession(String sessionId, String sessionToken) {
        SessionRecord current = activeSessions.get(sessionId);
        if (current == null) {
            return null;
        }
        if (sessionToken == null || !current.sessionToken().equals(sessionToken)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "reading_session.forbidden",
                    "Session token mismatch", "Phiên đọc này không thuộc về bạn.");
        }
        return current;
    }

    /**
     * Records where the reader has got to in a story.
     *
     * <p>One row per reader and story, overwritten in place: the history screen
     * wants the story once, not once per heartbeat. A measurement older than the
     * one already stored is discarded, so a second device catching up on a stale
     * queue cannot drag the reader backwards.
     */
    @PutMapping("/me/reading-progress/{storyId}")
    public StatusResponse saveProgress(
            @PathVariable String storyId,
            @RequestBody ReadingProgressRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = requireUser(jwt);
        Instant deviceUpdatedAt = parseInstant(request.deviceUpdatedAt());
        // The reader may be on the story page with no chapter open yet.
        String chapterId = request.chapterId() == null || request.chapterId().isBlank()
                ? null : request.chapterId().trim();

        // A HashMap rather than Map.of: chapter_id and device_updated_at are
        // both legitimately null, which Map.of refuses to hold.
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("userId", userId);
        params.put("storyId", storyId);
        params.put("chapterId", chapterId);
        params.put("position", clampPosition(request.position()));
        params.put("deviceUpdatedAt", deviceUpdatedAt == null
                ? null : java.sql.Timestamp.from(deviceUpdatedAt));
        params.put("now", java.sql.Timestamp.from(Instant.now()));

        int written = jdbc.update(
                """
                        INSERT INTO reading_progress
                            (user_id, story_id, chapter_id, position, device_updated_at, updated_at)
                        VALUES (:userId, :storyId, :chapterId, :position, :deviceUpdatedAt, :now)
                        ON DUPLICATE KEY UPDATE
                            chapter_id = IF(
                                reading_progress.device_updated_at IS NULL
                                    OR VALUES(device_updated_at) IS NULL
                                    OR VALUES(device_updated_at) >= reading_progress.device_updated_at,
                                VALUES(chapter_id), reading_progress.chapter_id),
                            position = IF(
                                reading_progress.device_updated_at IS NULL
                                    OR VALUES(device_updated_at) IS NULL
                                    OR VALUES(device_updated_at) >= reading_progress.device_updated_at,
                                VALUES(position), reading_progress.position),
                            device_updated_at = GREATEST(
                                COALESCE(reading_progress.device_updated_at, VALUES(device_updated_at)),
                                COALESCE(VALUES(device_updated_at), reading_progress.device_updated_at)),
                            updated_at = VALUES(updated_at)
                        """,
                params
        );
        return new StatusResponse(written > 0 ? "SAVED" : "UNCHANGED");
    }

    /**
     * The reader's own history, most recently read first.
     *
     * <p>The cursor is the previous page's last {@code lastReadAt}, so paging
     * walks strictly backwards in time instead of by offset, which would skip
     * or repeat rows as the reader keeps reading.
     */
    @GetMapping("/me/reading-history")
    public ReadingHistoryResponse readingHistory(
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String userId = requireUser(jwt);
        Instant before = parseInstant(cursor);

        Map<String, Object> params = new java.util.HashMap<>();
        params.put("userId", userId);
        params.put("before", before == null ? null : java.sql.Timestamp.from(before));
        params.put("limit", HISTORY_PAGE_SIZE + 1);

        List<Map<String, Object>> rows = jdbc.query(
                """
                        SELECT rp.story_id, rp.chapter_id, rp.position, rp.updated_at,
                               s.title AS story_title, s.slug AS story_slug, s.cover_url,
                               c.title AS chapter_title, c.chapter_number
                        FROM reading_progress rp
                        JOIN stories s ON s.id = rp.story_id
                        LEFT JOIN chapters c ON c.id = rp.chapter_id
                        WHERE rp.user_id = :userId
                          AND (:before IS NULL OR rp.updated_at < :before)
                        ORDER BY rp.updated_at DESC
                        LIMIT :limit
                        """,
                params,
                (rs, rowNum) -> {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("storyId", rs.getString("story_id"));
                    item.put("storySlug", rs.getString("story_slug"));
                    item.put("storyTitle", rs.getString("story_title"));
                    item.put("coverUrl", rs.getString("cover_url"));
                    item.put("chapterId", rs.getString("chapter_id"));
                    item.put("chapterTitle", rs.getString("chapter_title"));
                    int number = rs.getInt("chapter_number");
                    item.put("chapterNumber", rs.wasNull() ? null : number);
                    item.put("position", rs.getBigDecimal("position"));
                    item.put("lastReadAt", rs.getTimestamp("updated_at").toInstant().toString());
                    return item;
                }
        );

        boolean hasMore = rows.size() > HISTORY_PAGE_SIZE;
        List<Map<String, Object>> items = hasMore ? rows.subList(0, HISTORY_PAGE_SIZE) : rows;
        String nextCursor = hasMore
                ? String.valueOf(items.get(items.size() - 1).get("lastReadAt"))
                : null;
        return new ReadingHistoryResponse(items, nextCursor);
    }

    private static double clampPosition(double position) {
        if (Double.isNaN(position)) {
            return 0;
        }
        return Math.min(100, Math.max(0, position));
    }

    /** A cursor or device clock that cannot be read counts as absent, not as an error. */
    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    /** Xem {@link com.storyplatform.shared.api.ClientIp} về chỗ tiêu đề bị giả. */
    private static String clientIp(jakarta.servlet.http.HttpServletRequest request) {
        return com.storyplatform.shared.api.ClientIp.of(request);
    }

    private static String requireUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Authentication required", "Bạn cần đăng nhập để lưu tiến độ đọc.");
        }
        return jwt.getSubject();
    }
}
