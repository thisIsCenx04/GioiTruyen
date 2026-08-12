package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.dto.ChapterUnlockResponse;
import com.storyplatform.monetization.application.dto.DonationRequest;
import com.storyplatform.monetization.application.dto.DonationResponse;
import com.storyplatform.monetization.application.dto.WalletResponse;
import com.storyplatform.shared.api.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonetizationFlowService {

    private static final BigDecimal DEFAULT_PURCHASE_FEE_RATE = new BigDecimal("0.20");
    private static final BigDecimal DEFAULT_DONATION_FEE_RATE = new BigDecimal("0.10");

    private final NamedParameterJdbcTemplate jdbc;

    public MonetizationFlowService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public WalletResponse wallet(UUID userId) {
        return jdbc.query(
                        """
                                SELECT coin_balance, gem_balance, updated_at
                                FROM wallets
                                WHERE user_id = :userId
                                LIMIT 1
                                """,
                        Map.of("userId", userId.toString()),
                        (rs, rowNum) -> wallet(rs)
                ).stream()
                .findFirst()
                .orElse(new WalletResponse(0, 0, null));
    }

    @Transactional
    public ChapterUnlockResponse unlockChapter(UUID userId, UUID chapterId) {
        Optional<ChapterUnlockResponse> existing = existingUnlock(userId, chapterId);
        if (existing.isPresent()) {
            return existing.get();
        }
        ChapterPrice chapter = chapter(chapterId);
        if (chapter.coinPrice() == 0) {
            createFreeUnlock(userId, chapter);
            return new ChapterUnlockResponse(chapter.id(), chapter.storyId(), 0, currentCoinBalance(userId), false);
        }
        WalletBalance wallet = lockWallet(userId);
        if (wallet.coinBalance() < chapter.coinPrice()) {
            throw new ApiException(HttpStatus.CONFLICT, "wallet.insufficient_coin", "Insufficient coin", "Not enough coin to unlock this chapter");
        }

        long newBalance = wallet.coinBalance() - chapter.coinPrice();
        updateCoinBalance(userId, newBalance);

        UUID orderId = UUID.randomUUID();
        UUID unlockId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        Instant now = Instant.now();
        long platformFee = fee(chapter.coinPrice(), DEFAULT_PURCHASE_FEE_RATE, "chapter_unlock");
        long teamNet = chapter.coinPrice() - platformFee;

        jdbc.update(
                """
                        INSERT INTO purchase_orders (id, user_id, story_id, purchase_type, total_coin, created_at)
                        VALUES (:id, :userId, :storyId, 'SINGLE_CHAPTER', :totalCoin, :createdAt)
                        """,
                new MapSqlParameterSource()
                        .addValue("id", orderId.toString())
                        .addValue("userId", userId.toString())
                        .addValue("storyId", chapter.storyId().toString())
                        .addValue("totalCoin", chapter.coinPrice())
                        .addValue("createdAt", now)
        );
        jdbc.update(
                "INSERT INTO purchase_order_items (order_id, chapter_id, coin_price) VALUES (:orderId, :chapterId, :coinPrice)",
                new MapSqlParameterSource()
                        .addValue("orderId", orderId.toString())
                        .addValue("chapterId", chapter.id().toString())
                        .addValue("coinPrice", chapter.coinPrice())
        );
        jdbc.update(
                """
                        INSERT INTO chapter_unlocks (id, user_id, chapter_id, purchase_order_id, coin_paid, created_at)
                        VALUES (:id, :userId, :chapterId, :orderId, :coinPaid, :createdAt)
                        """,
                new MapSqlParameterSource()
                        .addValue("id", unlockId.toString())
                        .addValue("userId", userId.toString())
                        .addValue("chapterId", chapter.id().toString())
                        .addValue("orderId", orderId.toString())
                        .addValue("coinPaid", chapter.coinPrice())
                        .addValue("createdAt", now)
        );
        insertWalletTransaction(transactionId, userId, "PURCHASE", -chapter.coinPrice(), newBalance, "PURCHASE_ORDER", orderId, "Unlock chapter", now);
        insertTeamLedger(ledgerId, chapter.teamId(), "STORY_PURCHASE", chapter.coinPrice(), platformFee, teamNet, "CHAPTER_UNLOCK", unlockId, now);
        incrementTeamRevenue(chapter.teamId(), teamNet);
        return new ChapterUnlockResponse(chapter.id(), chapter.storyId(), chapter.coinPrice(), newBalance, false);
    }

    @Transactional
    public DonationResponse donate(UUID userId, UUID teamId, DonationRequest request) {
        WalletBalance wallet = lockWallet(userId);
        if (wallet.coinBalance() < request.coinAmount()) {
            throw new ApiException(HttpStatus.CONFLICT, "wallet.insufficient_coin", "Insufficient coin", "Not enough coin to donate");
        }
        if (request.storyId() != null) {
            verifyStoryBelongsToTeam(request.storyId(), teamId);
        }
        long newBalance = wallet.coinBalance() - request.coinAmount();
        long platformFee = fee(request.coinAmount(), DEFAULT_DONATION_FEE_RATE, "donation");
        long teamNet = request.coinAmount() - platformFee;
        UUID donationId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        Instant now = Instant.now();

        updateCoinBalance(userId, newBalance);
        jdbc.update(
                """
                        INSERT INTO donations (
                            id, user_id, team_id, story_id, gross_coin, commission_rate, commission_coin,
                            team_net_coin, message, created_at
                        )
                        VALUES (
                            :id, :userId, :teamId, :storyId, :grossCoin, :commissionRate, :commissionCoin,
                            :teamNetCoin, :message, :createdAt
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("id", donationId.toString())
                        .addValue("userId", userId.toString())
                        .addValue("teamId", teamId.toString())
                        .addValue("storyId", request.storyId() == null ? null : request.storyId().toString())
                        .addValue("grossCoin", request.coinAmount())
                        .addValue("commissionRate", feeRate("donation", DEFAULT_DONATION_FEE_RATE).multiply(new BigDecimal("100")))
                        .addValue("commissionCoin", platformFee)
                        .addValue("teamNetCoin", teamNet)
                        .addValue("message", request.message())
                        .addValue("createdAt", now)
        );
        insertWalletTransaction(transactionId, userId, "DONATION", -request.coinAmount(), newBalance, "DONATION", donationId, "Donate to team", now);
        insertTeamLedger(ledgerId, teamId, "DONATION", request.coinAmount(), platformFee, teamNet, "DONATION", donationId, now);
        incrementTeamRevenue(teamId, teamNet);
        return new DonationResponse(donationId, teamId, request.storyId(), request.coinAmount(), platformFee, teamNet, newBalance);
    }

    private Optional<ChapterUnlockResponse> existingUnlock(UUID userId, UUID chapterId) {
        return jdbc.query(
                        """
                                SELECT c.id, c.story_id, cu.coin_paid
                                FROM chapter_unlocks cu
                                JOIN chapters c ON c.id = cu.chapter_id
                                WHERE cu.user_id = :userId AND cu.chapter_id = :chapterId
                                LIMIT 1
                                """,
                        new MapSqlParameterSource()
                                .addValue("userId", userId.toString())
                                .addValue("chapterId", chapterId.toString()),
                        (rs, rowNum) -> new ChapterUnlockResponse(
                                UUID.fromString(rs.getString("id")),
                                UUID.fromString(rs.getString("story_id")),
                                rs.getLong("coin_paid"),
                                currentCoinBalance(userId),
                                true
                        )
                ).stream()
                .findFirst();
    }

    private void createFreeUnlock(UUID userId, ChapterPrice chapter) {
        jdbc.update(
                """
                        INSERT INTO chapter_unlocks (id, user_id, chapter_id, purchase_order_id, coin_paid, created_at)
                        VALUES (:id, :userId, :chapterId, NULL, 0, :createdAt)
                        ON DUPLICATE KEY UPDATE coin_paid = coin_paid
                        """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("userId", userId.toString())
                        .addValue("chapterId", chapter.id().toString())
                        .addValue("createdAt", Instant.now())
        );
    }

    private ChapterPrice chapter(UUID chapterId) {
        return jdbc.query(
                        """
                                SELECT c.id, c.story_id, c.coin_price, s.team_id
                                FROM chapters c
                                JOIN stories s ON s.id = c.story_id
                                WHERE c.id = :chapterId AND c.status = 'PUBLISHED'
                                LIMIT 1
                                """,
                        Map.of("chapterId", chapterId.toString()),
                        (rs, rowNum) -> new ChapterPrice(
                                UUID.fromString(rs.getString("id")),
                                UUID.fromString(rs.getString("story_id")),
                                UUID.fromString(rs.getString("team_id")),
                                rs.getLong("coin_price")
                        )
                ).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "chapter.not_found", "Chapter not found", "Chapter not found"));
    }

    private WalletBalance lockWallet(UUID userId) {
        return jdbc.query(
                        "SELECT coin_balance, gem_balance FROM wallets WHERE user_id = :userId FOR UPDATE",
                        Map.of("userId", userId.toString()),
                        (rs, rowNum) -> new WalletBalance(rs.getLong("coin_balance"), rs.getLong("gem_balance"))
                ).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "wallet.not_found", "Wallet not found", "Wallet not found"));
    }

    private long currentCoinBalance(UUID userId) {
        return jdbc.queryForObject("SELECT coin_balance FROM wallets WHERE user_id = :userId", Map.of("userId", userId.toString()), Long.class);
    }

    private void updateCoinBalance(UUID userId, long newBalance) {
        jdbc.update(
                "UPDATE wallets SET coin_balance = :coinBalance, updated_at = :updatedAt WHERE user_id = :userId",
                new MapSqlParameterSource()
                        .addValue("coinBalance", newBalance)
                        .addValue("updatedAt", Instant.now())
                        .addValue("userId", userId.toString())
        );
    }

    private void insertWalletTransaction(UUID id, UUID userId, String type, long amount, long balanceAfter, String referenceType, UUID referenceId, String description, Instant now) {
        jdbc.update(
                """
                        INSERT INTO wallet_transactions (
                            id, user_id, currency, type, amount, balance_after, reference_type,
                            reference_id, description, created_at
                        )
                        VALUES (
                            :id, :userId, 'COIN', :type, :amount, :balanceAfter, :referenceType,
                            :referenceId, :description, :createdAt
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id.toString())
                        .addValue("userId", userId.toString())
                        .addValue("type", type)
                        .addValue("amount", amount)
                        .addValue("balanceAfter", balanceAfter)
                        .addValue("referenceType", referenceType)
                        .addValue("referenceId", referenceId.toString())
                        .addValue("description", description)
                        .addValue("createdAt", now)
        );
    }

    private void insertTeamLedger(UUID id, UUID teamId, String type, long grossCoin, long platformFee, long netCoin, String referenceType, UUID referenceId, Instant now) {
        jdbc.update(
                """
                        INSERT INTO team_ledger (
                            id, team_id, type, gross_coin, platform_fee_coin, net_coin,
                            reference_type, reference_id, created_at
                        )
                        VALUES (
                            :id, :teamId, :type, :grossCoin, :platformFee, :netCoin,
                            :referenceType, :referenceId, :createdAt
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id.toString())
                        .addValue("teamId", teamId.toString())
                        .addValue("type", type)
                        .addValue("grossCoin", grossCoin)
                        .addValue("platformFee", platformFee)
                        .addValue("netCoin", netCoin)
                        .addValue("referenceType", referenceType)
                        .addValue("referenceId", referenceId.toString())
                        .addValue("createdAt", now)
        );
    }

    /**
     * Moves a team's earnings into the owner's wallet, and updates the team's
     * running total.
     *
     * <p>The cache on the teams row is a reporting figure, not money anyone can
     * spend. Updating only that left donations and chapter sales visible in the
     * team's statistics while the owner's balance never moved, so nothing could
     * actually be withdrawn or spent.
     */
    private void incrementTeamRevenue(UUID teamId, long netCoin) {
        jdbc.update(
                "UPDATE teams SET revenue_coin_cache = revenue_coin_cache + :netCoin, updated_at = :updatedAt WHERE id = :teamId",
                new MapSqlParameterSource()
                        .addValue("netCoin", netCoin)
                        .addValue("updatedAt", Instant.now())
                        .addValue("teamId", teamId.toString())
        );

        if (netCoin <= 0) {
            return;
        }

        String ownerId = jdbc.query(
                        "SELECT created_by FROM teams WHERE id = :teamId",
                        new MapSqlParameterSource("teamId", teamId.toString()),
                        (rs, rowNum) -> rs.getString("created_by"))
                .stream()
                .findFirst()
                .orElse(null);
        if (ownerId == null) {
            return;
        }

        // A team created before wallets existed may have none; create on demand
        // rather than silently dropping the earnings.
        jdbc.update(
                """
                        INSERT IGNORE INTO wallets (id, user_id, coin_balance, gem_balance, updated_at)
                        VALUES (:id, :userId, 0, 0, NOW())
                        """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("userId", ownerId)
        );
        jdbc.update(
                """
                        UPDATE wallets SET coin_balance = coin_balance + :netCoin, updated_at = NOW()
                        WHERE user_id = :userId
                        """,
                new MapSqlParameterSource()
                        .addValue("netCoin", netCoin)
                        .addValue("userId", ownerId)
        );

        Long balance = jdbc.queryForObject(
                "SELECT coin_balance FROM wallets WHERE user_id = :userId",
                new MapSqlParameterSource("userId", ownerId),
                Long.class
        );
        jdbc.update(
                """
                        INSERT INTO wallet_transactions
                            (id, user_id, currency, type, amount, balance_after,
                             reference_type, reference_id, description, created_at)
                        VALUES (:id, :userId, 'COIN', 'EARNING', :amount, :balanceAfter,
                                'TEAM_EARNING', :teamId, 'Doanh thu nhóm', NOW())
                        """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("userId", ownerId)
                        .addValue("amount", netCoin)
                        .addValue("balanceAfter", balance == null ? netCoin : balance)
                        .addValue("teamId", teamId.toString())
        );
    }

    private void verifyStoryBelongsToTeam(UUID storyId, UUID teamId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stories WHERE id = :storyId AND team_id = :teamId",
                new MapSqlParameterSource()
                        .addValue("storyId", storyId.toString())
                        .addValue("teamId", teamId.toString()),
                Integer.class
        );
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "donation.story_team_mismatch", "Story does not belong to team", "Story does not belong to team");
        }
    }

    private long fee(long gross, BigDecimal fallbackRate, String key) {
        return feeRate(key, fallbackRate)
                .multiply(BigDecimal.valueOf(gross))
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    private BigDecimal feeRate(String key, BigDecimal fallback) {
        return jdbc.queryForList(
                        """
                                SELECT JSON_UNQUOTE(JSON_EXTRACT(value, :path))
                                FROM site_settings
                                WHERE `key` = 'monetization.platform_fee_rate'
                                LIMIT 1
                                """,
                        Map.of("path", "$." + key),
                        String.class
                ).stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(BigDecimal::new)
                .orElse(fallback);
    }

    private WalletResponse wallet(ResultSet rs) throws SQLException {
        return new WalletResponse(
                rs.getLong("coin_balance"),
                rs.getLong("gem_balance"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private record WalletBalance(long coinBalance, long gemBalance) {
    }

    private record ChapterPrice(UUID id, UUID storyId, UUID teamId, long coinPrice) {
    }
}
