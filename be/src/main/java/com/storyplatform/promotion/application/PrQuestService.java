package com.storyplatform.promotion.application;

import com.storyplatform.shared.api.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PR quests: a team pays readers to promote a story off the platform.
 *
 * <p>The work happens on TikTok, YouTube or a Facebook page - somewhere this
 * server cannot see. Everything here follows from that. Money is escrowed
 * before any work starts, so a creator can see the coins exist. A human
 * approves, because no rule can judge a video. And every step carries a
 * deadline, because the side holding the money has no reason to hurry and the
 * side doing the work cannot afford to wait forever.
 */
@Service
public class PrQuestService {

    /**
     * The only fee. Taken at publish and never returned, even if nobody claims.
     *
     * <p>A flat fee and nothing else: the platform charges for the slot on the
     * board, not for the work. Every coin of the budget reaches a creator or
     * comes back to the team, which makes the arithmetic on the card something
     * a publisher can check in their head.
     */
    public static final long PUBLISH_FEE_XU = 10_000L;

    public static final long MIN_REWARD_XU = 1_000L;
    public static final long MAX_BUDGET_XU = 10_000_000L;
    public static final int MAX_SLOTS = 100;
    /** How many PR quests one reader may hold at once. */
    public static final int MAX_ACTIVE_CLAIMS = 3;

    /**
     * How long a quest may stay open for sign-ups.
     *
     * <p>Two choices, both short. A quest that sits on the board for months
     * keeps its budget escrowed and its slots blocked while the story it was
     * meant to promote has already moved on.
     */
    private static final List<Integer> REGISTRATION_DAYS = List.of(5, 10);

    private final JdbcClient jdbc;

    public PrQuestService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ money

    /**
     * What publishing a quest costs: the budget, plus one flat fee.
     *
     * <p>There is no percentage. Every coin of the budget either reaches a
     * creator or is refunded, so what a team pays the platform is a single
     * number they know before they publish and that never changes afterwards.
     */
    public record QuestCost(long budgetXu, long publishFeeXu, long totalXu) {
    }

    public static QuestCost cost(long rewardXu, int slotCount) {
        long budget = rewardXu * slotCount;
        return new QuestCost(budget, PUBLISH_FEE_XU, budget + PUBLISH_FEE_XU);
    }

    // ----------------------------------------------------------------- create

    public record QuestDraft(
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
    }

    /** Validates a draft and refuses it with a reason the publisher can act on. */
    public static void validate(QuestDraft draft) {
        if (draft.title() == null || draft.title().isBlank()) {
            throw bad("pr.title_required", "Nhiệm vụ phải có tiêu đề.");
        }
        if (draft.requirement() == null || draft.requirement().isBlank()) {
            throw bad("pr.requirement_required",
                    "Phải mô tả rõ yêu cầu (KPI) để người nhận biết phải làm gì mới được duyệt.");
        }
        if (draft.rewardXu() < MIN_REWARD_XU) {
            throw bad("pr.reward_too_low",
                    ("Thưởng mỗi người tối thiểu %s Xu. Mức thấp hơn thì không ai nhận, "
                            + "nhiệm vụ chỉ chiếm chỗ trên bảng.").formatted(xu(MIN_REWARD_XU)));
        }
        if (draft.slotCount() < 1 || draft.slotCount() > MAX_SLOTS) {
            throw bad("pr.slots_out_of_range", "Số suất phải từ 1 đến " + MAX_SLOTS + ".");
        }
        long budget = draft.rewardXu() * draft.slotCount();
        if (budget > MAX_BUDGET_XU) {
            throw bad("pr.budget_too_high",
                    "Tổng ngân sách %s Xu vượt giới hạn %s Xu mỗi nhiệm vụ."
                            .formatted(xu(budget), xu(MAX_BUDGET_XU)));
        }
        if (!REGISTRATION_DAYS.contains(draft.registrationDays())) {
            throw bad("pr.duration_invalid",
                    "Hạn đăng ký phải là một trong: " + REGISTRATION_DAYS + " ngày.");
        }
    }

