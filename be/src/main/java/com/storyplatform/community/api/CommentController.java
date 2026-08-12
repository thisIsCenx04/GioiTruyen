package com.storyplatform.community.api;

import com.storyplatform.shared.api.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reader comments on a story or a chapter.
 *
 * <p>Deleting keeps the row and flips its status: a reply chain with a hole in
 * the middle reads as broken, and moderation needs to see what was said.
 */
@RestController
@RequestMapping("/comments")
public class CommentController {

    private static final int MAX_BODY = 5000;
    private static final int MAX_PAGE_SIZE = 50;

    private final JdbcClient jdbc;

    public CommentController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Author(String id, String displayName, String avatarMediaId) {
    }

    public record CommunityComment(
            String id,
            String targetType,
            String targetId,
            String parentId,
            String rootId,
            int depth,
            Author author,
            String body,
            String status,
            int version,
            String createdAt,
            String updatedAt
    ) {
    }

    public record CommentPage(List<CommunityComment> items, String nextCursor, boolean hasMore) {
    }

    public record CreateCommentRequest(String targetType, String targetId, String parentId, String body) {
    }

    public record UpdateCommentRequest(String body) {
    }

    // avatar_url lives on users; user_profiles carries bio and cover only.
    private static final String SELECT = """
            SELECT c.id, c.story_id, c.chapter_id, c.parent_id, c.content, c.status,
                   c.created_at, c.updated_at,
                   u.id AS author_id, u.display_name, u.email, u.avatar_url
            FROM comments c
            JOIN users u ON u.id = c.user_id
            """;

    /** Public: anyone may read the discussion under a story. */
    @GetMapping
    @Transactional(readOnly = true)
    public CommentPage list(
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        String type = normaliseTarget(targetType);
        int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);

        // One extra row answers "is there more" without a second count query.
        String column = "CHAPTER".equals(type) ? "c.chapter_id" : "c.story_id";
        List<CommunityComment> rows = jdbc.sql(SELECT + """
                        WHERE %s = ?
                          AND c.status <> 'DELETED'
                          AND (? IS NULL OR c.created_at < ?)
                        ORDER BY c.created_at DESC
                        LIMIT ?
                        """.formatted(column))
                .params(targetId, cursor, cursor, size + 1)
                .query((rs, rowNum) -> toComment(rs))
                .list();

