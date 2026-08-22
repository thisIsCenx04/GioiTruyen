package com.storyplatform.unit.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyplatform.promotion.application.PrQuestService;
import com.storyplatform.promotion.application.PrQuestService.QuestDraft;
import com.storyplatform.shared.api.ApiException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * The PR quest flow, end to end, against the real schema.
 *
 * <p>The unit tests cover the arithmetic; this covers whether the coins
 * actually arrive. Every step here moves real rows: an escrow that is debited
 * but never credited, or a payout that draws from an empty escrow, is the kind
 * of fault that only shows up once money is involved.
 *
 * <p>Fixtures are created and torn down per test, so a failed run leaves
 * nothing behind in the database it ran against.
 */
class PrQuestFlowTest {

    private PrQuestService service;
    private JdbcClient jdbc;

    private UUID owner;
    private UUID creator;
    private UUID team;
    private UUID story;

    /**
     * A local database, or the test steps aside.
     *
     * <p>Deliberately not a {@code @SpringBootTest}: the whole
     * {@code integration/**} package is excluded from compilation in the POM,
     * so a Boot context test here would silently never run - which is exactly
     * what happened to the first version of this file. A plain DataSource
     * exercises the same SQL against the same schema and always runs.
     *
     * <p>Credentials come from the environment. A machine without a local
     * database skips these rather than failing, so the suite stays green on a
     * fresh checkout.
     */
    @BeforeEach
    void connect() {
        String url = System.getenv().getOrDefault("MYSQL_URL",
                "jdbc:mysql://127.0.0.1:3306/gioitruyen?useUnicode=true&characterEncoding=utf8"
                        + "&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false");
        String user = System.getenv().getOrDefault("MYSQL_USER", "gioitruyen");
        String password = System.getenv().getOrDefault("MYSQL_PASSWORD", "");

        DriverManagerDataSource source = new DriverManagerDataSource(url, user, password);
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");

        try (var probe = source.getConnection()) {
            Assumptions.assumeTrue(probe.isValid(2), "Không kết nối được MySQL cục bộ");
        } catch (Exception unavailable) {
            Assumptions.abort("Bỏ qua: không có MySQL cục bộ (" + unavailable.getMessage() + ")");
        }

        jdbc = JdbcClient.create((DataSource) source);
        service = new PrQuestService(jdbc);

        Assumptions.assumeTrue(
                jdbc.sql("""
                                SELECT COUNT(1) FROM information_schema.TABLES
                                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pr_quests'
                                """)
                        .query(Long.class).single() > 0,
                "Bỏ qua: CSDL cục bộ chưa chạy migration V29");

        seed();
    }

    void seed() {
        owner = UUID.randomUUID();
        creator = UUID.randomUUID();
        team = UUID.randomUUID();
        story = UUID.randomUUID();

        insertUser(owner, "pr-owner");
        insertUser(creator, "pr-creator");

        jdbc.sql("""
                INSERT INTO teams (id, slug, name, description, status, created_by)
                VALUES (?, ?, 'PR Test Team', 'fixture', 'ACTIVE', ?)
                """)
                .params(team.toString(), "pr-test-" + team, owner.toString())
                .update();

        jdbc.sql("""
                INSERT INTO stories (id, team_id, created_by, title, slug, status)
                VALUES (?, ?, ?, 'Truyện test PR', ?, 'PUBLISHED')
                """)
                .params(story.toString(), team.toString(), owner.toString(), "pr-test-" + story)
                .update();
    }

    @AfterEach
    void cleanUp() {
        // The connect step may have aborted before anything was created.
        if (jdbc == null || team == null) return;
        jdbc.sql("DELETE FROM pr_quests WHERE team_id = ?").param(team.toString()).update();
        jdbc.sql("DELETE FROM stories WHERE id = ?").param(story.toString()).update();
        jdbc.sql("DELETE FROM teams WHERE id = ?").param(team.toString()).update();
        for (UUID user : List.of(owner, creator)) {
            jdbc.sql("DELETE FROM wallet_transactions WHERE user_id = ?").param(user.toString()).update();
            jdbc.sql("DELETE FROM wallets WHERE user_id = ?").param(user.toString()).update();
            jdbc.sql("DELETE FROM users WHERE id = ?").param(user.toString()).update();
        }
    }

