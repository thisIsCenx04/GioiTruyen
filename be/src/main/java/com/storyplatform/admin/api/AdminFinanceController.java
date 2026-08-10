package com.storyplatform.admin.api;

import com.storyplatform.admin.application.dto.AdminDtos.AdminCashFlowRow;
import com.storyplatform.admin.application.dto.AdminDtos.CreateCashFlowRequest;
import com.storyplatform.admin.application.dto.AdminDtos.ReverseCashFlowRequest;
import com.storyplatform.shared.api.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/finance")
public class AdminFinanceController {

    /**
     * Mirrors the wallet_transactions.type column enum defined in the schema.
     */
    private static final Set<String> ENTRY_TYPES = Set.of(
            "DEPOSIT", "PURCHASE", "DONATION", "RECOMMENDATION",
            "DAILY_REWARD", "REFERRAL_REWARD", "REFUND", "ADMIN_ADJUSTMENT");

    private static final String LIST_SQL = """
            SELECT wt.id, wt.type, wt.amount, wt.reference_type, wt.reference_id,
                   wt.description, wt.user_id, wt.created_at,
                   u.email AS user_email
            FROM wallet_transactions wt
            LEFT JOIN users u ON u.id = wt.user_id
            ORDER BY wt.created_at DESC
            LIMIT 200
            """;

    private final JdbcClient jdbc;

    public AdminFinanceController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/cash-flow")
    @Transactional(readOnly = true)
    public List<AdminCashFlowRow> list() {
        return jdbc.sql(LIST_SQL).query(AdminFinanceController::mapRow).list();
    }

    @PostMapping("/cash-flow")
    @Transactional
    public AdminCashFlowRow create(@RequestBody CreateCashFlowRequest request) {
        AdminCategoryController.requireText(request.userId(), "userId");
        if (request.amountXu() == null || request.amountXu() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "cashflow.invalid_amount",
                    "Invalid amount", "amountXu must be a non-zero value");
        }

        UUID userId = AdminStoryController.parseUuid(request.userId(), "userId");
        String entryType = normalizeEntryType(request.entryType());
        long amount = request.amountXu();

        long balanceAfter = applyToWallet(userId, amount);
        UUID id = UUID.randomUUID();

        jdbc.sql("""
                        INSERT INTO wallet_transactions
                            (id, user_id, currency, type, amount, balance_after,
                             reference_type, reference_id, description, created_at)
                        VALUES (?, ?, 'COIN', ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id.toString(), userId.toString(), entryType, amount, balanceAfter,
                        AdminStoryController.blankToNull(request.referenceType()),
                        normalizeReferenceId(request.referenceId()),
                        AdminStoryController.blankToNull(request.description()),
                        Timestamp.from(Instant.now()))
                .update();

        return findRow(id);
    }

    /**
     * Reverses an entry by appending a compensating transaction; the original row
     * is kept so the ledger stays append-only and auditable.
     */
    @PostMapping("/cash-flow/{id}/reverse")
    @Transactional
    public AdminCashFlowRow reverse(@PathVariable UUID id, @RequestBody(required = false) ReverseCashFlowRequest request) {
        AdminCashFlowRow original = jdbc.sql("""
                        SELECT wt.id, wt.type, wt.amount, wt.reference_type, wt.reference_id,
                               wt.description, wt.user_id, wt.created_at, u.email AS user_email
                        FROM wallet_transactions wt
                        LEFT JOIN users u ON u.id = wt.user_id
                        WHERE wt.id = ?
                        """)
                .param(id.toString())
                .query(AdminFinanceController::mapRow)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "cashflow.not_found",
                        "Entry not found", "Cash flow entry not found"));

        UUID userId = UUID.fromString(original.userId());
        long reversalAmount = -original.amountXu();
        long balanceAfter = applyToWallet(userId, reversalAmount);

        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "Reversal of " + id
                : request.reason().trim();

        UUID reversalId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO wallet_transactions
                            (id, user_id, currency, type, amount, balance_after,
                             reference_type, reference_id, description, created_at)
                        VALUES (?, ?, 'COIN', 'ADMIN_ADJUSTMENT', ?, ?, 'REVERSAL', ?, ?, ?)
                        """)
                .params(reversalId.toString(), userId.toString(), reversalAmount, balanceAfter,
                        id.toString(), reason, Timestamp.from(Instant.now()))
                .update();

        return findRow(reversalId);
    }

    /**
     * Wallet balances are unsigned in the schema, so a debit that would overdraw
     * is rejected before the ledger row is written.
     */
    private long applyToWallet(UUID userId, long amount) {
        long current = jdbc.sql("SELECT COALESCE(coin_balance, 0) FROM wallets WHERE user_id = ?")
                .param(userId.toString())
                .query(Long.class)
                .optional()
                .orElseGet(() -> {
                    long userExists = jdbc.sql("SELECT COUNT(*) FROM users WHERE id = ?")
                            .param(userId.toString()).query(Long.class).optional().orElse(0L);
                    if (userExists == 0) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "cashflow.invalid_user",
                                "Unknown user", "userId does not match an existing user");
                    }
                    jdbc.sql("INSERT INTO wallets (id, user_id, coin_balance, gem_balance, updated_at) "
                                    + "VALUES (?, ?, 0, 0, ?)")
                            .params(UUID.randomUUID().toString(), userId.toString(), Timestamp.from(Instant.now()))
                            .update();
                    return 0L;
                });

        long updated = current + amount;
        if (updated < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "cashflow.insufficient_balance",
                    "Insufficient balance",
                    "Wallet balance " + current + " cannot absorb an adjustment of " + amount);
        }

        jdbc.sql("UPDATE wallets SET coin_balance = ?, updated_at = ? WHERE user_id = ?")
                .params(updated, Timestamp.from(Instant.now()), userId.toString())
                .update();
        return updated;
    }

    private AdminCashFlowRow findRow(UUID id) {
        return jdbc.sql("""
                        SELECT wt.id, wt.type, wt.amount, wt.reference_type, wt.reference_id,
                               wt.description, wt.user_id, wt.created_at, u.email AS user_email
                        FROM wallet_transactions wt
                        LEFT JOIN users u ON u.id = wt.user_id
                        WHERE wt.id = ?
                        """)
                .param(id.toString())
                .query(AdminFinanceController::mapRow)
                .single();
    }

    private static String normalizeEntryType(String entryType) {
        if (entryType == null || entryType.isBlank()) {
            return "ADMIN_ADJUSTMENT";
        }
        String candidate = entryType.trim().toUpperCase(Locale.ROOT);
        if (!ENTRY_TYPES.contains(candidate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "cashflow.invalid_type",
                    "Invalid entry type", entryType + " is not a supported transaction type");
        }
        return candidate;
    }

    /** reference_id is a BINARY/CHAR UUID column, so free-text references are dropped. */
    private static String normalizeReferenceId(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(referenceId.trim()).toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static AdminCashFlowRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AdminCashFlowRow(
                rs.getString("id"),
                rs.getString("type"),
                rs.getLong("amount"),
                AdminCategoryController.nullToEmpty(rs.getString("reference_type")),
                AdminCategoryController.nullToEmpty(rs.getString("reference_id")),
                AdminCategoryController.nullToEmpty(rs.getString("description")),
                AdminCategoryController.nullToEmpty(rs.getString("user_id")),
                AdminCategoryController.nullToEmpty(rs.getString("user_email")),
                rs.getTimestamp("created_at") == null
                        ? null : rs.getTimestamp("created_at").toInstant().toString()
        );
    }
}
