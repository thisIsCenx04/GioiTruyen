package com.storyplatform.promotion.api;

import com.storyplatform.promotion.application.PrQuestService;
import com.storyplatform.promotion.application.PrQuestService.QuestDraft;
import com.storyplatform.shared.api.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The team side of PR quests: writing them, paying for them, and judging the
 * work that comes back.
 *
 * <p>Only OWNER and MANAGER reach any of this. A PR quest spends the team's
 * coins and commits them to strangers, which is not an editor's decision.
 */
@RestController
@RequestMapping("/teams/{teamId}/pr-quests")
public class TeamPrQuestController {

    private final PrQuestService service;
    private final JdbcClient jdbc;

    public TeamPrQuestController(PrQuestService service, JdbcClient jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    public record QuestRequest(
            String questKind,
            String platform,
            String title,
            String requirement,
            String contactChannel,
            String contactHandle,
            String storyId,
            long rewardXu,
            int slotCount,
            int registrationDays
    ) {
        QuestDraft toDraft() {
            return new QuestDraft(questKind, platform, title, requirement, contactChannel,
                    contactHandle, storyId, rewardXu, slotCount, registrationDays);
        }
    }

    public record RejectRequest(String reason, String contactChannel, String contactHandle) {
    }

    public record DisputeRequest(String reason, List<String> evidenceUrls) {
    }

    public record TeamQuestRow(
            String id,
            String questKind,
            String platform,
            String title,
            String requirement,
            String storyId,
            String storyTitle,
            long rewardXu,
            int slotCount,
            int claimedCount,
            int approvedCount,
            /** Applications waiting on the owner - the APPLY queue. */
            int pendingCount,
            /** Submissions waiting on the owner. This is the number that matters daily. */
            int awaitingReview,
            long escrowXu,
            long paidXu,
            long publishFeeXu,
            String status,
            String registrationEndsAt,
            String publishedAt,
            String createdAt
    ) {
    }

    /** What publishing would cost, so the form can show it before any money moves. */
    @GetMapping("/quote")
    public Map<String, Object> quote(
            @PathVariable("teamId") String teamRef,
            @AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.RequestParam long rewardXu,
            @org.springframework.web.bind.annotation.RequestParam int slotCount
    ) {
        requireManager(teamRef, jwt);
        if (rewardXu <= 0 || slotCount <= 0) {
            return Map.of("budgetXu", 0, "publishFeeXu", PrQuestService.PUBLISH_FEE_XU,
                    "totalXu", 0);
        }
        var cost = PrQuestService.cost(rewardXu, slotCount);
        return Map.of(
                "budgetXu", cost.budgetXu(),
                "publishFeeXu", cost.publishFeeXu(),
                "totalXu", cost.totalXu());
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TeamQuestRow> list(@PathVariable("teamId") String teamRef, @AuthenticationPrincipal Jwt jwt) {
        String teamId = requireManager(teamRef, jwt);
        return jdbc.sql("""
                        SELECT q.*, s.title AS story_title,
                               (SELECT COUNT(1) FROM pr_quest_claims c
                                 WHERE c.quest_id = q.id AND c.status = 'PENDING') AS pending_count,
                               (SELECT COUNT(1) FROM pr_quest_claims c
                                 WHERE c.quest_id = q.id AND c.status = 'SUBMITTED') AS awaiting_review
                        FROM pr_quests q
                        LEFT JOIN stories s ON s.id = q.story_id
                        WHERE q.team_id = ?
                        ORDER BY q.created_at DESC
                        """)
                .param(teamId)
                .query((rs, n) -> new TeamQuestRow(
                        rs.getString("id"), rs.getString("quest_kind"), rs.getString("platform"),
                        rs.getString("title"), rs.getString("requirement"), rs.getString("story_id"),
                        rs.getString("story_title"), rs.getLong("reward_xu"), rs.getInt("slot_count"),
                        rs.getInt("claimed_count"), rs.getInt("approved_count"),
                        rs.getInt("pending_count"), rs.getInt("awaiting_review"),
                        rs.getLong("escrow_xu"), rs.getLong("paid_xu"),
                        rs.getLong("publish_fee_xu"), rs.getString("status"),
                        text(rs, "registration_ends_at"), text(rs, "published_at"),
                        text(rs, "created_at")))
                .list();
    }

    /** Applications and submissions on one quest - the owner's work queue. */
    @GetMapping("/{questId}/claims")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> claims(
            @PathVariable("teamId") String teamRef,
            @PathVariable String questId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String teamId = requireManager(teamRef, jwt);
        return jdbc.sql("""
                        SELECT c.id, c.status, c.apply_note, c.submission_url, c.submission_note,
                               c.claimed_at, c.submitted_at, c.submit_due_at, c.review_due_at,
                               c.reviewed_at, c.auto_approved, c.reject_reason, c.paid_xu,
                               u.id AS user_id, u.display_name, u.email, u.avatar_url,
                               (SELECT GROUP_CONCAT(f.media_url SEPARATOR '\t')
                                  FROM pr_claim_files f WHERE f.claim_id = c.id) AS files
                        FROM pr_quest_claims c
                        JOIN pr_quests q ON q.id = c.quest_id
                        JOIN users u ON u.id = c.user_id
                        WHERE c.quest_id = ? AND q.team_id = ?
                        ORDER BY FIELD(c.status, 'SUBMITTED', 'PENDING', 'CLAIMED') DESC, c.created_at ASC
                        """)
                .params(questId, teamId)
                .query((rs, n) -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<String, Object>();
                    row.put("id", rs.getString("id"));
                    row.put("status", rs.getString("status"));
                    row.put("applyNote", rs.getString("apply_note"));
                    row.put("submissionUrl", rs.getString("submission_url"));
                    row.put("submissionNote", rs.getString("submission_note"));
                    row.put("claimedAt", text(rs, "claimed_at"));
                    row.put("submittedAt", text(rs, "submitted_at"));
                    row.put("submitDueAt", text(rs, "submit_due_at"));
                    row.put("reviewDueAt", text(rs, "review_due_at"));
                    row.put("reviewedAt", text(rs, "reviewed_at"));
                    row.put("autoApproved", rs.getBoolean("auto_approved"));
                    row.put("rejectReason", rs.getString("reject_reason"));
                    row.put("paidXu", rs.getLong("paid_xu"));
                    row.put("userId", rs.getString("user_id"));
                    row.put("userName", displayName(rs.getString("display_name"), rs.getString("email")));
                    row.put("userAvatarUrl", rs.getString("avatar_url"));
                    String files = rs.getString("files");
                    row.put("files", files == null || files.isBlank()
                            ? List.of() : List.of(files.split("\t")));
                    return row;
                })
                .list();
    }

    @PostMapping
    public Map<String, String> create(
            @PathVariable("teamId") String teamRef,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody QuestRequest request
    ) {
        String teamId = requireManager(teamRef, jwt);
        String id = service.createDraft(UUID.fromString(teamId), userId(jwt), request.toDraft());
        return Map.of("id", id);
    }

    @PutMapping("/{questId}")
    public void update(
            @PathVariable("teamId") String teamRef,
            @PathVariable String questId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody QuestRequest request
    ) {
        String teamId = requireManager(teamRef, jwt);
        service.updateDraft(questId, UUID.fromString(teamId), request.toDraft());
    }

    @DeleteMapping("/{questId}")
    public void delete(
            @PathVariable("teamId") String teamRef,
            @PathVariable String questId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String teamId = requireManager(teamRef, jwt);
        service.deleteDraft(questId, UUID.fromString(teamId));
    }

    @PostMapping("/{questId}/publish")
    public void publish(
            @PathVariable("teamId") String teamRef,
            @PathVariable String questId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String teamId = requireManager(teamRef, jwt);
        service.publish(questId, UUID.fromString(teamId), userId(jwt));
    }

    @PostMapping("/{questId}/stop")
    public Map<String, Long> stop(
            @PathVariable("teamId") String teamRef,
            @PathVariable String questId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String teamId = requireManager(teamRef, jwt);
        return Map.of("refundedXu", service.close(questId, UUID.fromString(teamId), "CANCELLED"));
    }

    @PostMapping("/claims/{claimId}/accept")
    public void accept(
            @PathVariable("teamId") String teamRef,
            @PathVariable String claimId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String teamId = requireManager(teamRef, jwt);
        service.acceptApplication(claimId, UUID.fromString(teamId));
    }

    @PostMapping("/claims/{claimId}/decline")
    public void decline(
            @PathVariable("teamId") String teamRef,
            @PathVariable String claimId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) RejectRequest request
    ) {
        String teamId = requireManager(teamRef, jwt);
        service.declineApplication(claimId, UUID.fromString(teamId),
                request == null ? null : request.reason());
    }