    /**
     * The whole loop, on the numbers the owner asked about: 60,000 a head for
     * five slots, one creator paid, everything else refunded when it closes.
     */
    @Test
    @DisplayName("publish, claim, submit, approve, close - and the coins add up at every step")
    void runsTheWholeLoop() {
        fund(owner, 1_000_000L);

        String questId = service.createDraft(team, owner, draft(60_000L, 5));
        service.publish(questId, team, owner);

        // 300,000 ngân sách + 10,000 phí đăng = 310,000. Không có phần trăm.
        assertThat(balance(owner)).isEqualTo(690_000L);
        assertThat(scalar("SELECT escrow_xu FROM pr_quests WHERE id = ?", questId)).isEqualTo(300_000L);
        assertThat(scalar("SELECT publish_fee_xu FROM pr_quests WHERE id = ?", questId)).isEqualTo(10_000L);

        String claimId = service.claim(questId, creator);
        assertThat(scalar("SELECT claimed_count FROM pr_quests WHERE id = ?", questId)).isEqualTo(1L);

        service.submit(claimId, creator, "https://tiktok.com/@t/video/1", "xong rồi", List.of());
        assertThat(status(claimId)).isEqualTo("SUBMITTED");

        service.approve(claimId, team, false);

        // Người làm nhận đủ; nền tảng không trừ gì ở bước này.
        assertThat(balance(creator)).isEqualTo(60_000L);
        assertThat(scalar("SELECT paid_xu FROM pr_quests WHERE id = ?", questId)).isEqualTo(60_000L);
        assertThat(scalar("SELECT escrow_xu FROM pr_quests WHERE id = ?", questId)).isEqualTo(240_000L);

        long refunded = service.close(questId, team, "CANCELLED");

        // Bốn suất không ai nhận, hoàn đủ.
        assertThat(refunded).isEqualTo(240_000L);
        assertThat(balance(owner)).isEqualTo(930_000L);

        // Nền tảng giữ đúng phí đăng; phần còn lại đến tay người làm.
        long ownerSpent = 1_000_000L - balance(owner);
        assertThat(ownerSpent).isEqualTo(10_000L + 60_000L);
    }

    /**
     * The rule the whole marketplace rests on: an owner who says nothing pays
     * anyway. Without it a creator has done the work and has no way to force a
     * decision.
     */
    @Test
    @DisplayName("a submission left unanswered past its deadline is paid automatically")
    void paysOutWhenTheOwnerNeverAnswers() {
        fund(owner, 200_000L);
        String questId = service.createDraft(team, owner, draft(50_000L, 1));
        service.publish(questId, team, owner);

        String claimId = service.claim(questId, creator);
        service.submit(claimId, creator, "https://tiktok.com/@t/video/2", null, List.of());

        // Wind the review deadline back, as the scheduler would find it a week on.
        jdbc.sql("UPDATE pr_quest_claims SET review_due_at = NOW(3) - INTERVAL 1 DAY WHERE id = ?")
                .param(claimId).update();

        service.runDueWork();

        assertThat(status(claimId)).isEqualTo("APPROVED");
        assertThat(balance(creator)).isEqualTo(50_000L);
        assertThat(scalar("SELECT auto_approved FROM pr_quest_claims WHERE id = ?", claimId)).isEqualTo(1L);
    }

    /** A slot held by someone who never submits has to come back to the pool. */
    @Test
    @DisplayName("a claim with nothing submitted by its deadline releases the slot")
    void releasesTheSlotWhenNothingIsSubmitted() {
        fund(owner, 200_000L);
        String questId = service.createDraft(team, owner, draft(50_000L, 1));
        service.publish(questId, team, owner);

        service.claim(questId, creator);
        assertThat(scalar("SELECT claimed_count FROM pr_quests WHERE id = ?", questId)).isEqualTo(1L);
        assertThat(statusOf(questId)).isEqualTo("FULL");

        jdbc.sql("UPDATE pr_quest_claims SET submit_due_at = NOW(3) - INTERVAL 1 DAY WHERE quest_id = ?")
                .param(questId).update();

        service.runDueWork();

        assertThat(scalar("SELECT claimed_count FROM pr_quests WHERE id = ?", questId)).isZero();
        assertThat(statusOf(questId)).isEqualTo("OPEN");
        assertThat(balance(creator)).isZero();
    }

