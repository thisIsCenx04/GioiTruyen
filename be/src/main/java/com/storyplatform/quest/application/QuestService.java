package com.storyplatform.quest.application;

import com.storyplatform.quest.application.dto.QuestDtos;
import com.storyplatform.shared.api.ApiException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily quests. Every signed-in reader gets the same active catalogue; progress
 * is tracked per user per day and resets at local midnight.
 *
 * <p>Progress arrives as small increments (a minute of reading, one share) and
 * is folded into a single counter row rather than an event log, so an active
 * reader costs one UPDATE a minute instead of an ever-growing table.
 */
@Service
public class QuestService {

    /** Quest days roll over at Vietnamese midnight, not UTC. */
    private static final ZoneId QUEST_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Largest increment accepted from one progress call. The client pings once a
     * minute, so anything larger is a stale or forged batch and is clamped.
     */
    private static final int MAX_INCREMENT = 5;

    private final JdbcClient jdbc;

    public QuestService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Quest types an admin may choose, with the rule that measures each. */
    public List<QuestDtos.QuestTypeOption> questTypes() {
        return List.of(
                new QuestDtos.QuestTypeOption("LOGIN_DAILY", "Điểm danh hằng ngày", "lần",
                        "Tự hoàn thành ngay khi người dùng mở trang nhiệm vụ trong ngày."),
                new QuestDtos.QuestTypeOption("READ_MINUTES", "Đọc truyện theo phút", "phút",
                        "Đếm thời gian ở trang đọc chương, mỗi phút một nhịp."),
                new QuestDtos.QuestTypeOption("ONLINE_MINUTES", "Online theo phút", "phút",
                        "Đếm thời gian mở website ở bất kỳ trang nào."),
                new QuestDtos.QuestTypeOption("READ_CHAPTERS", "Đọc số chương", "chương",
                        "Tăng 1 mỗi khi mở một chương mới."),
                new QuestDtos.QuestTypeOption("SHARE_STORY", "Chia sẻ truyện", "lần",
                        "Tăng 1 khi bấm nút chia sẻ lên mạng xã hội."),
                new QuestDtos.QuestTypeOption("COMMENT_STORY", "Bình luận truyện", "bình luận",
                        "Tăng 1 khi gửi một bình luận.")
        );
    }

    /**
     * Today's quests for one reader. Rows are created lazily on first read, so a
     * user who never opens the page costs nothing.
     */
    @Transactional
    public QuestDtos.QuestBoard board(UUID userId) {
        LocalDate today = LocalDate.now(QUEST_ZONE);

        // Opening the board is the act of checking in.
        awardLoginQuest(userId, today);

        List<QuestDtos.QuestProgress> quests = jdbc.sql("""
                        SELECT q.id, q.quest_type, q.title, q.description, q.target_value,
                               q.reward_coin, q.reward_gem,
                               COALESCE(p.progress_value, 0) AS progress_value,
                               p.completed_at, p.claimed_at
                        FROM quest_definitions q
                        LEFT JOIN user_quest_progress p
                               ON p.quest_id = q.id AND p.user_id = ? AND p.quest_date = ?
                        WHERE q.is_active = TRUE
                        ORDER BY q.sort_order, q.title
                        """)
                .params(userId.toString(), java.sql.Date.valueOf(today))
                .query((rs, rowNum) -> {
                    int target = rs.getInt("target_value");
                    int progress = rs.getInt("progress_value");
                    return new QuestDtos.QuestProgress(
                            rs.getString("id"),
                            rs.getString("quest_type"),
                            rs.getString("title"),
                            rs.getString("description"),
                            target,
                            Math.min(progress, target),
                            rs.getInt("reward_coin"),
                            rs.getInt("reward_gem"),
                            rs.getTimestamp("completed_at") != null,
                            rs.getTimestamp("claimed_at") != null
                    );
                })
                .list();

        long completed = quests.stream().filter(QuestDtos.QuestProgress::completed).count();
        return new QuestDtos.QuestBoard(today.toString(), (int) completed, quests.size(), quests);
    }

