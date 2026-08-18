package com.storyplatform.community.api;

import com.storyplatform.shared.api.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The heart under a comment.
 *
 * <p>Only comments are reactable. A story is followed or favourited instead, and
 * those already have their own endpoints under {@code /stories/{id}}, so an
 * unknown target type is refused rather than silently answered with a zero:
 * a button that always reads "0" looks broken in exactly the same way a missing
 * endpoint does, but takes far longer to diagnose.
 */
@RestController
@RequestMapping("/reactions")
public class ReactionController {

    private final JdbcClient jdbc;

    public ReactionController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code active} is this reader's own like; {@code count} is everyone's. */
    public record ReactionState(boolean active, long count) {
    }

    /** Public: the count is part of the comment, whether or not anyone is signed in. */
    @GetMapping("/{targetType}/{targetId}")
    @Transactional(readOnly = true)
    public ReactionState status(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String targetType,
            @PathVariable String targetId
    ) {
        requireComment(targetType);
        requireCommentExists(targetId);
        return state(targetId, currentUser(jwt));
    }

    @PostMapping("/{targetType}/{targetId}")
    @Transactional
    public ReactionState add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String targetType,
            @PathVariable String targetId
    ) {
        requireComment(targetType);
        requireCommentExists(targetId);
        String userId = requireUser(jwt);

        // A double tap, or the same tap retried, must not count twice; the
        // primary key is (comment_id, user_id), so the second insert is a no-op.
        jdbc.sql("""
                        INSERT IGNORE INTO comment_likes (comment_id, user_id)
                        VALUES (?, ?)
                        """)
                .params(targetId, userId)
                .update();
        return refreshCache(targetId, userId);
    }

    @DeleteMapping("/{targetType}/{targetId}")
    @Transactional
    public ReactionState remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String targetType,
            @PathVariable String targetId
    ) {
        requireComment(targetType);
        requireCommentExists(targetId);
        String userId = requireUser(jwt);

        jdbc.sql("DELETE FROM comment_likes WHERE comment_id = ? AND user_id = ?")
                .params(targetId, userId)
                .update();
        return refreshCache(targetId, userId);
    }

    /**
     * Writes the counter the comment list reads back from the rows that are the
     * truth, rather than adding or subtracting one: a retried insert changes no
     * rows, and a drifted cache corrects itself on the next tap.
     */
    private ReactionState refreshCache(String commentId, String userId) {
        ReactionState state = state(commentId, userId);
        jdbc.sql("UPDATE comments SET like_count_cache = ? WHERE id = ?")
                .params(state.count(), commentId)
                .update();
        return state;
    }

    private ReactionState state(String commentId, String userId) {
        long count = jdbc.sql("SELECT COUNT(*) FROM comment_likes WHERE comment_id = ?")
                .param(commentId)
                .query(Long.class)
                .single();
        boolean active = userId != null && jdbc.sql("""
                        SELECT COUNT(*) FROM comment_likes
                        WHERE comment_id = ? AND user_id = ?
                        """)
                .params(commentId, userId)
                .query(Long.class)
                .single() > 0;
        return new ReactionState(active, count);
    }

    private void requireCommentExists(String commentId) {
        boolean exists = jdbc.sql("SELECT COUNT(*) FROM comments WHERE id = ?")
                .param(commentId)
                .query(Long.class)
                .single() > 0;
        if (!exists) {
            throw new ApiException(HttpStatus.NOT_FOUND, "reaction.target_not_found",
                    "Not found", "Không tìm thấy bình luận này.");
        }
    }

    private static void requireComment(String targetType) {
        String type = targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);
        if (!"COMMENT".equals(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "reaction.invalid_target",
                    "Unknown target type", "Chỉ hỗ trợ thả tim cho bình luận.");
        }
    }

    private static String currentUser(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }

    private static String requireUser(Jwt jwt) {
        String userId = currentUser(jwt);
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Authentication required", "Vui lòng đăng nhập để thả tim.");
        }
        return userId;
    }
}