    @Transactional
    public String createDraft(UUID teamId, UUID ownerId, QuestDraft draft) {
        validate(draft);
        String id = UUID.randomUUID().toString();
        jdbc.sql("""
                        INSERT INTO pr_quests
                            (id, team_id, story_id, created_by, quest_kind, platform, title,
                             requirement, contact_channel, contact_handle, reward_xu, slot_count,
                             status, registration_ends_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)
                        """)
                .params(id, teamId.toString(), blankToNull(draft.storyId()), ownerId.toString(),
                        kind(draft.questKind()), platform(draft.platform()), draft.title().trim(),
                        draft.requirement().trim(), blankToNull(draft.contactChannel()),
                        blankToNull(draft.contactHandle()), draft.rewardXu(), draft.slotCount(),
                        Timestamp.from(Instant.now().plus(draft.registrationDays(), ChronoUnit.DAYS)))
                .update();
        return id;
    }

    /** A draft may still be edited freely; a published quest may not. */
    @Transactional
    public void updateDraft(String questId, UUID teamId, QuestDraft draft) {
        validate(draft);
        requireStatus(questId, teamId, "DRAFT",
                "Nhiệm vụ đã publish nên không sửa được nữa. Hãy dừng nhiệm vụ và tạo nhiệm vụ mới.");
        jdbc.sql("""
                        UPDATE pr_quests
                           SET quest_kind = ?, platform = ?, title = ?, requirement = ?,
                               contact_channel = ?, contact_handle = ?, story_id = ?,
                               reward_xu = ?, slot_count = ?, registration_ends_at = ?
                         WHERE id = ? AND team_id = ? AND status = 'DRAFT'
                        """)
                .params(kind(draft.questKind()), platform(draft.platform()), draft.title().trim(),
                        draft.requirement().trim(), blankToNull(draft.contactChannel()),
                        blankToNull(draft.contactHandle()), blankToNull(draft.storyId()),
                        draft.rewardXu(), draft.slotCount(),
                        Timestamp.from(Instant.now().plus(draft.registrationDays(), ChronoUnit.DAYS)),
                        questId, teamId.toString())
                .update();
    }

    @Transactional
    public void deleteDraft(String questId, UUID teamId) {
        requireStatus(questId, teamId, "DRAFT",
                "Chỉ xoá được nhiệm vụ chưa publish. Nhiệm vụ đang chạy thì dùng “Dừng nhiệm vụ”.");
        jdbc.sql("DELETE FROM pr_quests WHERE id = ? AND team_id = ? AND status = 'DRAFT'")
                .params(questId, teamId.toString())
                .update();
    }

    // ---------------------------------------------------------------- publish

    /**
     * Takes the money and opens the quest for claims.
     *
     * <p>Charging up front is the whole point: a creator looking at the board
     * can see the coins are already committed, rather than trusting a promise
     * from a team they have never dealt with.
     */
    @Transactional
    public void publish(String questId, UUID teamId, UUID ownerId) {
        var quest = jdbc.sql("""
                        SELECT reward_xu, slot_count, title, status
                        FROM pr_quests WHERE id = ? AND team_id = ?
                        """)
                .params(questId, teamId.toString())
                .query((rs, n) -> new Object[] {
                        rs.getLong("reward_xu"), rs.getInt("slot_count"),
                        rs.getString("title"), rs.getString("status") })
                .optional()
                .orElseThrow(() -> notFound("Không tìm thấy nhiệm vụ."));

        if (!"DRAFT".equals(quest[3])) {
            throw bad("pr.already_published", "Nhiệm vụ này đã được publish rồi.");
        }

        QuestCost cost = cost((Long) quest[0], (Integer) quest[1]);
        debitWallet(ownerId, cost.totalXu(), questId,
                "Ký quỹ nhiệm vụ PR: " + quest[2]);

        jdbc.sql("""
                        UPDATE pr_quests
                           SET status = 'OPEN', published_at = NOW(3),
                               escrow_xu = ?, publish_fee_xu = ?
                         WHERE id = ? AND team_id = ? AND status = 'DRAFT'
                        """)
                .params(cost.budgetXu(), cost.publishFeeXu(),
                        questId, teamId.toString())
                .update();
    }

    // ------------------------------------------------------------------ claim