    /**
     * The active quest catalogue with zero progress, for a signed-out visitor.
     * No rows are written, so browsing the page costs nothing.
     */
    @Transactional(readOnly = true)
    public QuestDtos.QuestBoard preview() {
        List<QuestDtos.QuestProgress> quests = jdbc.sql("""
                        SELECT id, quest_type, title, description, target_value,
                               reward_coin, reward_gem
                        FROM quest_definitions
                        WHERE is_active = TRUE
                        ORDER BY sort_order, title
                        """)
                .query((rs, rowNum) -> new QuestDtos.QuestProgress(
                        rs.getString("id"),
                        rs.getString("quest_type"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("target_value"),
                        0,
                        rs.getInt("reward_coin"),
                        rs.getInt("reward_gem"),
                        false,
                        false))
                .list();
        return new QuestDtos.QuestBoard(
                LocalDate.now(QUEST_ZONE).toString(), 0, quests.size(), quests);
    }

    /**
     * Adds progress to every active quest of a type. One ping can advance several
     * quests at once - a minute of reading counts toward the 5, 15, 30 and 60
     * minute quests together.
     */
    @Transactional
    public QuestDtos.QuestBoard recordProgress(UUID userId, String questType, Integer amount) {
        String type = questType == null ? "" : questType.trim().toUpperCase(java.util.Locale.ROOT);
        boolean known = questTypes().stream().anyMatch(option -> option.value().equals(type));
        if (!known) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "quest.unknown_type",
                    "Unknown quest type", "Loại nhiệm vụ không hợp lệ.");
        }

        int increment = Math.max(1, Math.min(amount == null ? 1 : amount, MAX_INCREMENT));
        LocalDate today = LocalDate.now(QUEST_ZONE);