    /**
     * The slot counter is guarded by a conditional UPDATE, so the last slot can
     * only be taken once however many callers reach for it.
     */
    @Test
    @DisplayName("a quest cannot be claimed past its slot count")
    void refusesClaimsBeyondTheSlotCount() {
        fund(owner, 200_000L);
        String questId = service.createDraft(team, owner, draft(50_000L, 1));
        service.publish(questId, team, owner);

        service.claim(questId, creator);

        UUID second = UUID.randomUUID();
        insertUser(second, "pr-second");
        try {
            assertThatThrownBy(() -> service.claim(questId, second))
                    .isInstanceOfSatisfying(ApiException.class,
                            failure -> assertThat(failure.code()).isEqualTo("pr.not_open"));
        } finally {
            jdbc.sql("DELETE FROM wallets WHERE user_id = ?").param(second.toString()).update();
            jdbc.sql("DELETE FROM users WHERE id = ?").param(second.toString()).update();
        }
    }

    /** One reader, one claim - enforced by the unique key, not by a check. */
    @Test
    @DisplayName("the same reader cannot claim one quest twice")
    void refusesADoubleClaim() {
        fund(owner, 400_000L);
        String questId = service.createDraft(team, owner, draft(50_000L, 5));
        service.publish(questId, team, owner);

        service.claim(questId, creator);
        assertThatThrownBy(() -> service.claim(questId, creator))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("pr.already_claimed"));

