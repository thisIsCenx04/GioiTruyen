package com.storyplatform.system.api;

import com.storyplatform.shared.api.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reader's inbox. Messages are written by other parts of the platform - a
 * top-up being approved or rejected, for instance - and read here.
 *
 * <p>Paging is by creation time rather than offset so that a message arriving
 * mid-scroll cannot shift the page under the reader.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 50;

    private final JdbcClient jdbc;

    public NotificationController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record NotificationItem(
            String id,
            String type,
            String title,
            String body,
            Map<String, String> data,
            String readAt,
            String createdAt
    ) {
    }

    public record NotificationPage(
            List<NotificationItem> items,
            String nextCursor,
            boolean hasMore,
            long unreadCount
    ) {
    }

    public record ReadAllResult(String readBefore, long unreadCount) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public NotificationPage list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        String userId = requireUser(jwt);
        int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);

        // One extra row tells us whether another page exists without a count.
        List<NotificationItem> rows = jdbc.sql("""
                        SELECT id, type, title, message, target_type, target_id, target_url,
                               is_read, created_at
                        FROM notifications
                        WHERE user_id = ?
                          AND (? IS NULL OR created_at < ?)
                        ORDER BY created_at DESC, id DESC
                        LIMIT ?
                        """)
                .params(userId, cursor, cursor, size + 1)
                .query((rs, rowNum) -> new NotificationItem(
                        rs.getString("id"),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("message"),
                        data(rs.getString("target_type"), rs.getString("target_id"),
                                rs.getString("target_url")),
                        rs.getBoolean("is_read") ? String.valueOf(rs.getTimestamp("created_at")) : null,
                        String.valueOf(rs.getTimestamp("created_at"))))
                .list();

        boolean hasMore = rows.size() > size;
        List<NotificationItem> items = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore ? items.getLast().createdAt() : null;

        return new NotificationPage(items, nextCursor, hasMore, unreadCount(userId));
    }

    @PostMapping("/{notificationId}/read")
    @Transactional
    public NotificationItem markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable String notificationId) {
        String userId = requireUser(jwt);
        int updated = jdbc.sql("UPDATE notifications SET is_read = TRUE WHERE id = ? AND user_id = ?")
                .params(notificationId, userId)
                .update();
        if (updated == 0) {
            // Either it belongs to somebody else or it is already read; both look
            // the same to the caller so neither leaks another account's inbox.
            boolean exists = jdbc.sql("SELECT COUNT(*) FROM notifications WHERE id = ? AND user_id = ?")
                    .params(notificationId, userId)
                    .query(Long.class)
                    .single() > 0;
            if (!exists) {
                throw new ApiException(HttpStatus.NOT_FOUND, "notification.not_found",
                        "Notification not found", "Không tìm thấy thông báo này.");
            }
        }
        return list(jwt, null, MAX_PAGE_SIZE).items().stream()
                .filter(item -> item.id().equals(notificationId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "notification.not_found",
                        "Notification not found", "Không tìm thấy thông báo này."));
    }

    @PostMapping("/read-all")
    @Transactional
    public ReadAllResult markAllRead(@AuthenticationPrincipal Jwt jwt) {
        String userId = requireUser(jwt);
        jdbc.sql("UPDATE notifications SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE")
                .param(userId)
                .update();
        String now = jdbc.sql("SELECT NOW()").query(String.class).single();
        return new ReadAllResult(now, unreadCount(userId));
    }

    private long unreadCount(String userId) {
        return jdbc.sql("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE")
                .param(userId)
                .query(Long.class)
                .single();
    }

    /** The client's {@code data} bag carries whatever the message links to. */
    private static Map<String, String> data(String targetType, String targetId, String targetUrl) {
        Map<String, String> data = new java.util.LinkedHashMap<>();
        if (targetType != null) data.put("targetType", targetType);
        if (targetId != null) data.put("targetId", targetId);
        if (targetUrl != null) data.put("targetUrl", targetUrl);
        return data;
    }

    private static String requireUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Authentication required", "Vui lòng đăng nhập để xem thông báo.");
        }
        return jwt.getSubject();
    }
}