        // One ping advances every quest of this type at once: a minute of reading
        // counts toward the 5, 15, 30 and 60 minute quests together.
        for (QuestTarget quest : activeQuestsOfType(type)) {
            bumpProgress(userId, quest.id(), today, increment, quest.target());
        }
        return board(userId);
    }

    /** Pays out a completed, unclaimed quest exactly once. */
    @Transactional
    public QuestDtos.ClaimResult claim(UUID userId, String questId) {
        LocalDate today = LocalDate.now(QUEST_ZONE);

        ClaimableQuest quest = jdbc.sql("""
                        SELECT p.id, p.claimed_at, p.progress_value,
                               q.target_value, q.reward_coin, q.reward_gem, q.title
                        FROM user_quest_progress p
                        JOIN quest_definitions q ON q.id = p.quest_id
                        WHERE p.user_id = ? AND p.quest_id = ? AND p.quest_date = ?
                        FOR UPDATE
                        """)
                .params(userId.toString(), questId, java.sql.Date.valueOf(today))
                .query((rs, rowNum) -> new ClaimableQuest(
                        rs.getString("id"),
                        rs.getTimestamp("claimed_at") != null,
                        rs.getInt("progress_value"),
                        rs.getInt("target_value"),
                        rs.getInt("reward_coin"),
                        rs.getInt("reward_gem"),
                        rs.getString("title")))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "quest.not_started",
                        "Quest not started", "Nhiệm vụ này chưa được bắt đầu hôm nay."));

        if (quest.claimed()) {
            throw new ApiException(HttpStatus.CONFLICT, "quest.already_claimed",
                    "Already claimed", "Bạn đã nhận thưởng nhiệm vụ này rồi.");
        }
        if (quest.progress() < quest.target()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "quest.incomplete",
                    "Quest incomplete",
                    "Nhiệm vụ chưa hoàn thành (%d/%d).".formatted(quest.progress(), quest.target()));
        }

        jdbc.sql("UPDATE user_quest_progress SET claimed_at = NOW(3), updated_at = NOW(3) WHERE id = ?")
                .param(quest.progressId())
                .update();

        long[] balances = credit(userId, quest.rewardCoin(), quest.rewardGem(), questId, quest.title());
        return new QuestDtos.ClaimResult(
                questId, quest.rewardCoin(), quest.rewardGem(), balances[0], balances[1]);
    }

    /** LOGIN_DAILY completes on sight; there is nothing else to measure. */
    private void awardLoginQuest(UUID userId, LocalDate today) {
        for (QuestTarget quest : activeQuestsOfType("LOGIN_DAILY")) {
            bumpProgress(userId, quest.id(), today, 1, quest.target());
        }
    }

    private List<QuestTarget> activeQuestsOfType(String questType) {
        return jdbc.sql("""
                        SELECT id, target_value FROM quest_definitions
                        WHERE is_active = TRUE AND quest_type = ?
                        """)
                .param(questType)
                .query((rs, rowNum) -> new QuestTarget(rs.getString("id"), rs.getInt("target_value")))
                .list();
    }

    private record QuestTarget(String id, int target) {
    }

    private record ClaimableQuest(
            String progressId,
            boolean claimed,
            int progress,
            int target,
            int rewardCoin,
            int rewardGem,
            String title
    ) {
    }

    /**
     * Upserts today's counter and stamps completion the moment it reaches target.
     * The unique key on (user, quest, day) makes this safe under concurrent pings.
     */
    private void bumpProgress(UUID userId, String questId, LocalDate day, int increment, int target) {
        jdbc.sql("""
                        INSERT INTO user_quest_progress
                            (id, user_id, quest_id, quest_date, progress_value, completed_at)
                        VALUES (?, ?, ?, ?, ?, IF(? >= ?, NOW(3), NULL))
                        ON DUPLICATE KEY UPDATE
                            progress_value = LEAST(progress_value + VALUES(progress_value), ?),
                            completed_at = IF(completed_at IS NULL
                                              AND progress_value + VALUES(progress_value) >= ?,
                                              NOW(3), completed_at),
                            updated_at = NOW(3)
                        """)
                .params(UUID.randomUUID().toString(), userId.toString(), questId,
                        java.sql.Date.valueOf(day), increment, increment, target, target, target)
                .update();
    }

    /** Credits the wallet and records the movement. Returns {coin, gem} balances. */
    private long[] credit(UUID userId, int coin, int gem, String questId, String questTitle) {
        // The wallet row is created at registration, but a seeded account may
        // predate that; INSERT IGNORE keeps the claim working either way.
        jdbc.sql("""
                        INSERT IGNORE INTO wallets (id, user_id, coin_balance, gem_balance, updated_at)
                        VALUES (?, ?, 0, 0, NOW())
                        """)
                .params(UUID.randomUUID().toString(), userId.toString())
                .update();

        if (coin > 0 || gem > 0) {
            jdbc.sql("""
                            UPDATE wallets
                            SET coin_balance = coin_balance + ?,
                                gem_balance = gem_balance + ?,
                                updated_at = NOW()
                            WHERE user_id = ?
                            """)
                    .params(coin, gem, userId.toString())
                    .update();
        }

        long[] wallet = jdbc.sql(
                        "SELECT coin_balance, gem_balance FROM wallets WHERE user_id = ?")
                .param(userId.toString())
                .query((rs, rowNum) -> new long[] { rs.getLong("coin_balance"), rs.getLong("gem_balance") })
                .single();
        long coinBalance = wallet[0];
        long gemBalance = wallet[1];

        recordTransaction(userId, "COIN", coin, coinBalance, questId, questTitle);
        recordTransaction(userId, "GEM", gem, gemBalance, questId, questTitle);
        return new long[] { coinBalance, gemBalance };
    }

    private void recordTransaction(
            UUID userId, String currency, int amount, long balanceAfter, String questId, String title) {
        if (amount <= 0) {
            // wallet_transactions.amount is CHECK (amount <> 0).
            return;
        }
        jdbc.sql("""
                        INSERT INTO wallet_transactions
                            (id, user_id, currency, type, amount, balance_after,
                             reference_type, reference_id, description, created_at)
                        VALUES (?, ?, ?, 'DAILY_REWARD', ?, ?, 'QUEST', ?, ?, NOW())
                        """)
                .params(UUID.randomUUID().toString(), userId.toString(), currency, amount,
                        balanceAfter, questId, "Thưởng nhiệm vụ: " + title)
                .update();
    }
}