        // The failed attempt must not have consumed a second slot.
        assertThat(scalar("SELECT claimed_count FROM pr_quests WHERE id = ?", questId)).isEqualTo(1L);
    }

    /** Publishing without the coins to cover it must not open the quest. */
    @Test
    @DisplayName("publishing is refused when the wallet cannot cover it, and nothing changes")
    void refusesToPublishWithoutFunds() {
        fund(owner, 1_000L);
        String questId = service.createDraft(team, owner, draft(50_000L, 5));

        assertThatThrownBy(() -> service.publish(questId, team, owner))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("pr.insufficient_balance"));

        assertThat(balance(owner)).isEqualTo(1_000L);
        assertThat(statusOf(questId)).isEqualTo("DRAFT");
    }

    /**
     * Stopping a campaign closes the door to new claimants; it does not cancel
     * the person already filming. Their slot's coins stay reserved.
     */
    @Test
    @DisplayName("stopping a campaign keeps the money for slots already taken")
    void keepsCommittedMoneyWhenStopped() {
        fund(owner, 500_000L);
        String questId = service.createDraft(team, owner, draft(50_000L, 3));
        service.publish(questId, team, owner);
        String claimId = service.claim(questId, creator);

        long refunded = service.close(questId, team, "CANCELLED");

        // Hai suất chưa ai nhận quay về; suất thứ ba vẫn được giữ.
        assertThat(refunded).isEqualTo(100_000L);
        assertThat(scalar("SELECT escrow_xu FROM pr_quests WHERE id = ?", questId)).isEqualTo(50_000L);

        // And the creator can still finish and be paid from what was held.
        service.submit(claimId, creator, "https://tiktok.com/@t/video/3", null, List.of());
        service.approve(claimId, team, false);
        assertThat(balance(creator)).isEqualTo(50_000L);
        assertThat(scalar("SELECT escrow_xu FROM pr_quests WHERE id = ?", questId)).isZero();
    }

    /** An APPLY quest holds no slot until the owner picks someone. */
    @Test
    @DisplayName("an application takes a slot only when the owner accepts it")
    void applyQuestsHoldNoSlotUntilAccepted() {
        fund(owner, 200_000L);
        String questId = service.createDraft(team, owner,
                new QuestDraft("APPLY", "YOUTUBE", "PR YouTube", "Video 5 phút", null, null,
                        story.toString(), 50_000L, 1, 10));
        service.publish(questId, team, owner);

        String claimId = service.claim(questId, creator);
        assertThat(status(claimId)).isEqualTo("PENDING");
        assertThat(scalar("SELECT claimed_count FROM pr_quests WHERE id = ?", questId)).isZero();

        service.acceptApplication(claimId, team);
        assertThat(status(claimId)).isEqualTo("CLAIMED");
        assertThat(scalar("SELECT claimed_count FROM pr_quests WHERE id = ?", questId)).isEqualTo(1L);
    }

    /** Rejection is feedback: the slot stays so the work can be fixed and resent. */
    @Test
    @DisplayName("a rejected submission can be corrected and submitted again")
    void allowsResubmissionAfterRejection() {
        fund(owner, 200_000L);
        String questId = service.createDraft(team, owner, draft(50_000L, 1));
        service.publish(questId, team, owner);
        String claimId = service.claim(questId, creator);
        service.submit(claimId, creator, "https://tiktok.com/@t/video/4", null, List.of());

        service.reject(claimId, team, "Video chưa gắn link truyện", "ZALO", "0900000000");
        assertThat(status(claimId)).isEqualTo("REJECTED");
        assertThat(balance(creator)).isZero();

        service.submit(claimId, creator, "https://tiktok.com/@t/video/4b", null, List.of());
        service.approve(claimId, team, false);
        assertThat(balance(creator)).isEqualTo(50_000L);
    }

    /** Refusing without a reason or a contact leaves the creator nothing to act on. */
    @Test
    @DisplayName("a rejection must carry a reason and a way to reach the team")
    void requiresReasonAndContactOnRejection() {
        fund(owner, 200_000L);
        String questId = service.createDraft(team, owner, draft(50_000L, 1));
        service.publish(questId, team, owner);
        String claimId = service.claim(questId, creator);
        service.submit(claimId, creator, "https://tiktok.com/@t/video/5", null, List.of());

        assertThatThrownBy(() -> service.reject(claimId, team, "  ", "ZALO", "0900000000"))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("pr.reason_required"));
        assertThatThrownBy(() -> service.reject(claimId, team, "Chưa đạt", "ZALO", " "))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.code()).isEqualTo("pr.contact_required"));
    }

    // ---------------------------------------------------------------- helpers

    private static QuestDraft draft(long reward, int slots) {
        return new QuestDraft("OPEN", "TIKTOK", "PR TikTok",
                "Video 1000+ view, gắn link truyện", "ZALO", "0900000000",
                null, reward, slots, 10);
    }

    private void insertUser(UUID id, String prefix) {
        jdbc.sql("""
                INSERT INTO users (id, email, username, display_name, role, status)
                VALUES (?, ?, ?, ?, 'READER', 'ACTIVE')
                """)
                .params(id.toString(), prefix + "-" + id + "@test.local",
                        prefix + "-" + id, prefix)
                .update();
        jdbc.sql("INSERT INTO wallets (id, user_id, coin_balance) VALUES (?, ?, 0)")
                .params(UUID.randomUUID().toString(), id.toString())
                .update();
    }

    private void fund(UUID user, long amount) {
        jdbc.sql("UPDATE wallets SET coin_balance = ? WHERE user_id = ?")
                .params(amount, user.toString())
                .update();
    }

    private long balance(UUID user) {
        return jdbc.sql("SELECT coin_balance FROM wallets WHERE user_id = ?")
                .param(user.toString()).query(Long.class).single();
    }

    private long scalar(String sql, String id) {
        return jdbc.sql(sql).param(id).query(Long.class).optional().orElse(0L);
    }

    private String status(String claimId) {
        return jdbc.sql("SELECT status FROM pr_quest_claims WHERE id = ?")
                .param(claimId).query(String.class).single();
    }

    private String statusOf(String questId) {
        return jdbc.sql("SELECT status FROM pr_quests WHERE id = ?")
                .param(questId).query(String.class).single();
    }
}
