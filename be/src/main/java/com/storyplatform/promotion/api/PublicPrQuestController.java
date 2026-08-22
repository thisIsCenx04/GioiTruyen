package com.storyplatform.promotion.api;

import com.storyplatform.promotion.application.PrQuestService;
import com.storyplatform.shared.api.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reader side of PR quests: the board, the claim, the submission, and the
 * history of what was earned.
 *
 * <p>Reading the board is public - someone deciding whether to sign up should
 * be able to see what is on offer first. Everything that commits a person or
 * moves coins needs an account.
 */
@RestController
@RequestMapping("/pr-quests")
public class PublicPrQuestController {

    private final PrQuestService service;
    private final JdbcClient jdbc;
    private final com.storyplatform.admin.application.StoryMediaStorage media;

    public PublicPrQuestController(
            PrQuestService service,
            JdbcClient jdbc,
            com.storyplatform.admin.application.StoryMediaStorage media
    ) {
        this.service = service;
        this.jdbc = jdbc;
        this.media = media;
    }

    public record ApplyRequest(String note) {
    }

    public record SubmitRequest(String url, String note, List<String> fileUrls) {
    }

    public record DisputeRequest(String reason, List<String> evidenceUrls) {
    }

    /**
     * The open board.
     *
     * <p>Sorting is server-side because "highest reward" over a paged list has
     * to be decided where the whole list is, not in the browser over one page.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> board(
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "false") boolean availableOnly
    ) {
        String order = switch (sort.toLowerCase(Locale.ROOT)) {
            case "reward" -> "q.reward_xu DESC, q.published_at DESC";
            case "ending" -> "q.registration_ends_at ASC";
            case "slots" -> "(q.slot_count - q.claimed_count) ASC, q.published_at DESC";
            default -> "q.published_at DESC";
        };

        StringBuilder sql = new StringBuilder("""
                SELECT q.id, q.quest_kind, q.platform, q.title, q.requirement, q.reward_xu,
                       q.slot_count, q.claimed_count, q.escrow_xu, q.status,
                       q.registration_ends_at, q.published_at, q.submit_window_days,
                       q.review_window_days, q.content_hold_days,
                       t.id AS team_id, t.name AS team_name, t.slug AS team_slug, t.avatar_url,
                       s.title AS story_title, s.slug AS story_slug,
                       -- The team's payment record, computed rather than claimed.
                       -- A creator about to spend an evening filming deserves to
                       -- know whether this team actually approves what it asks for.
                       (SELECT COUNT(1) FROM pr_quests h WHERE h.team_id = t.id
                          AND h.status <> 'DRAFT') AS team_quests,
                       (SELECT COUNT(1) FROM pr_quest_claims hc
                          JOIN pr_quests h ON h.id = hc.quest_id
                         WHERE h.team_id = t.id AND hc.status = 'APPROVED') AS team_approved,
                       (SELECT COUNT(1) FROM pr_quest_claims hc
                          JOIN pr_quests h ON h.id = hc.quest_id
                         WHERE h.team_id = t.id AND hc.status IN ('APPROVED', 'REJECTED')) AS team_reviewed
                FROM pr_quests q
                JOIN teams t ON t.id = q.team_id
                LEFT JOIN stories s ON s.id = q.story_id
                WHERE q.status IN ('OPEN', 'FULL')
                """);
        if (platform != null && !platform.isBlank()) {
            sql.append(" AND q.platform = :platform");
        }
        if (availableOnly) {
            sql.append(" AND q.status = 'OPEN' AND q.claimed_count < q.slot_count");
        }
        sql.append(" ORDER BY ").append(order).append(" LIMIT 100");

        var spec = jdbc.sql(sql.toString());
        if (platform != null && !platform.isBlank()) {
            spec = spec.param("platform", platform.toUpperCase(Locale.ROOT));
        }
        return spec.query((rs, n) -> questCard(rs)).list();
    }

    /** The reader's own quests: claimed, submitted, and everything finished. */
    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(@AuthenticationPrincipal Jwt jwt) {
        String userId = requireUser(jwt);
        return jdbc.sql("""
                        SELECT c.id AS claim_id, c.status AS claim_status, c.submission_url,
                               c.submit_due_at, c.review_due_at, c.reviewed_at, c.auto_approved,
                               c.reject_reason, c.reject_contact_channel, c.reject_contact_handle,
                               c.paid_xu, c.created_at AS claimed_at,
                               q.id, q.quest_kind, q.platform, q.title, q.requirement, q.reward_xu,
                               q.slot_count, q.claimed_count, q.escrow_xu, q.status,
                               q.registration_ends_at, q.published_at, q.submit_window_days,
                               q.review_window_days, q.content_hold_days,
                               t.id AS team_id, t.name AS team_name, t.slug AS team_slug,
                               t.avatar_url, s.title AS story_title, s.slug AS story_slug,
                               0 AS team_quests, 0 AS team_approved, 0 AS team_reviewed
                        FROM pr_quest_claims c
                        JOIN pr_quests q ON q.id = c.quest_id
                        JOIN teams t ON t.id = q.team_id
                        LEFT JOIN stories s ON s.id = q.story_id
                        WHERE c.user_id = ?
                        ORDER BY c.created_at DESC
                        """)
                .param(userId)
                .query((rs, n) -> {
                    Map<String, Object> row = questCard(rs);
                    row.put("claimId", rs.getString("claim_id"));
                    row.put("claimStatus", rs.getString("claim_status"));
                    row.put("submissionUrl", rs.getString("submission_url"));
                    row.put("submitDueAt", text(rs, "submit_due_at"));
                    row.put("reviewDueAt", text(rs, "review_due_at"));
                    row.put("reviewedAt", text(rs, "reviewed_at"));
                    row.put("autoApproved", rs.getBoolean("auto_approved"));
                    row.put("rejectReason", rs.getString("reject_reason"));
                    row.put("rejectContact", contact(rs));
                    row.put("paidXu", rs.getLong("paid_xu"));
                    row.put("claimedAt", text(rs, "claimed_at"));
                    return row;
                })
                .list();
    }

