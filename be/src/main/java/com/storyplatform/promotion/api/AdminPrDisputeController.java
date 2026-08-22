package com.storyplatform.promotion.api;

import com.storyplatform.shared.api.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Where PR disputes are settled.
 *
 * <p>Both sides can end up here and for opposite reasons: a creator who did the
 * work and was refused, or a team whose creator took the coins and then deleted
 * the post. The admin sees the same evidence either way - the link, the
 * screenshots taken at submission time, and what each side wrote.
 *
 * <p>A ruling can move coins. That is deliberate: without it the only outcome
 * available would be an apology, and the side holding the money would have
 * nothing to lose by ignoring the rules.
 */
@RestController
@RequestMapping("/admin/pr-disputes")
public class AdminPrDisputeController {

    private final JdbcClient jdbc;

    public AdminPrDisputeController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record ResolveRequest(
            String note,
            /** Coins taken from the team and given to the creator. */
            long penaltyXu,
            /** true to settle in favour of the complainant, false to dismiss. */
            boolean upheld
    ) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "OPEN") String status) {
        return jdbc.sql("""
                        SELECT d.id, d.raised_role, d.reason, d.evidence_urls, d.status,
                               d.admin_note, d.penalty_xu, d.created_at, d.resolved_at,
                               c.id AS claim_id, c.status AS claim_status, c.submission_url,
                               c.reject_reason, c.paid_xu,
                               q.id AS quest_id, q.title AS quest_title, q.reward_xu,
                               q.platform, q.requirement,
                               t.id AS team_id, t.name AS team_name,
                               ru.display_name AS raiser_name, ru.email AS raiser_email,
                               cu.id AS creator_id, cu.display_name AS creator_name,
                               cu.email AS creator_email,
                               owner.id AS owner_id,
                               (SELECT GROUP_CONCAT(f.media_url SEPARATOR '\t')
                                  FROM pr_claim_files f WHERE f.claim_id = c.id) AS files
                        FROM pr_disputes d
                        JOIN pr_quest_claims c ON c.id = d.claim_id
                        JOIN pr_quests q ON q.id = c.quest_id
                        JOIN teams t ON t.id = q.team_id
                        JOIN users ru ON ru.id = d.raised_by
                        JOIN users cu ON cu.id = c.user_id
                        JOIN users owner ON owner.id = q.created_by
                        WHERE d.status = ?
                        ORDER BY d.created_at ASC
                        """)
                .param(status.toUpperCase(java.util.Locale.ROOT))
                .query((rs, n) -> row(rs))
                .list();
    }

    /**
     * Settles one dispute.
     *
     * <p>A penalty moves coins straight from the team owner's wallet to the
     * creator's. Both movements and the ruling itself are one transaction: a
     * half-applied ruling would leave coins that belong to nobody.
     */
    @PostMapping("/{disputeId}/resolve")
    @Transactional
    public void resolve(
            @PathVariable String disputeId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ResolveRequest request
    ) {
        String adminId = requireAdmin(jwt);

        var dispute = jdbc.sql("""
                        SELECT d.status, d.raised_role, c.user_id AS creator_id, q.created_by AS owner_id,
                               q.title
                        FROM pr_disputes d
                        JOIN pr_quest_claims c ON c.id = d.claim_id
                        JOIN pr_quests q ON q.id = c.quest_id
                        WHERE d.id = ?
                        """)
                .param(disputeId)
                .query((rs, n) -> new String[] {
                        rs.getString("status"), rs.getString("raised_role"),
                        rs.getString("creator_id"), rs.getString("owner_id"),
                        rs.getString("title") })
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "pr.dispute_not_found",
                        "Not found", "Không tìm thấy khiếu nại."));

        if (!"OPEN".equals(dispute[0])) {
            throw new ApiException(HttpStatus.CONFLICT, "pr.dispute_closed", "Already resolved",
                    "Khiếu nại này đã được xử lý.");
        }

        long penalty = Math.max(0, request.penaltyXu());
        if (penalty > 0 && request.upheld()) {
            // Which way the coins move depends on who complained: a creator who
            // was not paid is owed by the team, a team whose post came down is
            // owed by the creator.
            boolean fromTeam = "CREATOR".equals(dispute[1]);
            String payer = fromTeam ? dispute[3] : dispute[2];
            String payee = fromTeam ? dispute[2] : dispute[3];
            transfer(payer, payee, penalty, disputeId, dispute[4]);
        }

        jdbc.sql("""
                        UPDATE pr_disputes
                           SET status = ?, admin_id = ?, admin_note = ?, penalty_xu = ?,
                               resolved_at = NOW(3)
                         WHERE id = ? AND status = 'OPEN'
                        """)
                .params(request.upheld() ? "RESOLVED" : "DISMISSED", adminId,
                        request.note() == null ? null : request.note().trim(),
                        request.upheld() ? penalty : 0L, disputeId)
                .update();

        // Both sides are told the outcome. A ruling nobody hears about settles
        // nothing: the loser repeats the behaviour and the winner assumes the
        // complaint was ignored.
        String verdict = request.upheld() ? "được chấp nhận" : "không được chấp nhận";
        String note = request.note() == null || request.note().isBlank()
                ? "" : " — " + request.note().trim();
        notify(dispute[2], "Kết quả khiếu nại PR",
                "Khiếu nại về nhiệm vụ “%s” %s%s".formatted(dispute[4], verdict, note));
        notify(dispute[3], "Kết quả khiếu nại PR",
                "Khiếu nại về nhiệm vụ “%s” %s%s".formatted(dispute[4], verdict, note));
    }

    /**
     * Moves coins between two wallets under a lock on the payer.
     *
     * <p>When the payer cannot cover the penalty, what they do have is taken
     * rather than the ruling failing outright - an empty wallet must not be a
     * way to escape a decision.
     */
    private void transfer(String payerId, String payeeId, long amount, String disputeId, String questTitle) {
        Long balance = jdbc.sql("SELECT coin_balance FROM wallets WHERE user_id = ? FOR UPDATE")
                .param(payerId)
                .query(Long.class)
                .optional()
                .orElse(0L);

        long moved = Math.min(balance, amount);
        if (moved <= 0) {
            return;
        }

        jdbc.sql("UPDATE wallets SET coin_balance = coin_balance - ?, updated_at = NOW(3) WHERE user_id = ?")
                .params(moved, payerId).update();
        writeTransaction(payerId, -moved, balance - moved, disputeId,
                "Phạt khiếu nại PR: " + questTitle);

        jdbc.sql("UPDATE wallets SET coin_balance = coin_balance + ?, updated_at = NOW(3) WHERE user_id = ?")
                .params(moved, payeeId).update();
        long payeeBalance = jdbc.sql("SELECT coin_balance FROM wallets WHERE user_id = ?")
                .param(payeeId).query(Long.class).optional().orElse(moved);
        writeTransaction(payeeId, moved, payeeBalance, disputeId,
                "Bồi thường khiếu nại PR: " + questTitle);
    }

    private void writeTransaction(String userId, long amount, long after, String disputeId, String description) {
        jdbc.sql("""
                        INSERT INTO wallet_transactions
                            (id, user_id, currency, type, amount, balance_after,
                             reference_type, reference_id, description, created_at)
                        VALUES (?, ?, 'COIN', 'ADMIN_ADJUSTMENT', ?, ?, 'PR_DISPUTE', ?, ?, NOW(3))
                        """)
                .params(UUID.randomUUID().toString(), userId, amount, after, disputeId, description)
                .update();
    }

    private void notify(String userId, String title, String message) {
        jdbc.sql("""
                        INSERT INTO notifications
                            (id, user_id, type, title, message, target_type, target_url, created_at)
                        VALUES (?, ?, 'ADMIN', ?, ?, 'PR_DISPUTE', '/quests', NOW(3))
                        """)
                .params(UUID.randomUUID().toString(), userId, title, message)
                .update();
    }

    private static Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getString("id"));
        row.put("raisedRole", rs.getString("raised_role"));
        row.put("reason", rs.getString("reason"));
        String evidence = rs.getString("evidence_urls");
        row.put("evidenceUrls", evidence == null || evidence.isBlank()
                ? List.of() : List.of(evidence.split("\n")));
        row.put("status", rs.getString("status"));
        row.put("adminNote", rs.getString("admin_note"));
        row.put("penaltyXu", rs.getLong("penalty_xu"));
        row.put("createdAt", text(rs, "created_at"));
        row.put("claimId", rs.getString("claim_id"));
        row.put("claimStatus", rs.getString("claim_status"));
        row.put("submissionUrl", rs.getString("submission_url"));
        row.put("rejectReason", rs.getString("reject_reason"));
        row.put("paidXu", rs.getLong("paid_xu"));
        row.put("questId", rs.getString("quest_id"));
        row.put("questTitle", rs.getString("quest_title"));
        row.put("requirement", rs.getString("requirement"));
        row.put("platform", rs.getString("platform"));
        row.put("rewardXu", rs.getLong("reward_xu"));
        row.put("teamId", rs.getString("team_id"));
        row.put("teamName", rs.getString("team_name"));
        row.put("raiserName", name(rs.getString("raiser_name"), rs.getString("raiser_email")));
        row.put("creatorName", name(rs.getString("creator_name"), rs.getString("creator_email")));
        String files = rs.getString("files");
        row.put("files", files == null || files.isBlank() ? List.of() : List.of(files.split("\t")));
        return row;
    }

    private static String name(String display, String email) {
        if (display != null && !display.isBlank()) return display;
        return email == null ? "Người dùng" : email.split("@")[0];
    }

    private static String text(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

    private String requireAdmin(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Authentication required", "Vui lòng đăng nhập.");
        }
        return jwt.getSubject();
    }
}