    /**
     * Takes a slot, or records an application on an APPLY quest.
     *
     * <p>The slot is taken by one conditional UPDATE. Reading the count and then
     * inserting would leave a gap in which two simultaneous claims both see a
     * free slot and both take it, leaving the quest oversubscribed and the
     * escrow short. Here the database decides: whichever statement commits
     * first gets the row, the other affects no rows and is told the quest is
     * full. No application lock, no queue, no race.
     */
    @Transactional
    public String claim(String questId, UUID userId) {
        var quest = jdbc.sql("""
                        SELECT team_id, quest_kind, submit_window_days, title, status
                        FROM pr_quests WHERE id = ?
                        """)
                .param(questId)
                .query((rs, n) -> new Object[] {
                        rs.getString("team_id"), rs.getString("quest_kind"),
                        rs.getInt("submit_window_days"), rs.getString("title"),
                        rs.getString("status") })
                .optional()
                .orElseThrow(() -> notFound("Không tìm thấy nhiệm vụ."));

        if (!"OPEN".equals(quest[4])) {
            throw bad("pr.not_open", "Nhiệm vụ này không còn nhận người.");
        }
        requireNotOwnTeam((String) quest[0], userId);
        requireClaimBudget(userId);

        boolean apply = "APPLY".equals(quest[1]);
        String claimId = UUID.randomUUID().toString();

        if (apply) {
            // An application holds no slot. The owner approving it is what takes
            // one, so a popular quest can gather more applicants than slots.
            insertClaim(claimId, questId, userId, "PENDING", null);
            return claimId;
        }

        // The claim row goes in first, then the slot is taken.
        //
        // The other order looks more natural and is wrong: incrementing
        // claimed_count before the insert means a duplicate click - which the
        // UNIQUE key then rejects - has already consumed a slot. The
        // transaction rolls that back, but only because the exception escapes;
        // any future change that swallowed it would silently burn slots.
        // Inserting first makes the duplicate impossible to charge for,
        // whatever happens afterwards.
        Instant due = Instant.now().plus((Integer) quest[2], ChronoUnit.DAYS);
        insertClaim(claimId, questId, userId, "CLAIMED", due);
        takeSlot(questId);
        markFullIfNeeded(questId);
        return claimId;
    }

