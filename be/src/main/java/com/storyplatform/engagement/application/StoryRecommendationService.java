package com.storyplatform.engagement.application;

import com.storyplatform.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spending gems to recommend a story ("đề cử").
 *
 * <p>The gem total a story has collected decides where it sits in the
 * recommendation ranking, and that is all readers ever see of it: the ordering
 * is public, the amount is not. Showing the figure would turn the board into a
 * spending leaderboard and tell everyone how much any one story had been paid
 * for, so only the admin dashboard reads the total.
 */
@Service
public class StoryRecommendationService {

    /** Enough to mean something, small enough to be an ordinary gesture. */
    private static final long MINIMUM_GEMS = 1;
    private static final long MAXIMUM_GEMS = 1_000_000;

    private final JdbcClient jdbc;

    public StoryRecommendationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** What the reader gets back: their own gift and remaining balance, never the story's total. */
    public record RecommendationReceipt(String id, long gemAmount, long gemBalance) {
    }

    @Transactional
    public RecommendationReceipt recommend(String storyId, String userId, long gemAmount) {
        if (gemAmount < MINIMUM_GEMS || gemAmount > MAXIMUM_GEMS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "recommendation.amount_invalid",
                    "Invalid amount",
                    "Số ngọc đề cử phải từ %d đến %d.".formatted(MINIMUM_GEMS, MAXIMUM_GEMS));
        }
        requirePublishedStory(storyId);

        // Guarded debit: the WHERE clause carries the balance check, so two
        // concurrent recommendations cannot both pass a "do they have enough"
        // read and overdraw the wallet between them.
        int debited = jdbc.sql("""
                        UPDATE wallets
                        SET gem_balance = gem_balance - :amount, updated_at = NOW()
                        WHERE user_id = :userId AND gem_balance >= :amount
                        """)
                .param("amount", gemAmount)
                .param("userId", userId)
                .update();
        if (debited == 0) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "recommendation.insufficient_gems",
                    "Insufficient gems", "Bạn không đủ ngọc để đề cử. Hãy nạp thêm.");
        }

        long balanceAfter = jdbc.sql("SELECT gem_balance FROM wallets WHERE user_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .single();

        String recommendationId = UUID.randomUUID().toString();
        jdbc.sql("""
                        INSERT INTO story_recommendations (id, user_id, story_id, gem_amount, created_at)
                        VALUES (:id, :userId, :storyId, :amount, NOW())
                        """)
                .param("id", recommendationId)
                .param("userId", userId)
                .param("storyId", storyId)
                .param("amount", gemAmount)
                .update();

        // The ranking sorts on this column, so it has to move with the gift.
        jdbc.sql("""
                        UPDATE stories
                        SET recommendation_gem_cache = recommendation_gem_cache + :amount
                        WHERE id = :storyId
                        """)
                .param("amount", gemAmount)
                .param("storyId", storyId)
                .update();

        jdbc.sql("""
                        INSERT INTO wallet_transactions
                            (id, user_id, currency, type, amount, balance_after,
                             reference_type, reference_id, description, created_at)
                        VALUES (:id, :userId, 'GEM', 'RECOMMENDATION', :amount, :balanceAfter,
                                'STORY_RECOMMENDATION', :recommendationId, :description, NOW())
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("userId", userId)
                // Negative: the ledger reads as money leaving the wallet.
                .param("amount", -gemAmount)
                .param("balanceAfter", balanceAfter)
                .param("recommendationId", recommendationId)
                .param("description", "Đề cử truyện")
                .update();

        jdbc.sql("""
                        INSERT INTO story_daily_stats (story_id, stat_date, recommendations)
                        VALUES (:storyId, CURDATE(), :amount)
                        ON DUPLICATE KEY UPDATE recommendations = recommendations + VALUES(recommendations)
                        """)
                .param("storyId", storyId)
                .param("amount", gemAmount)
                .update();

        return new RecommendationReceipt(recommendationId, gemAmount, balanceAfter);
    }

    /** How much this reader has given a story, which is theirs to see. */
    public long myContribution(String storyId, String userId) {
        Long total = jdbc.sql("""
                        SELECT COALESCE(SUM(gem_amount), 0) FROM story_recommendations
                        WHERE story_id = :storyId AND user_id = :userId
                        """)
                .param("storyId", storyId)
                .param("userId", userId)
                .query(Long.class)
                .single();
        return total == null ? 0 : total;
    }

    private void requirePublishedStory(String storyId) {
        Long rows = jdbc.sql("SELECT COUNT(*) FROM stories WHERE id = :storyId AND status = 'PUBLISHED'")
                .param("storyId", storyId)
                .query(Long.class)
                .single();
        if (rows == null || rows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "story.not_found",
                    "Story not found", "Không tìm thấy truyện này.");
        }
    }
}
