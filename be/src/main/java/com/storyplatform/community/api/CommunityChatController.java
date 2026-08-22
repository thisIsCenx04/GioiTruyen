package com.storyplatform.community.api;

import com.storyplatform.shared.api.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The community chat rooms shown on the home page and in the Zhihu corner.
 *
 * <p>The panel used to be entirely make-believe: the browser seeded three
 * invented users into localStorage, showed them under a "live" badge, and
 * posted new messages to a route that did not exist, so every reader saw their
 * own private copy of a conversation that never happened. The tables were
 * already in the schema; this serves them.
 *
 * <p>Names come from the users table on read rather than being stored with the
 * message, so a reader who renames themselves is not left with an old name
 * attached to everything they ever said.
 */
@RestController
@RequestMapping("/community/messages")
public class CommunityChatController {

    /** Long enough for a paragraph, short enough that the panel stays a chat. */
    private static final int MAX_BODY = 500;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 30;

    /** Room keys the front end may ask for, mapped to the stored room name. */
    private static final Map<String, String> ROOMS = Map.of(
            "main", "Sảnh chính",
            "zhihu", "Góc Zhihu"
    );

    private final JdbcClient jdbc;

    public CommunityChatController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record ChatMessage(
            String id,
            String userId,
            String userName,
            String userAvatarUrl,
            /** ADMIN, TEAM or USER - decides which badge the name carries. */
            String userRole,
            String content,
            String createdAt
    ) {
    }

    public record PostMessageRequest(String room, String content) {
    }

    /** Public: the conversation is readable without an account. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<ChatMessage> list(
            @RequestParam(defaultValue = "main") String room,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int limit
    ) {
        String roomId = findRoom(normaliseRoom(room));
        if (roomId == null) {
            return List.of();
        }
        // Newest first out of the database so the limit keeps the *latest*
        // messages, then reversed so the panel reads top to bottom.
        List<ChatMessage> newestFirst = jdbc.sql("""
                        SELECT m.id, m.content, m.created_at,
                               u.id AS user_id, u.display_name, u.avatar_url, u.role,
                               EXISTS (SELECT 1 FROM team_members tm
                                        WHERE tm.user_id = u.id AND tm.status = 'ACTIVE') AS in_team
                        FROM community_messages m
                        JOIN users u ON u.id = m.user_id
                        WHERE m.room_id = :roomId AND m.status = 'VISIBLE'
                        ORDER BY m.created_at DESC, m.id DESC
                        LIMIT :limit
                        """)
                .param("roomId", roomId)
                .param("limit", Math.clamp(limit, 1, MAX_PAGE_SIZE))
                .query((rs, rowNum) -> new ChatMessage(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        displayName(rs.getString("display_name")),
                        rs.getString("avatar_url"),
                        badge(rs.getString("role"), rs.getBoolean("in_team")),
                        rs.getString("content"),
                        String.valueOf(rs.getTimestamp("created_at").toInstant())
                ))
                .list();
        return newestFirst.reversed();
    }

    /** Posting needs an account: every message is attributable to a reader. */
    @PostMapping
    @Transactional
    public ChatMessage post(@AuthenticationPrincipal Jwt jwt, @RequestBody PostMessageRequest request) {
        String userId = requireUser(jwt);
        String content = request == null || request.content() == null ? "" : request.content().strip();
        if (content.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "community.empty",
                    "Empty message", "Tin nhắn không được để trống.");
        }
        if (content.length() > MAX_BODY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "community.too_long",
                    "Message too long",
                    "Tin nhắn tối đa " + MAX_BODY + " ký tự (đang có " + content.length() + ").");
        }

        String key = normaliseRoom(request == null ? null : request.room());
        String roomId = findRoom(key);
        if (roomId == null) {
            // The rooms are fixtures of the product, not user data, so the first
            // message in a room creates it instead of failing.
            roomId = UUID.randomUUID().toString();
            jdbc.sql("INSERT INTO community_rooms (id, name, status) VALUES (:id, :name, 'VISIBLE')")
                    .param("id", roomId)
                    .param("name", ROOMS.get(key))
                    .update();
        }

        String id = UUID.randomUUID().toString();
        jdbc.sql("""
                        INSERT INTO community_messages (id, room_id, user_id, content, status, created_at)
                        VALUES (:id, :roomId, :userId, :content, 'VISIBLE', :createdAt)
                        """)
                .param("id", id)
                .param("roomId", roomId)
                .param("userId", userId)
                .param("content", content)
                .param("createdAt", java.sql.Timestamp.from(Instant.now()))
                .update();

        return jdbc.sql("""
                        SELECT m.id, m.content, m.created_at,
                               u.id AS user_id, u.display_name, u.avatar_url, u.role,
                               EXISTS (SELECT 1 FROM team_members tm
                                        WHERE tm.user_id = u.id AND tm.status = 'ACTIVE') AS in_team
                        FROM community_messages m
                        JOIN users u ON u.id = m.user_id
                        WHERE m.id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new ChatMessage(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        displayName(rs.getString("display_name")),
                        rs.getString("avatar_url"),
                        badge(rs.getString("role"), rs.getBoolean("in_team")),
                        rs.getString("content"),
                        String.valueOf(rs.getTimestamp("created_at").toInstant())
                ))
                .single();
    }

    private String findRoom(String key) {
        return jdbc.sql("SELECT id FROM community_rooms WHERE name = :name AND status = 'VISIBLE' LIMIT 1")
                .param("name", ROOMS.get(key))
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private static String normaliseRoom(String room) {
        String key = room == null ? "" : room.trim().toLowerCase(Locale.ROOT);
        return ROOMS.containsKey(key) ? key : "main";
    }

    private static String displayName(String stored) {
        return stored == null || stored.isBlank() ? "Độc giả" : stored;
    }

    /**
     * The badge beside a name.
     *
     * <p>users.role only tells READER from ADMIN, so team membership is read
     * from team_members instead: a publisher is an ordinary reader account that
     * belongs to a team.
     */
    private static String badge(String role, boolean inTeam) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return "ADMIN";
        }
        return inTeam ? "TEAM" : "USER";
    }

    private static String requireUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Authentication required", "Vui lòng đăng nhập để tham gia thảo luận.");
        }
        return jwt.getSubject();
    }
}