        boolean hasMore = rows.size() > size;
        List<CommunityComment> items = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore ? items.getLast().createdAt() : null;
        return new CommentPage(items, nextCursor, hasMore);
    }

    @PostMapping
    @Transactional
    public CommunityComment create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateCommentRequest request
    ) {
        String userId = requireUser(jwt);
        String body = requireBody(request.body());
        String type = normaliseTarget(request.targetType());

        String storyId;
        String chapterId = null;
        if ("CHAPTER".equals(type)) {
            chapterId = request.targetId();
            storyId = jdbc.sql("SELECT story_id FROM chapters WHERE id = ?")
                    .param(chapterId)
                    .query(String.class)
                    .optional()
                    .orElseThrow(() -> notFound("Không tìm thấy chương này."));
        } else {
            storyId = request.targetId();
            boolean exists = jdbc.sql("SELECT COUNT(*) FROM stories WHERE id = ?")
                    .param(storyId)
                    .query(Long.class)
                    .single() > 0;
            if (!exists) {
                throw notFound("Không tìm thấy truyện này.");
            }
        }

        // A reply must belong to the same discussion; otherwise a crafted
        // parentId could graft a thread onto an unrelated story.
        String parentId = request.parentId() == null || request.parentId().isBlank()
                ? null : request.parentId().trim();
        if (parentId != null) {
            String parentStory = jdbc.sql("SELECT story_id FROM comments WHERE id = ?")
                    .param(parentId)
                    .query(String.class)
                    .optional()
                    .orElseThrow(() -> notFound("Không tìm thấy bình luận gốc."));
            if (!parentStory.equals(storyId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "comment.parent_mismatch",
                        "Parent belongs to another story",
                        "Bình luận gốc không thuộc truyện này.");
            }
        }

        String id = UUID.randomUUID().toString();
        jdbc.sql("""
                        INSERT INTO comments (id, user_id, story_id, chapter_id, parent_id, content, status)
                        VALUES (?, ?, ?, ?, ?, ?, 'VISIBLE')
                        """)
                .params(id, userId, storyId, chapterId, parentId, body)
                .update();

        return findById(id);
    }

    @PatchMapping("/{commentId}")
    @Transactional
    public CommunityComment update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String commentId,
            @RequestBody UpdateCommentRequest request
    ) {
        String userId = requireUser(jwt);
        String body = requireBody(request.body());
        requireOwner(commentId, userId);

        jdbc.sql("UPDATE comments SET content = ?, updated_at = NOW() WHERE id = ?")
                .params(body, commentId)
                .update();
        return findById(commentId);
    }

    @DeleteMapping("/{commentId}")
    @Transactional
    public CommunityComment remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String commentId
    ) {
        String userId = requireUser(jwt);
        requireOwner(commentId, userId);

        jdbc.sql("UPDATE comments SET status = 'DELETED', updated_at = NOW() WHERE id = ?")
                .param(commentId)
                .update();
        return findById(commentId);
    }

    private void requireOwner(String commentId, String userId) {
        String owner = jdbc.sql("SELECT user_id FROM comments WHERE id = ?")
                .param(commentId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> notFound("Không tìm thấy bình luận này."));

        if (owner.equals(userId)) {
            return;
        }
        boolean admin = jdbc.sql("SELECT COUNT(*) FROM users WHERE id = ? AND role = 'ADMIN'")
                .param(userId)
                .query(Long.class)
                .single() > 0;
        if (!admin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "comment.not_owner",
                    "Not the author", "Bạn chỉ có thể sửa hoặc xóa bình luận của mình.");
        }
    }

    private CommunityComment findById(String id) {
        return jdbc.sql(SELECT + " WHERE c.id = ?")
                .param(id)
                .query((rs, rowNum) -> toComment(rs))
                .single();
    }

    private static CommunityComment toComment(ResultSet rs) throws SQLException {
        String chapterId = rs.getString("chapter_id");
        boolean onChapter = chapterId != null;
        String parentId = rs.getString("parent_id");
        String displayName = rs.getString("display_name");
        if (displayName == null || displayName.isBlank()) {
            // Fall back to the local part of the email rather than showing a
            // blank byline; the full address is not the reader's to publish.
            String email = rs.getString("email");
            displayName = email == null ? "Độc giả" : email.split("@")[0];
        }

        return new CommunityComment(
                rs.getString("id"),
                onChapter ? "CHAPTER" : "STORY",
                onChapter ? chapterId : rs.getString("story_id"),
                parentId,
                parentId == null ? rs.getString("id") : parentId,
                parentId == null ? 0 : 1,
                new Author(rs.getString("author_id"), displayName, rs.getString("avatar_url")),
                "DELETED".equals(rs.getString("status")) ? "" : rs.getString("content"),
                rs.getString("status"),
                1,
                String.valueOf(rs.getTimestamp("created_at")),
                String.valueOf(rs.getTimestamp("updated_at"))
        );
    }

    private static String normaliseTarget(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!"STORY".equals(type) && !"CHAPTER".equals(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "comment.invalid_target",
                    "Unknown target type", "Chỉ hỗ trợ bình luận cho truyện hoặc chương.");
        }
        return type;
    }

    private static String requireBody(String value) {
        String body = value == null ? "" : value.trim();
        if (body.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "comment.empty",
                    "Empty comment", "Nội dung bình luận không được để trống.");
        }
        if (body.length() > MAX_BODY) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "comment.too_long",
                    "Comment too long",
                    "Bình luận tối đa %d ký tự.".formatted(MAX_BODY));
        }
        return body;
    }

    private static String requireUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Authentication required", "Vui lòng đăng nhập để bình luận.");
        }
        return jwt.getSubject();
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "comment.not_found", "Not found", message);
    }
}