    /**
     * Takes a slot, or applies for one.
     *
     * <p>Which of the two happens is decided by the quest, not by the caller,
     * so a reader cannot skip an owner's screening by calling a different route.
     */
    @PostMapping("/{questId}/claim")
    public Map<String, String> claim(
            @PathVariable String questId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) ApplyRequest request
    ) {
        String userId = requireUser(jwt);
        String claimId = service.claim(questId, UUID.fromString(userId));
        if (request != null && request.note() != null && !request.note().isBlank()) {
            jdbc.sql("UPDATE pr_quest_claims SET apply_note = ? WHERE id = ? AND user_id = ?")
                    .params(request.note().trim(), claimId, userId)
                    .update();
        }
        return Map.of("claimId", claimId);
    }

    @PostMapping("/claims/{claimId}/submit")
    public void submit(
            @PathVariable String claimId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody SubmitRequest request
    ) {
        String userId = requireUser(jwt);
        service.submit(claimId, UUID.fromString(userId), request.url(), request.note(),
                request.fileUrls());
    }

    /**
     * Uploads one screenshot and returns the URL to send back with the
     * submission.
     *
     * <p>Separate from the submission itself so a slow upload cannot make the
     * whole submission time out, and so the reader can see each screenshot land
     * before committing the link.
     */
    @PostMapping("/proof")
    public Map<String, String> uploadProof(
            @AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.RequestPart("file")
            org.springframework.web.multipart.MultipartFile file
    ) {
        requireUser(jwt);
        return Map.of("url", media.storePrProof(file));
    }

    /** The creator's complaint: work done and submitted, and the team will not pay. */
    @PostMapping("/claims/{claimId}/dispute")
    public void dispute(
            @PathVariable String claimId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody DisputeRequest request
    ) {
        String userId = requireUser(jwt);
        long owns = jdbc.sql("SELECT COUNT(1) FROM pr_quest_claims WHERE id = ? AND user_id = ?")
                .params(claimId, userId)
                .query(Long.class)
                .single();
        if (owns == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "pr.not_found", "Not found",
                    "Không tìm thấy nhiệm vụ này trong danh sách của bạn.");
        }
        service.raiseDispute(claimId, UUID.fromString(userId), "CREATOR",
                request.reason(), request.evidenceUrls());
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, Object> questCard(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("questKind", rs.getString("quest_kind"));
        row.put("platform", rs.getString("platform"));
        row.put("title", rs.getString("title"));
        row.put("requirement", rs.getString("requirement"));
        row.put("rewardXu", rs.getLong("reward_xu"));
        row.put("slotCount", rs.getInt("slot_count"));
        row.put("claimedCount", rs.getInt("claimed_count"));
        row.put("escrowXu", rs.getLong("escrow_xu"));
        row.put("status", rs.getString("status"));
        row.put("registrationEndsAt", text(rs, "registration_ends_at"));
        row.put("publishedAt", text(rs, "published_at"));
        row.put("submitWindowDays", rs.getInt("submit_window_days"));
        row.put("reviewWindowDays", rs.getInt("review_window_days"));
        row.put("contentHoldDays", rs.getInt("content_hold_days"));
        row.put("teamId", rs.getString("team_id"));
        row.put("teamName", rs.getString("team_name"));
        row.put("teamSlug", rs.getString("team_slug"));
        row.put("teamAvatarUrl", rs.getString("avatar_url"));
        row.put("storyTitle", rs.getString("story_title"));
        row.put("storySlug", rs.getString("story_slug"));
        row.put("teamQuests", rs.getInt("team_quests"));
        int reviewed = rs.getInt("team_reviewed");
        row.put("teamApprovalRate", reviewed == 0 ? null : rs.getInt("team_approved") * 100 / reviewed);
        return row;
    }

    private static String contact(ResultSet rs) throws SQLException {
        String handle = rs.getString("reject_contact_handle");
        if (handle == null || handle.isBlank()) return null;
        String channel = rs.getString("reject_contact_channel");
        return channel == null || channel.isBlank() ? handle : channel + ": " + handle;
    }

    private static String text(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

    private static String requireUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Authentication required", "Vui lòng đăng nhập để nhận nhiệm vụ PR.");
        }
        return jwt.getSubject();
    }
}
