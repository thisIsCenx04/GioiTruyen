package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.dto.ChapterUnlockResponse;
import com.storyplatform.monetization.application.dto.ComboPurchaseResponse;
import com.storyplatform.monetization.application.dto.DonationRequest;
import com.storyplatform.monetization.application.dto.DonationResponse;
import com.storyplatform.monetization.application.dto.WalletResponse;
import com.storyplatform.shared.api.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonetizationFlowService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(MonetizationFlowService.class);

    private static final BigDecimal DEFAULT_PURCHASE_FEE_RATE = new BigDecimal("0.20");
    private static final BigDecimal DEFAULT_DONATION_FEE_RATE = new BigDecimal("0.10");

    private final NamedParameterJdbcTemplate jdbc;
    private final long withdrawalMinimumGrossXu;
    private final long withdrawalFreeFromGrossXu;
    private final long withdrawalFlatFeeXu;
    private final long withdrawalMaximumGrossXu;

    public MonetizationFlowService(
            NamedParameterJdbcTemplate jdbc,
            @Value("${app.monetization.withdrawals.minimum-gross-xu:100000}") long withdrawalMinimumGrossXu,
            @Value("${app.monetization.withdrawals.free-from-gross-xu:1000000}") long withdrawalFreeFromGrossXu,
            @Value("${app.monetization.withdrawals.flat-fee-xu:20000}") long withdrawalFlatFeeXu,
            @Value("${app.monetization.withdrawals.maximum-gross-xu:1000000000}") long withdrawalMaximumGrossXu
    ) {
        this.jdbc = jdbc;
        this.withdrawalMinimumGrossXu = withdrawalMinimumGrossXu;
        this.withdrawalFreeFromGrossXu = withdrawalFreeFromGrossXu;
        this.withdrawalFlatFeeXu = withdrawalFlatFeeXu;
        this.withdrawalMaximumGrossXu = withdrawalMaximumGrossXu;
    }

    public record WalletTransactionRow(
            UUID id,
            String currency,
            String type,
            long amount,
            long balanceAfter,
            String referenceType,
            UUID referenceId,
            String description,
            Instant createdAt
    ) {}

    public record WithdrawalRequest(
            String accountName,
            String accountNumber,
            String bankName,
            long grossAmountXu
    ) {}

    public record WithdrawalReceipt(
            UUID id,
            UUID teamId,
            String accountName,
            String bankName,
            String destinationMasked,
            long grossAmountXu,
            long feeXu,
            long netAmountXu,
            String state,
            /** Lời của quản trị viên. Huỷ thì bắt buộc có, người rút đọc được. */
            String adminNote,
            /** Mã giao dịch ngân hàng, để người rút đối chiếu với sao kê. */
            String transferReference,
            /** Lời của người rút khi báo chưa nhận được tiền. */
            String confirmNote,
            Instant reviewedAt,
            Instant paidAt,
            Instant confirmedAt,
            Instant createdAt
    ) {}

    public record WithdrawalPage(List<WithdrawalReceipt> items, String nextCursor) {}

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

    @Transactional(readOnly = true)
    public List<WalletTransactionRow> walletTransactions(UUID userId, boolean admin) {
        if (!admin) {
            requireOwnedTeam(userId);
        }
        return jdbc.query(
                """
                        SELECT id, currency, type, amount, balance_after, reference_type,
                               reference_id, description, created_at
                        FROM wallet_transactions
                        WHERE user_id = :userId
                        ORDER BY created_at DESC
                        LIMIT 200
                        """,
                Map.of("userId", userId.toString()),
                (rs, rowNum) -> new WalletTransactionRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("currency"),
                        rs.getString("type"),
                        rs.getLong("amount"),
                        rs.getLong("balance_after"),
                        rs.getString("reference_type"),
                        rs.getString("reference_id") == null ? null : UUID.fromString(rs.getString("reference_id")),
                        rs.getString("description"),
                        rs.getTimestamp("created_at").toInstant()
                )
        );
    }

    @Transactional(readOnly = true)
    public WithdrawalPage withdrawals(UUID userId, boolean admin, String cursor) {
        if (!admin) {
            requireOwnedTeam(userId);
        }
        Instant before = parseCursor(cursor);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId.toString())
                .addValue("before", before == null ? null : java.sql.Timestamp.from(before))
                .addValue("limit", 51);

        List<WithdrawalReceipt> rows = jdbc.query(
                """
                        SELECT id, team_id, account_name, bank_name, account_number,
                               gross_amount_xu, fee_xu, net_amount_xu, state, admin_note,
                               transfer_reference, confirm_note, reviewed_at, paid_at,
                               confirmed_at, created_at
                        FROM withdrawal_requests
                        WHERE user_id = :userId
                          AND (:before IS NULL OR created_at < :before)
                        ORDER BY created_at DESC
                        LIMIT :limit
                        """,
                params,
                (rs, rowNum) -> withdrawal(rs)
        );
        boolean hasMore = rows.size() > 50;
        List<WithdrawalReceipt> items = hasMore ? rows.subList(0, 50) : rows;
        String nextCursor = hasMore ? items.get(items.size() - 1).createdAt().toString() : null;
        return new WithdrawalPage(items, nextCursor);
    }

    @Transactional
    public WithdrawalReceipt createWithdrawal(UUID userId, boolean admin, WithdrawalRequest request) {
        UUID teamId = admin ? ownerTeamId(userId).orElse(null) : requireOwnedTeam(userId);
        String accountName = requireText(request.accountName(), "Tên trên thẻ");
        String accountNumber = requireText(request.accountNumber(), "Số tài khoản");
        String bankName = requireText(request.bankName(), "Ngân hàng");
        if (accountName.length() > 160 || accountNumber.length() > 80 || bankName.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "withdrawal.destination_too_long",
                    "Destination too long", "Thông tin nhận tiền vượt quá giới hạn cho phép.");
        }

        long gross = request.grossAmountXu();
        if (gross < withdrawalMinimumGrossXu || gross > withdrawalMaximumGrossXu) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "withdrawal.amount_invalid",
                    "Invalid withdrawal amount",
                    "Số xu rút phải từ %d đến %d.".formatted(withdrawalMinimumGrossXu, withdrawalMaximumGrossXu));
        }
        long fee = gross >= withdrawalFreeFromGrossXu ? 0 : withdrawalFlatFeeXu;
        long net = gross - fee;
        if (net <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "withdrawal.net_amount_invalid",
                    "Invalid withdrawal net amount", "Số xu nhận sau phí phải lớn hơn 0.");
        }

        WalletBalance wallet = lockWallet(userId);
        if (wallet.coinBalance() < gross) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "withdrawal.insufficient_balance",
                    "Insufficient balance", "Số dư xu không đủ để tạo yêu cầu rút.");
        }

        long newBalance = wallet.coinBalance() - gross;
        UUID withdrawalId = UUID.randomUUID();
        Instant now = Instant.now();
        updateCoinBalance(userId, newBalance);

        jdbc.update(
                """
                        INSERT INTO withdrawal_requests
                            (id, user_id, team_id, account_name, account_number, bank_name,
                             gross_amount_xu, fee_xu, net_amount_xu, state, created_at, updated_at)
                        VALUES
                            (:id, :userId, :teamId, :accountName, :accountNumber, :bankName,
                             :gross, :fee, :net, 'PENDING_REVIEW', :createdAt, :updatedAt)
                        """,
                new MapSqlParameterSource()
                        .addValue("id", withdrawalId.toString())
                        .addValue("userId", userId.toString())
                        .addValue("teamId", teamId == null ? null : teamId.toString())
                        .addValue("accountName", accountName)
                        .addValue("accountNumber", accountNumber)
                        .addValue("bankName", bankName)
                        .addValue("gross", gross)
                        .addValue("fee", fee)
                        .addValue("net", net)
                        .addValue("createdAt", now)
                        .addValue("updatedAt", now)
        );
        insertWalletTransaction(UUID.randomUUID(), userId, "WITHDRAWAL", -gross, newBalance,
                "WITHDRAWAL_REQUEST", withdrawalId, "Yêu cầu rút tiền", now);

        return findWithdrawal(withdrawalId);
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
        notifyTeamOfPurchase(chapter.teamId(), chapter.storyId(), teamNet);
        return new ChapterUnlockResponse(chapter.id(), chapter.storyId(), chapter.coinPrice(), newBalance, false);
    }

    public boolean hasPurchasedStoryCombo(UUID userId, UUID storyId) {
        if (userId == null || storyId == null) return false;
        Integer count = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM story_combo_purchases WHERE user_id = ? AND story_id = ?",
                Integer.class,
                userId.toString(),
                storyId.toString()
        );
        return count != null && count > 0;
    }

    /**
     * Tells the publishing team a chapter of theirs was bought.
     *
     * <p>Goes to every active member, since any of them may be the one watching
     * the inbox. The buyer is deliberately not named: who reads what is the
     * reader's business, and the team only needs to know a sale happened and
     * what it earned.
     *
     * <p>Failures are swallowed. The coins have already moved by the time this
     * runs, and losing a notification must not roll back a completed purchase
     * or fail the reader's unlock.
     */
    private void notifyTeamOfPurchase(UUID teamId, UUID storyId, long teamNetCoin) {
        try {
            String title = jdbc.queryForObject(
                    "SELECT title FROM stories WHERE id = :storyId",
                    new MapSqlParameterSource("storyId", storyId.toString()),
                    String.class
            );
            jdbc.update(
                    """
                            INSERT INTO notifications
                                (id, user_id, type, title, message, target_type, target_id, target_url)
                            SELECT UUID(), tm.user_id, 'PURCHASE', :title, :message,
                                   'STORY', :storyId, :url
                            FROM team_members tm
                            WHERE tm.team_id = :teamId AND tm.status = 'ACTIVE'
                            """,
                    new MapSqlParameterSource()
                            .addValue("title", "Có độc giả mua chương")
                            .addValue("message", "Một chương của \"%s\" vừa được mua. Nhóm nhận %d xu."
                                    .formatted(title == null ? "truyện của bạn" : title, teamNetCoin))
                            .addValue("storyId", storyId.toString())
                            .addValue("url", "/teams/" + teamId + "/dashboard")
                            .addValue("teamId", teamId.toString())
            );
        } catch (Exception exception) {
            log.warn("Could not notify team {} of a chapter purchase", teamId, exception);
        }
    }

    private record StoryComboInfo(UUID storyId, UUID teamId, long comboPriceXu) {}

    /**
     * What a combo costs and whether that is actually a discount.
     *
     * <p>The client used to derive all of this itself - guessing that the first
     * three chapters are free, hard-coding 10 Xu per chapter and applying a 30%
     * default discount - so the price on screen had no connection to the chapter
     * prices in the database or to what the purchase endpoint would charge.
     * These are the real figures.
     *
     * @param chapterTotalXu what the chapters cost bought one by one
     * @param comboPriceXu what the combo charges
     * @param configured true when someone set a combo price on purpose; false
     *        means the combo is simply the sum, and no discount should be shown
     * @param discountPercent 0 unless a configured price undercuts the sum
     */
    public record ComboPricing(
            long chapterTotalXu,
            long comboPriceXu,
            boolean configured,
            int discountPercent
    ) {}

    @Transactional(readOnly = true)
    public ComboPricing comboPricing(UUID storyId) {
        Long configured = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COALESCE(combo_price_xu, 0) FROM stories WHERE id = ?",
                Long.class,
                storyId.toString()
        );
        long configuredPrice = configured == null ? 0L : configured;
        long chapterTotal = chapterTotalCoin(storyId);
        long price = configuredPrice > 0 ? configuredPrice : chapterTotal;

        // A configured price above the sum is not a discount, so it reports 0
        // rather than a negative saving.
        int discount = 0;
        if (configuredPrice > 0 && chapterTotal > 0 && configuredPrice < chapterTotal) {
            discount = (int) Math.round((chapterTotal - configuredPrice) * 100.0 / chapterTotal);
        }
        return new ComboPricing(chapterTotal, price, configuredPrice > 0, discount);
    }

    /** Sum of what the chapters cost individually - the combo's list price. */
    private long chapterTotalCoin(UUID storyId) {
        Long total = jdbc.getJdbcTemplate().queryForObject(
                "SELECT COALESCE(SUM(coin_price), 0) FROM chapters WHERE story_id = ?",
                Long.class,
                storyId.toString()
        );
        return total == null ? 0L : total;
    }

    /**
     * The price a combo actually charges.
     *
     * <p>An unset {@code combo_price_xu} means "no bundle deal": the combo costs
     * exactly what the chapters add up to. It used to quietly charge
     * {@code max(10, round(sum * 0.7))}, which invented a 30% discount nobody
     * configured, billed 10 Xu for a story whose chapters were all free, and fell
     * back to 100 Xu when the sum was absent. A discount now only exists when
     * someone sets a lower total on purpose.
     */
    private long comboPriceFor(UUID storyId, long configuredPrice) {
        return configuredPrice > 0 ? configuredPrice : chapterTotalCoin(storyId);
    }

    @Transactional
    public ComboPurchaseResponse purchaseStoryCombo(UUID userId, UUID storyId) {
        if (hasPurchasedStoryCombo(userId, storyId)) {
            return new ComboPurchaseResponse(storyId, 0, currentCoinBalance(userId), true);
        }

        StoryComboInfo storyInfo = jdbc.getJdbcTemplate().query(
                "SELECT id, team_id, combo_price_xu FROM stories WHERE id = ?",
                (rs, rowNum) -> new StoryComboInfo(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("team_id") != null ? UUID.fromString(rs.getString("team_id")) : null,
                        rs.getObject("combo_price_xu") != null ? rs.getLong("combo_price_xu") : 0L
                ),
                storyId.toString()
        ).stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "story.not_found", "Story not found", "Story does not exist"));

        long comboPrice = comboPriceFor(storyId, storyInfo.comboPriceXu());
        // Nothing to sell: every chapter is already free and no combo total was
        // configured. The UI hides the combo in this case, but the endpoint is
        // reachable on its own, and recording a 0 Xu "purchase" would create a
        // paid-unlock record for a story that was never behind a paywall.
        if (comboPrice <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "story.combo_not_for_sale",
                    "Story has no paid chapters",
                    "Truyện này đang miễn phí toàn bộ, không cần mua combo.");
        }

        WalletBalance wallet = lockWallet(userId);
        if (wallet.coinBalance() < comboPrice) {
            throw new ApiException(HttpStatus.CONFLICT, "wallet.insufficient_coin", "Insufficient coin balance", "Not enough coin to buy story combo");
        }

        long newBalance = wallet.coinBalance() - comboPrice;
        updateCoinBalance(userId, newBalance);

        UUID orderId = UUID.randomUUID();
        UUID comboPurchaseId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        Instant now = Instant.now();

        long platformFee = fee(comboPrice, DEFAULT_PURCHASE_FEE_RATE, "story_combo");
        long teamNet = comboPrice - platformFee;

        jdbc.update(
                """
                -- purchase_type is an enum of SINGLE_CHAPTER / CHAPTER_RANGE /
                -- FULL_STORY. This wrote 'STORY_COMBO', which is not one of them,
                -- so MySQL rejected the row ("Data truncated for column
                -- 'purchase_type'") and the whole purchase failed. Buying every
                -- chapter at once is exactly FULL_STORY.
                INSERT INTO purchase_orders (id, user_id, story_id, purchase_type, total_coin, created_at)
                VALUES (:id, :userId, :storyId, 'FULL_STORY', :totalCoin, :createdAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", orderId.toString())
                        .addValue("userId", userId.toString())
                        .addValue("storyId", storyId.toString())
                        .addValue("totalCoin", comboPrice)
                        .addValue("createdAt", now)
        );

        jdbc.update(
                """
                INSERT INTO story_combo_purchases (id, user_id, story_id, price_xu, created_at)
                VALUES (:id, :userId, :storyId, :priceXu, :createdAt)
                """,
                new MapSqlParameterSource()
                        .addValue("id", comboPurchaseId.toString())
                        .addValue("userId", userId.toString())
                        .addValue("storyId", storyId.toString())
                        .addValue("priceXu", comboPrice)
                        .addValue("createdAt", now)
        );

        insertWalletTransaction(transactionId, userId, "PURCHASE", -comboPrice, newBalance, "PURCHASE_ORDER", orderId, "Buy story full combo", now);
        if (storyInfo.teamId() != null) {
            insertTeamLedger(ledgerId, storyInfo.teamId(), "STORY_PURCHASE", comboPrice, platformFee, teamNet, "STORY_COMBO", comboPurchaseId, now);
            incrementTeamRevenue(storyInfo.teamId(), teamNet);
        }

        return new ComboPurchaseResponse(storyId, comboPrice, newBalance, true);
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
                        .addValue("referenceId", referenceId == null ? null : referenceId.toString())
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
                        """
                                SELECT user_id FROM team_members
                                WHERE team_id = :teamId AND member_role = 'OWNER' AND status = 'ACTIVE'
                                ORDER BY joined_at
                                LIMIT 1
                                """,
                        new MapSqlParameterSource("teamId", teamId.toString()),
                        (rs, rowNum) -> rs.getString("user_id"))
                .stream()
                .findFirst()
                .orElseGet(() -> jdbc.query(
                                "SELECT created_by FROM teams WHERE id = :teamId",
                                new MapSqlParameterSource("teamId", teamId.toString()),
                                (rs, rowNum) -> rs.getString("created_by"))
                        .stream()
                        .findFirst()
                        .orElse(null));
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

    private UUID requireOwnedTeam(UUID userId) {
        return ownerTeamId(userId).orElseThrow(() -> new ApiException(
                HttpStatus.FORBIDDEN,
                "team.owner_required",
                "Owner required",
                "Bạn không có quyền thao tác này."));
    }

    private Optional<UUID> ownerTeamId(UUID userId) {
        Optional<UUID> ownedByRole = jdbc.query(
                        """
                                SELECT t.id FROM team_members m
                                JOIN teams t ON t.id = m.team_id
                                WHERE m.user_id = :userId
                                  AND m.member_role = 'OWNER'
                                  AND m.status = 'ACTIVE'
                                  AND t.status = 'ACTIVE'
                                ORDER BY m.joined_at
                                LIMIT 1
                                """,
                        Map.of("userId", userId.toString()),
                        (rs, rowNum) -> UUID.fromString(rs.getString("id")))
                .stream()
                .findFirst();
        if (ownedByRole.isPresent()) {
            return ownedByRole;
        }
        return jdbc.query(
                        """
                                SELECT id FROM teams
                                WHERE created_by = :userId AND status = 'ACTIVE'
                                ORDER BY created_at
                                LIMIT 1
                                """,
                        Map.of("userId", userId.toString()),
                        (rs, rowNum) -> UUID.fromString(rs.getString("id")))
                .stream()
                .findFirst();
    }


    /* ── Duyệt rút tiền ──────────────────────────────────────────────────
       Vòng đời:

         PENDING_REVIEW ──duyệt──> APPROVED ──đã chuyển──> PAID
               │                      │                     │
               │                      │              ┌──────┴───────┐
               │                      │         xác nhận      báo chưa nhận
               │                      │              │              │
               └──────huỷ─────────────┴──> REJECTED  COMPLETED   DISPUTED
                                            (hoàn xu)                │
                                                 ▲───huỷ─────────────┤
                                                 └───chuyển lại──> PAID

       Xu bị trừ ngay lúc gửi yêu cầu, nên mọi đường dẫn tới REJECTED đều phải
       hoàn lại - nếu không người dùng mất trắng số xu của yêu cầu bị huỷ.

       Mọi phép đổi trạng thái là một câu UPDATE có điều kiện, không phải đọc
       rồi mới ghi: giữa hai bước đó một quản trị viên khác có thể vừa huỷ, và
       bản ghi sẽ bị đẩy ngược từ REJECTED về PAID. Điều kiện nằm trong mệnh đề
       WHERE nên chỉ đúng một lệnh thắng, số còn lại nhận rows = 0. */

    /** Một dòng trong hàng đợi của quản trị viên: kèm người gửi và nhóm. */
    public record AdminWithdrawalRow(
            UUID id,
            UUID userId,
            String userName,
            String userEmail,
            UUID teamId,
            String teamName,
            String accountName,
            String accountNumber,
            String bankName,
            long grossAmountXu,
            long feeXu,
            long netAmountXu,
            String state,
            String adminNote,
            String transferReference,
            String confirmNote,
            Instant reviewedAt,
            Instant paidAt,
            Instant confirmedAt,
            Instant createdAt
    ) {}

    @Transactional(readOnly = true)
    public List<AdminWithdrawalRow> adminWithdrawals(String state) {
        boolean all = state == null || state.isBlank() || "ALL".equalsIgnoreCase(state);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("state", all ? null : state);

        // Số tài khoản đầy đủ chỉ lộ ở màn hình này: quản trị viên phải gõ nó
        // vào giao diện ngân hàng, che đi thì không chuyển tiền được.
        return jdbc.query(
                """
                        SELECT w.id, w.user_id, u.display_name AS user_name, u.email AS user_email,
                               w.team_id, t.name AS team_name,
                               w.account_name, w.account_number, w.bank_name,
                               w.gross_amount_xu, w.fee_xu, w.net_amount_xu, w.state,
                               w.admin_note, w.transfer_reference, w.confirm_note,
                               w.reviewed_at, w.paid_at, w.confirmed_at, w.created_at
                        FROM withdrawal_requests w
                        JOIN users u ON u.id = w.user_id
                        LEFT JOIN teams t ON t.id = w.team_id
                        WHERE (:state IS NULL OR w.state = :state)
                        ORDER BY w.created_at DESC
                        LIMIT 200
                        """,
                params,
                (rs, rowNum) -> new AdminWithdrawalRow(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("user_name"),
                        rs.getString("user_email"),
                        rs.getString("team_id") == null ? null : UUID.fromString(rs.getString("team_id")),
                        rs.getString("team_name"),
                        rs.getString("account_name"),
                        rs.getString("account_number"),
                        rs.getString("bank_name"),
                        rs.getLong("gross_amount_xu"),
                        rs.getLong("fee_xu"),
                        rs.getLong("net_amount_xu"),
                        rs.getString("state"),
                        rs.getString("admin_note"),
                        rs.getString("transfer_reference"),
                        rs.getString("confirm_note"),
                        instantOrNull(rs, "reviewed_at"),
                        instantOrNull(rs, "paid_at"),
                        instantOrNull(rs, "confirmed_at"),
                        rs.getTimestamp("created_at").toInstant()
                )
        );
    }

    private void requireChanged(int rows, String expected) {
        if (rows != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "withdrawal.wrong_state",
                    "Withdrawal in wrong state",
                    "Yêu cầu rút không còn ở trạng thái %s. Tải lại danh sách rồi thao tác lại."
                            .formatted(expected));
        }
    }

    /** Duyệt: chấp nhận yêu cầu, chưa chuyển tiền. */
    @Transactional
    public WithdrawalReceipt approveWithdrawal(UUID adminId, UUID id, String note) {
        int rows = jdbc.update(
                """
                        UPDATE withdrawal_requests
                           SET state = 'APPROVED', admin_note = :note, reviewed_by = :adminId,
                               reviewed_at = NOW(3), updated_at = NOW(3)
                         WHERE id = :id AND state = 'PENDING_REVIEW'
                        """,
                new MapSqlParameterSource()
                        .addValue("adminId", adminId.toString())
                        .addValue("id", id.toString())
                        .addValue("note", blankToNull(note))
        );
        requireChanged(rows, "chờ duyệt");
        return findWithdrawal(id);
    }

    /**
     * Đánh dấu đã chuyển tiền. Từ đây quả bóng sang chân người rút.
     *
     * <p>DISPUTED cũng vào được đây: người rút báo chưa nhận, quản trị viên
     * chuyển lại rồi đánh dấu đã chuyển lần nữa.
     */
    @Transactional
    public WithdrawalReceipt markWithdrawalPaid(UUID adminId, UUID id, String reference, String note) {
        int rows = jdbc.update(
                """
                        UPDATE withdrawal_requests
                           SET state = 'PAID', admin_note = :note, transfer_reference = :reference,
                               reviewed_by = :adminId, reviewed_at = NOW(3), paid_at = NOW(3),
                               confirm_note = NULL, updated_at = NOW(3)
                         WHERE id = :id
                           AND state IN ('PENDING_REVIEW', 'APPROVED', 'PROCESSING', 'DISPUTED')
                        """,
                new MapSqlParameterSource()
                        .addValue("adminId", adminId.toString())
                        .addValue("id", id.toString())
                        .addValue("note", blankToNull(note))
                        .addValue("reference", blankToNull(reference))
        );
        requireChanged(rows, "đang chờ chuyển tiền");
        return findWithdrawal(id);
    }

    /**
     * Huỷ yêu cầu và hoàn xu.
     *
     * <p>Ghi chú là bắt buộc: người rút bị lấy lại một khoản họ đã yêu cầu và
     * phải biết vì sao. Một lần huỷ không lý do là một khiếu nại chắc chắn tới.
     */
    @Transactional
    public WithdrawalReceipt rejectWithdrawal(UUID adminId, UUID id, String note) {
        String reason = requireText(note, "Lý do huỷ");
        if (reason.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "withdrawal.note_too_long",
                    "Note too long", "Lý do huỷ tối đa 500 ký tự.");
        }
        int rows = jdbc.update(
                """
                        UPDATE withdrawal_requests
                           SET state = 'REJECTED', admin_note = :note, reviewed_by = :adminId,
                               reviewed_at = NOW(3), updated_at = NOW(3)
                         WHERE id = :id
                           AND state IN ('PENDING_REVIEW', 'APPROVED', 'PROCESSING', 'PAID', 'DISPUTED')
                        """,
                new MapSqlParameterSource()
                        .addValue("adminId", adminId.toString())
                        .addValue("id", id.toString())
                        .addValue("note", reason)
        );
        requireChanged(rows, "chưa chốt");
        refundWithdrawal(id);
        return findWithdrawal(id);
    }

    /**
     * Trả xu về ví, đúng một lần.
     *
     * <p>`refunded_at IS NULL` trong mệnh đề WHERE là chốt chặn: hai quản trị
     * viên cùng bấm huỷ trong một tích tắc thì chỉ một người ghi được mốc đó,
     * và người kia không cộng thêm một lần xu nữa.
     */
    private void refundWithdrawal(UUID id) {
        int claimed = jdbc.update(
                "UPDATE withdrawal_requests SET refunded_at = NOW(3) WHERE id = :id AND refunded_at IS NULL",
                Map.of("id", id.toString())
        );
        if (claimed != 1) return;

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT user_id, gross_amount_xu FROM withdrawal_requests WHERE id = :id",
                Map.of("id", id.toString())
        );
        UUID userId = UUID.fromString((String) row.get("user_id"));
        long gross = ((Number) row.get("gross_amount_xu")).longValue();

        WalletBalance wallet = lockWallet(userId);
        long newBalance = wallet.coinBalance() + gross;
        updateCoinBalance(userId, newBalance);
        insertWalletTransaction(UUID.randomUUID(), userId, "REFUND", gross, newBalance,
                "WITHDRAWAL_REQUEST", id, "Hoàn xu do yêu cầu rút bị huỷ", Instant.now());
    }

    /** Người rút xác nhận đã nhận được tiền. Đây là điểm kết của một yêu cầu. */
    @Transactional
    public WithdrawalReceipt confirmWithdrawal(UUID userId, UUID id) {
        int rows = jdbc.update(
                """
                        UPDATE withdrawal_requests
                           SET state = 'COMPLETED', confirmed_at = NOW(3), updated_at = NOW(3)
                         WHERE id = :id AND user_id = :userId AND state = 'PAID'
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id.toString())
                        .addValue("userId", userId.toString())
        );
        if (rows != 1) requireOwnWithdrawal(userId, id);
        requireChanged(rows, "đã chuyển tiền");
        return findWithdrawal(id);
    }

    /** Người rút báo chưa nhận được tiền; yêu cầu quay lại bàn quản trị viên. */
    @Transactional
    public WithdrawalReceipt disputeWithdrawal(UUID userId, UUID id, String note) {
        String reason = requireText(note, "Ghi chú");
        if (reason.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "withdrawal.note_too_long",
                    "Note too long", "Ghi chú tối đa 500 ký tự.");
        }
        int rows = jdbc.update(
                """
                        UPDATE withdrawal_requests
                           SET state = 'DISPUTED', confirm_note = :note, updated_at = NOW(3)
                         WHERE id = :id AND user_id = :userId AND state = 'PAID'
                        """,
                new MapSqlParameterSource()
                        .addValue("id", id.toString())
                        .addValue("note", reason)
                        .addValue("userId", userId.toString())
        );
        if (rows != 1) requireOwnWithdrawal(userId, id);
        requireChanged(rows, "đã chuyển tiền");
        return findWithdrawal(id);
    }

    /**
     * Yêu cầu này có thuộc về người đang gọi không.
     *
     * <p>Gọi khi câu UPDATE không đổi được dòng nào, để phân biệt hai lý do rất
     * khác nhau: yêu cầu của người khác (404) hay yêu cầu của mình nhưng sai
     * trạng thái (409). Trả về 409 cho cả hai là chỉ cho người lạ biết yêu cầu
     * đó có tồn tại.
     */
    private void requireOwnWithdrawal(UUID userId, UUID id) {
        Integer mine = jdbc.query(
                "SELECT 1 FROM withdrawal_requests WHERE id = :id AND user_id = :userId LIMIT 1",
                Map.of("id", id.toString(), "userId", userId.toString()),
                rs -> rs.next() ? 1 : null
        );
        if (mine == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "withdrawal.not_found",
                    "Withdrawal not found", "Không tìm thấy yêu cầu rút tiền.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "withdrawal.missing_field",
                    "Missing field", label + " không được để trống.");
        }
        return value.trim();
    }

    private static Instant parseCursor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (java.time.format.DateTimeParseException exception) {
            return null;
        }
    }

    private WithdrawalReceipt findWithdrawal(UUID id) {
        return jdbc.query(
                        """
                                SELECT id, team_id, account_name, bank_name, account_number,
                                       gross_amount_xu, fee_xu, net_amount_xu, state, admin_note,
                                       transfer_reference, confirm_note, reviewed_at, paid_at,
                                       confirmed_at, created_at
                                FROM withdrawal_requests
                                WHERE id = :id
                                LIMIT 1
                                """,
                        Map.of("id", id.toString()),
                        (rs, rowNum) -> withdrawal(rs))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "withdrawal.not_found", "Withdrawal not found", "Không tìm thấy yêu cầu rút tiền."));
    }

    private static WithdrawalReceipt withdrawal(ResultSet rs) throws SQLException {
        String teamId = rs.getString("team_id");
        return new WithdrawalReceipt(
                UUID.fromString(rs.getString("id")),
                teamId == null ? null : UUID.fromString(teamId),
                rs.getString("account_name"),
                rs.getString("bank_name"),
                maskAccount(rs.getString("account_number")),
                rs.getLong("gross_amount_xu"),
                rs.getLong("fee_xu"),
                rs.getLong("net_amount_xu"),
                rs.getString("state"),
                rs.getString("admin_note"),
                rs.getString("transfer_reference"),
                rs.getString("confirm_note"),
                instantOrNull(rs, "reviewed_at"),
                instantOrNull(rs, "paid_at"),
                instantOrNull(rs, "confirmed_at"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "";
        }
        String trimmed = accountNumber.trim();
        int shown = Math.min(4, trimmed.length());
        return "**** " + trimmed.substring(trimmed.length() - shown);
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