    /**
     * The atomic slot grab. Returns nothing; throws when the quest is full.
     *
     * <p>Every condition that could make the claim invalid is in the WHERE
     * clause, so the check and the increment cannot be separated by another
     * request slipping between them.
     */
    private void takeSlot(String questId) {
        int rows = jdbc.sql("""
                        UPDATE pr_quests
                           SET claimed_count = claimed_count + 1
                         WHERE id = ?
                           AND status = 'OPEN'
                           AND claimed_count < slot_count
                           AND (registration_ends_at IS NULL OR registration_ends_at > NOW(3))
                        """)
                .param(questId)
                .update();
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "pr.no_slot_left",
                    "No slot left",
                    "Nhiệm vụ đã đủ người nhận hoặc đã hết hạn đăng ký.");
        }
    }

    /** Closes the board once the last slot is gone, so it stops being offered. */
    private void markFullIfNeeded(String questId) {
        jdbc.sql("UPDATE pr_quests SET status = 'FULL' WHERE id = ? AND claimed_count >= slot_count AND status = 'OPEN'")
                .param(questId)
                .update();
    }

    private void insertClaim(String claimId, String questId, UUID userId, String status, Instant submitDue) {
        try {
            jdbc.sql("""
                            INSERT INTO pr_quest_claims
                                (id, quest_id, user_id, status, claimed_at, submit_due_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """)
                    .params(claimId, questId, userId.toString(), status,
                            status.equals("PENDING") ? null : Timestamp.from(Instant.now()),
                            submitDue == null ? null : Timestamp.from(submitDue))
                    .update();
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            // uk_pr_claim_once. Reached by a double click on two tabs, where both
            // requests pass every application check before either one inserts.
            throw new ApiException(HttpStatus.CONFLICT, "pr.already_claimed",
                    "Already claimed", "Bạn đã nhận nhiệm vụ này rồi.");
        }
    }

    // -------------------------------------------------- APPLY quest decisions

    /** Approving an application is what takes the slot, so it uses the same UPDATE. */
    @Transactional
    public void acceptApplication(String claimId, UUID teamId) {
        var row = loadClaimForTeam(claimId, teamId);
        if (!"PENDING".equals(row.status())) {
            throw bad("pr.not_pending", "Đơn này không còn ở trạng thái chờ duyệt.");
        }
        takeSlot(row.questId());
        int days = jdbc.sql("SELECT submit_window_days FROM pr_quests WHERE id = ?")
                .param(row.questId()).query(Integer.class).single();
        jdbc.sql("""
                        UPDATE pr_quest_claims
                           SET status = 'CLAIMED', claimed_at = NOW(3), submit_due_at = ?
                         WHERE id = ? AND status = 'PENDING'
                        """)
                .params(Timestamp.from(Instant.now().plus(days, ChronoUnit.DAYS)), claimId)
                .update();
        markFullIfNeeded(row.questId());
        notify(row.userId(), "Đơn PR được duyệt",
                "Bạn đã được chọn cho nhiệm vụ PR. Hãy hoàn thành và nộp link trong " + days + " ngày.");
    }

    @Transactional
    public void declineApplication(String claimId, UUID teamId, String reason) {
        var row = loadClaimForTeam(claimId, teamId);
        if (!"PENDING".equals(row.status())) {
            throw bad("pr.not_pending", "Đơn này không còn ở trạng thái chờ duyệt.");
        }
        jdbc.sql("UPDATE pr_quest_claims SET status = 'DECLINED', reject_reason = ?, reviewed_at = NOW(3) WHERE id = ?")
                .params(blankToNull(reason), claimId)
                .update();
        notify(row.userId(), "Đơn PR không được chọn",
                reason == null || reason.isBlank() ? "Nhóm đã chọn người khác." : reason);
    }

    // ----------------------------------------------------------------- submit

    @Transactional
    public void submit(String claimId, UUID userId, String url, String note, List<String> files) {
        if (url == null || url.isBlank()) {
            throw bad("pr.url_required", "Phải nộp link bài đăng để nhóm kiểm tra.");
        }
        // Screenshots are encouraged rather than required. They are the only
        // evidence that survives a post being deleted after approval, so the
        // form pushes for them - but refusing a submission without one would
        // block someone whose upload failed from being paid for work they did.

        var row = jdbc.sql("""
                        SELECT c.status, c.quest_id, q.review_window_days, q.created_by, q.title
                        FROM pr_quest_claims c JOIN pr_quests q ON q.id = c.quest_id
                        WHERE c.id = ? AND c.user_id = ?
                        """)
                .params(claimId, userId.toString())
                .query((rs, n) -> new Object[] {
                        rs.getString("status"), rs.getString("quest_id"),
                        rs.getInt("review_window_days"), rs.getString("created_by"),
                        rs.getString("title") })
                .optional()
                .orElseThrow(() -> notFound("Không tìm thấy nhiệm vụ bạn đã nhận."));

        if (!"CLAIMED".equals(row[0]) && !"REJECTED".equals(row[0])) {
            throw bad("pr.not_submittable",
                    "Nhiệm vụ này không ở trạng thái có thể nộp bài.");
        }

        int reviewDays = (Integer) row[2];
        jdbc.sql("""
                        UPDATE pr_quest_claims
                           SET status = 'SUBMITTED', submitted_at = NOW(3), submission_url = ?,
                               submission_note = ?, review_due_at = ?
                         WHERE id = ?
                        """)
                .params(url.trim(), blankToNull(note),
                        Timestamp.from(Instant.now().plus(reviewDays, ChronoUnit.DAYS)), claimId)
                .update();

        jdbc.sql("DELETE FROM pr_claim_files WHERE claim_id = ?").param(claimId).update();
        for (String file : files) {
            if (file == null || file.isBlank()) continue;
            jdbc.sql("INSERT INTO pr_claim_files (id, claim_id, media_url) VALUES (?, ?, ?)")
                    .params(UUID.randomUUID().toString(), claimId, file.trim())
                    .update();
        }

        notify((String) row[3], "Có bài PR chờ duyệt",
                "Nhiệm vụ “%s” có bài nộp mới. Bạn có %d ngày để duyệt, quá hạn hệ thống sẽ tự duyệt và trả Xu."
                        .formatted(row[4], reviewDays));
    }

    // ----------------------------------------------------------------- review

    @Transactional
    public void approve(String claimId, UUID teamId, boolean automatic) {
        var row = loadClaimForTeam(claimId, teamId);
        if (!"SUBMITTED".equals(row.status())) {
            throw bad("pr.not_submitted", "Bài này chưa được nộp nên không duyệt được.");
        }
        payClaim(claimId, row.questId(), row.userId(), automatic);
    }

    /**
     * Pays one creator out of the escrow, in full.
     *
     * <p>Nothing is deducted here. The platform's only charge was taken at
     * publish, so the creator receives exactly the figure the quest advertised.
     *
     * <p>Guarded by a conditional UPDATE on the escrow: the row only changes
     * while there is money left to pay from, so two approvals landing at once
     * cannot both draw the last slot's worth.
     */
    private void payClaim(String claimId, String questId, String userId, boolean automatic) {
        long reward = jdbc.sql("SELECT reward_xu FROM pr_quests WHERE id = ?")
                .param(questId).query(Long.class).single();

        int rows = jdbc.sql("""
                        UPDATE pr_quests
                           SET escrow_xu = escrow_xu - ?,
                               paid_xu = paid_xu + ?,
                               approved_count = approved_count + 1
                         WHERE id = ? AND escrow_xu >= ?
                        """)
                .params(reward, reward, questId, reward)
                .update();
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "pr.escrow_empty",
                    "Escrow empty",
                    "Ký quỹ của nhiệm vụ không đủ để trả. Hãy liên hệ quản trị viên.");
        }

        jdbc.sql("""
                        UPDATE pr_quest_claims
                           SET status = 'APPROVED', reviewed_at = NOW(3), auto_approved = ?,
                               paid_xu = ?, paid_at = NOW(3)
                         WHERE id = ?
                        """)
                .params(automatic, reward, claimId)
                .update();

        creditWallet(userId, reward, questId, "Thưởng nhiệm vụ PR");
        notify(userId, "Nhiệm vụ PR đã được duyệt",
                automatic
                        ? "Nhóm không phản hồi trong hạn duyệt nên hệ thống đã tự duyệt. Bạn nhận %s Xu.".formatted(xu(reward))
                        : "Bạn nhận %s Xu.".formatted(xu(reward)));
    }

    /**
     * Refuses a submission. The slot stays with the creator so they can fix the
     * work and submit again before their deadline - a rejection is feedback,
     * not a dismissal.
     */
    @Transactional
    public void reject(String claimId, UUID teamId, String reason, String channel, String handle) {
        if (reason == null || reason.isBlank()) {
            throw bad("pr.reason_required",
                    "Phải ghi lý do từ chối để người nhận biết cần sửa gì.");
        }
        if (handle == null || handle.isBlank()) {
            throw bad("pr.contact_required",
                    "Phải để lại kênh liên hệ (Facebook, Zalo, số điện thoại) để hai bên trao đổi.");
        }
        var row = loadClaimForTeam(claimId, teamId);
        if (!"SUBMITTED".equals(row.status())) {
            throw bad("pr.not_submitted", "Bài này chưa được nộp nên không từ chối được.");
        }
        jdbc.sql("""
                        UPDATE pr_quest_claims
                           SET status = 'REJECTED', reviewed_at = NOW(3), reject_reason = ?,
                               reject_contact_channel = ?, reject_contact_handle = ?
                         WHERE id = ?
                        """)
                .params(reason.trim(), blankToNull(channel), handle.trim(), claimId)
                .update();
        notify(row.userId(), "Bài PR bị từ chối",
                reason.trim() + " — Liên hệ: " + handle.trim());
    }

    // ------------------------------------------------------------------ close

    /**
     * Ends a quest and returns what was never spent.
     *
     * <p>Slots already taken are not refunded: that money is committed to
     * someone who may already be filming. Stopping a campaign closes the door
     * to new claimants, it does not cancel the people already inside.
     */
    @Transactional
    public long close(String questId, UUID teamId, String status) {
        var row = jdbc.sql("""
                        SELECT escrow_xu, claimed_count, approved_count,
                               reward_xu, created_by, title, status
                        FROM pr_quests WHERE id = ? AND team_id = ?
                        """)
                .params(questId, teamId.toString())
                .query((rs, n) -> new Object[] {
                        rs.getLong("escrow_xu"),
                        rs.getInt("claimed_count"), rs.getInt("approved_count"),
                        rs.getLong("reward_xu"), rs.getString("created_by"),
                        rs.getString("title"), rs.getString("status") })
                .optional()
                .orElseThrow(() -> notFound("Không tìm thấy nhiệm vụ."));

        if ("CLOSED".equals(row[6]) || "CANCELLED".equals(row[6])) {
            return 0L;
        }

        // Claims still in flight keep their money reserved. Only the slots
        // nobody took come back.
        int outstanding = (Integer) row[1] - (Integer) row[2];
        long committed = Math.max(0, outstanding) * (Long) row[3];
        long refund = Math.max(0, (Long) row[0] - committed);

        jdbc.sql("""
                        UPDATE pr_quests
                           SET status = ?, closed_at = NOW(3), escrow_xu = escrow_xu - ?
                         WHERE id = ? AND team_id = ?
                        """)
                .params(status, refund, questId, teamId.toString())
                .update();

        if (refund > 0) {
            creditWallet((String) row[4], refund, questId,
                    "Hoàn Xu nhiệm vụ PR: " + row[5]);
            notify((String) row[4], "Đã hoàn Xu nhiệm vụ PR",
                    "Nhiệm vụ “%s” kết thúc. Hoàn lại %s Xu (phí đăng %s Xu không hoàn)."
                            .formatted(row[5], xu(refund), xu(PUBLISH_FEE_XU)));
        }
        return refund;
    }

    // --------------------------------------------------------------- disputes

    @Transactional
    public void raiseDispute(String claimId, UUID userId, String role, String reason, List<String> evidence) {
        if (reason == null || reason.isBlank()) {
            throw bad("pr.dispute_reason_required", "Phải mô tả rõ vấn đề để quản trị viên xử lý.");
        }
        boolean exists = jdbc.sql("SELECT COUNT(1) FROM pr_disputes WHERE claim_id = ? AND status = 'OPEN'")
                .param(claimId).query(Long.class).single() > 0;
        if (exists) {
            throw bad("pr.dispute_open", "Khiếu nại cho nhiệm vụ này đang được xử lý.");
        }
        jdbc.sql("""
                        INSERT INTO pr_disputes (id, claim_id, raised_by, raised_role, reason, evidence_urls)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .params(UUID.randomUUID().toString(), claimId, userId.toString(),
                        "TEAM".equalsIgnoreCase(role) ? "TEAM" : "CREATOR",
                        reason.trim(),
                        evidence == null || evidence.isEmpty() ? null : String.join("\n", evidence))
                .update();
    }

    // ------------------------------------------------------------- scheduling

    /**
     * Everything the clock decides. Without this the deadlines are only words:
     * a claim nobody submits blocks its slot forever, and a submission nobody
     * reviews is never paid.
     */
    @Transactional
    public int runDueWork() {
        int handled = 0;
        handled += expireStaleClaims();
        handled += autoApproveOverdue();
        handled += closeExpiredQuests();
        return handled;
    }

    /** A claim with nothing submitted by its deadline gives the slot back. */
    private int expireStaleClaims() {
        List<Object[]> stale = jdbc.sql("""
                        SELECT id, quest_id, user_id FROM pr_quest_claims
                        WHERE status = 'CLAIMED' AND submit_due_at IS NOT NULL AND submit_due_at < NOW(3)
                        """)
                .query((rs, n) -> new Object[] {
                        rs.getString("id"), rs.getString("quest_id"), rs.getString("user_id") })
                .list();

        for (Object[] row : stale) {
            jdbc.sql("UPDATE pr_quest_claims SET status = 'EXPIRED' WHERE id = ? AND status = 'CLAIMED'")
                    .param((String) row[0]).update();
            jdbc.sql("""
                            UPDATE pr_quests
                               SET claimed_count = GREATEST(claimed_count - 1, 0),
                                   status = CASE WHEN status = 'FULL' THEN 'OPEN' ELSE status END
                             WHERE id = ?
                            """)
                    .param((String) row[1]).update();
            notify((String) row[2], "Nhiệm vụ PR đã quá hạn nộp",
                    "Bạn không nộp bài trong hạn nên suất đã được trả lại cho người khác.");
        }
        return stale.size();
    }

    /**
     * A submission the owner never answered is approved and paid.
     *
     * <p>This is the rule the whole marketplace rests on. Without it the side
     * holding the money can simply say nothing: the creator has already done
     * the work, has no way to complain about a rejection that never came, and
     * nobody would take a second quest.
     */
    private int autoApproveOverdue() {
        List<Object[]> overdue = jdbc.sql("""
                        SELECT c.id, c.quest_id, c.user_id, q.created_by, q.title
                        FROM pr_quest_claims c JOIN pr_quests q ON q.id = c.quest_id
                        WHERE c.status = 'SUBMITTED' AND c.review_due_at IS NOT NULL
                          AND c.review_due_at < NOW(3)
                        """)
                .query((rs, n) -> new Object[] {
                        rs.getString("id"), rs.getString("quest_id"), rs.getString("user_id"),
                        rs.getString("created_by"), rs.getString("title") })
                .list();

        for (Object[] row : overdue) {
            payClaim((String) row[0], (String) row[1], (String) row[2], true);
            notify((String) row[3], "Bài PR được tự động duyệt",
                    "Nhiệm vụ “%s” quá hạn duyệt nên hệ thống đã tự duyệt và trả Xu cho người thực hiện."
                            .formatted(row[4]));
        }
        return overdue.size();
    }

    private int closeExpiredQuests() {
        List<Object[]> expired = jdbc.sql("""
                        SELECT id, team_id FROM pr_quests
                        WHERE status IN ('OPEN', 'FULL')
                          AND registration_ends_at IS NOT NULL AND registration_ends_at < NOW(3)
                        """)
                .query((rs, n) -> new Object[] { rs.getString("id"), rs.getString("team_id") })
                .list();

        for (Object[] row : expired) {
            // Money stays reserved while anyone is still working; the sweep runs
            // again later and refunds once they have finished.
            long working = jdbc.sql("""
                            SELECT COUNT(1) FROM pr_quest_claims
                            WHERE quest_id = ? AND status IN ('CLAIMED', 'SUBMITTED', 'PENDING')
                            """)
                    .param((String) row[0]).query(Long.class).single();
            if (working > 0) {
                continue;
            }
            close((String) row[0], UUID.fromString((String) row[1]), "CLOSED");
        }
        return expired.size();
    }

    // ------------------------------------------------------------------ money

    private void debitWallet(UUID userId, long amount, String questId, String description) {
        Long balance = jdbc.sql("SELECT coin_balance FROM wallets WHERE user_id = ? FOR UPDATE")
                .param(userId.toString())
                .query(Long.class)
                .optional()
                .orElseThrow(() -> notFound("Không tìm thấy ví của bạn."));

        if (balance < amount) {
            throw new ApiException(HttpStatus.CONFLICT, "pr.insufficient_balance",
                    "Insufficient balance",
                    "Cần %s Xu để publish nhiệm vụ nhưng ví chỉ còn %s Xu."
                            .formatted(xu(amount), xu(balance)));
        }

        long after = balance - amount;
        jdbc.sql("UPDATE wallets SET coin_balance = ?, updated_at = NOW(3) WHERE user_id = ?")
                .params(after, userId.toString())
                .update();
        writeTransaction(userId.toString(), "PR_ESCROW", -amount, after, questId, description);
    }

    private void creditWallet(String userId, long amount, String questId, String description) {
        if (amount <= 0) return;
        jdbc.sql("UPDATE wallets SET coin_balance = coin_balance + ?, updated_at = NOW(3) WHERE user_id = ?")
                .params(amount, userId)
                .update();
        long after = jdbc.sql("SELECT coin_balance FROM wallets WHERE user_id = ?")
                .param(userId).query(Long.class).optional().orElse(amount);
        writeTransaction(userId, "PR_REWARD", amount, after, questId, description);
    }

    private void writeTransaction(String userId, String type, long amount, long after,
                                  String questId, String description) {
        jdbc.sql("""
                        INSERT INTO wallet_transactions
                            (id, user_id, currency, type, amount, balance_after,
                             reference_type, reference_id, description, created_at)
                        VALUES (?, ?, 'COIN', ?, ?, ?, 'PR_QUEST', ?, ?, NOW(3))
                        """)
                .params(UUID.randomUUID().toString(), userId, type, amount, after,
                        questId, description)
                .update();
    }

    // ------------------------------------------------------------------ rules

    /**
     * A team member cannot take their own team's quest.
     *
     * <p>This stops the lazy version of self-dealing, not the determined one -
     * an alternate account is invisible from here. What actually makes it
     * pointless is the publish fee, which is charged the moment the quest goes
     * live and never comes back, whoever ends up doing the work.
     */
    private void requireNotOwnTeam(String teamId, UUID userId) {
        long member = jdbc.sql("""
                        SELECT COUNT(1) FROM team_members
                        WHERE team_id = ? AND user_id = ? AND status = 'ACTIVE'
                        """)
                .params(teamId, userId.toString())
                .query(Long.class)
                .single();
        if (member > 0) {
            throw bad("pr.own_team", "Bạn là thành viên của nhóm này nên không nhận được nhiệm vụ của nhóm mình.");
        }
    }

    /** Caps how many quests one reader can hold, so nobody corners the board. */
    private void requireClaimBudget(UUID userId) {
        long active = jdbc.sql("""
                        SELECT COUNT(1) FROM pr_quest_claims
                        WHERE user_id = ? AND status IN ('PENDING', 'CLAIMED', 'SUBMITTED')
                        """)
                .param(userId.toString())
                .query(Long.class)
                .single();
        if (active >= MAX_ACTIVE_CLAIMS) {
            throw bad("pr.too_many_claims",
                    "Bạn đang nhận %d nhiệm vụ PR chưa xong. Hoàn thành bớt rồi nhận thêm."
                            .formatted(active));
        }
    }

    // ----------------------------------------------------------------- helpers

    private record ClaimRow(String questId, String userId, String status) {
    }

    private ClaimRow loadClaimForTeam(String claimId, UUID teamId) {
        return jdbc.sql("""
                        SELECT c.quest_id, c.user_id, c.status
                        FROM pr_quest_claims c JOIN pr_quests q ON q.id = c.quest_id
                        WHERE c.id = ? AND q.team_id = ?
                        """)
                .params(claimId, teamId.toString())
                .query((rs, n) -> new ClaimRow(
                        rs.getString("quest_id"), rs.getString("user_id"), rs.getString("status")))
                .optional()
                .orElseThrow(() -> notFound("Không tìm thấy bài nộp này."));
    }

    private void requireStatus(String questId, UUID teamId, String status, String message) {
        String current = jdbc.sql("SELECT status FROM pr_quests WHERE id = ? AND team_id = ?")
                .params(questId, teamId.toString())
                .query(String.class)
                .optional()
                .orElseThrow(() -> notFound("Không tìm thấy nhiệm vụ."));
        if (!status.equals(current)) {
            throw bad("pr.wrong_status", message);
        }
    }

    private void notify(String userId, String title, String message) {
        jdbc.sql("""
                        INSERT INTO notifications
                            (id, user_id, type, title, message, target_type, target_url, created_at)
                        VALUES (?, ?, 'SYSTEM', ?, ?, 'PR_QUEST', '/quests', NOW(3))
                        """)
                .params(UUID.randomUUID().toString(), userId, title, message)
                .update();
    }

    private static String kind(String value) {
        return "APPLY".equalsIgnoreCase(value) ? "APPLY" : "OPEN";
    }

    private static String platform(String value) {
        if (value == null) return "OTHER";
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "TIKTOK", "YOUTUBE", "FACEBOOK" -> value.toUpperCase(Locale.ROOT);
            default -> "OTHER";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * A coin figure written the way a Vietnamese reader expects: 300.000.
     *
     * <p>{@code %,d} formats with the JVM's default locale, so the same message
     * came out as "300,000" on one machine and "300.000" on another - the
     * server's regional settings deciding what a user reads. The locale is
     * pinned here instead.
     */
    private static final Locale VI = Locale.of("vi", "VN");

    public static String xu(long amount) {
        return java.text.NumberFormat.getInstance(VI).format(amount);
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, "Invalid PR quest", message);
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "pr.not_found", "Not found", message);
    }
}