    @PostMapping("/claims/{claimId}/approve")
    public void approve(
            @PathVariable("teamId") String teamRef,
            @PathVariable String claimId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String teamId = requireManager(teamRef, jwt);
        service.approve(claimId, UUID.fromString(teamId), false);
    }

    @PostMapping("/claims/{claimId}/reject")
    public void reject(
            @PathVariable("teamId") String teamRef,
            @PathVariable String claimId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RejectRequest request
    ) {
        String teamId = requireManager(teamRef, jwt);
        service.reject(claimId, UUID.fromString(teamId), request.reason(),
                request.contactChannel(), request.contactHandle());
    }

    /** The team's own complaint: work approved and paid, then the post came down. */
    @PostMapping("/claims/{claimId}/dispute")
    public void dispute(
            @PathVariable("teamId") String teamRef,
            @PathVariable String claimId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody DisputeRequest request
    ) {
        requireManager(teamRef, jwt);
        service.raiseDispute(claimId, userId(jwt), "TEAM", request.reason(), request.evidenceUrls());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Resolves the team reference and proves the caller may spend its coins.
     *
     * <p>Deliberately narrower than the rest of the workspace: an EDITOR can
     * publish chapters but cannot commit the team's money to strangers.
     */
    private String requireManager(String teamRef, Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Authentication required", "Vui lòng đăng nhập.");
        }
        String teamId = jdbc.sql("SELECT id FROM teams WHERE id = ? OR slug = ? LIMIT 1")
                .params(teamRef, teamRef)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "team.not_found",
                        "Team not found", "Không tìm thấy nhóm."));

        String role = jdbc.sql("""
                        SELECT member_role FROM team_members
                        WHERE team_id = ? AND user_id = ? AND status = 'ACTIVE'
                        """)
                .params(teamId, jwt.getSubject())
                .query(String.class)
                .optional()
                .orElse(null);

        if (!"OWNER".equals(role) && !"MANAGER".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "pr.forbidden", "Forbidden",
                    "Chỉ chủ nhóm hoặc quản lý mới quản lý được nhiệm vụ PR.");
        }
        return teamId;
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static String displayName(String name, String email) {
        if (name != null && !name.isBlank()) return name;
        return email == null ? "Độc giả" : email.split("@")[0];
    }

    private static String text(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }
}
