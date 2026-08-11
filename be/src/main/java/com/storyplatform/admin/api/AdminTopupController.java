package com.storyplatform.admin.api;

import com.storyplatform.shared.api.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Confirming that money arrived. Transfers are matched by hand against the
 * transaction code the payer put in the note, so crediting is an admin action
 * rather than something a webhook decides.
 */
@RestController
@RequestMapping("/admin/topups")
public class AdminTopupController {

    private final JdbcClient jdbc;

    public AdminTopupController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record AdminTopupRow(
            String id,
            String userEmail,
            String transactionCode,
            long amountVnd,
            long coinReceived,
            long gemReceived,
            String methodName,
            String status,
            String createdAt,
            String paidAt
    ) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminTopupRow> list(@RequestParam(required = false) String status) {
        String filter = status == null || status.isBlank() ? null
                : status.trim().toUpperCase(java.util.Locale.ROOT);
        return jdbc.sql("""
                        SELECT p.id, p.transaction_code, p.amount_vnd, p.coin_received,
                               p.gem_received, p.status, p.created_at, p.paid_at,
                               u.email AS user_email, m.name AS method_name
                        FROM payments p
                        JOIN users u ON u.id = p.user_id
                        LEFT JOIN payment_methods m ON m.id = p.payment_method_id
                        WHERE (? IS NULL OR p.status = ?)
                        ORDER BY p.created_at DESC
                        LIMIT 200
                        """)
                .params(filter, filter)
                .query((rs, rowNum) -> new AdminTopupRow(
                        rs.getString("id"),
                        rs.getString("user_email"),
                        rs.getString("transaction_code"),
                        rs.getLong("amount_vnd"),
                        rs.getLong("coin_received"),
                        rs.getLong("gem_received"),
                        rs.getString("method_name"),
                        rs.getString("status"),
                        String.valueOf(rs.getTimestamp("created_at")),
                        rs.getTimestamp("paid_at") == null ? null : String.valueOf(rs.getTimestamp("paid_at"))))
                .list();
    }

    /**
     * Marks a top-up paid and moves the coins and gems into the wallet.
     *
     * <p>The row is locked and re-checked inside the transaction, so pressing
     * the button twice cannot credit twice.
     */
    @PostMapping("/{paymentId}/approve")
    @Transactional
    public AdminTopupRow approve(@PathVariable String paymentId) {
        Pending pending = jdbc.sql("""
                        SELECT user_id, coin_received, gem_received, status, transaction_code
                        FROM payments WHERE id = ? FOR UPDATE
                        """)
                .param(paymentId)
                .query((rs, rowNum) -> new Pending(
                        rs.getString("user_id"),
                        rs.getLong("coin_received"),
                        rs.getLong("gem_received"),
                        rs.getString("status"),
                        rs.getString("transaction_code")))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "topup.not_found",
                        "Top-up not found", "Không tìm thấy yêu cầu nạp này."));

        if ("PAID".equals(pending.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "topup.already_paid",
                    "Already credited", "Yêu cầu này đã được cộng xu rồi.");
        }
        if (!"PENDING".equals(pending.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "topup.not_pending",
                    "Not pending",
                    "Yêu cầu đang ở trạng thái %s, không thể duyệt.".formatted(pending.status()));
        }

        jdbc.sql("UPDATE payments SET status = 'PAID', paid_at = NOW() WHERE id = ?")
                .param(paymentId).update();

        // A wallet row normally exists from registration; a seeded account may
        // predate that, so it is created on demand rather than failing here.
        jdbc.sql("""
                        INSERT IGNORE INTO wallets (id, user_id, coin_balance, gem_balance, updated_at)
                        VALUES (?, ?, 0, 0, NOW())
                        """)
                .params(UUID.randomUUID().toString(), pending.userId())
                .update();

        jdbc.sql("""
                        UPDATE wallets
                        SET coin_balance = coin_balance + ?, gem_balance = gem_balance + ?, updated_at = NOW()
                        WHERE user_id = ?
                        """)
                .params(pending.coin(), pending.gem(), pending.userId())
                .update();

        long[] balances = jdbc.sql("SELECT coin_balance, gem_balance FROM wallets WHERE user_id = ?")
                .param(pending.userId())
                .query((rs, rowNum) -> new long[] { rs.getLong("coin_balance"), rs.getLong("gem_balance") })
                .single();

        recordTransaction(pending.userId(), "COIN", pending.coin(), balances[0], paymentId, pending.code());
        recordTransaction(pending.userId(), "GEM", pending.gem(), balances[1], paymentId, pending.code());

        return list(null).stream()
                .filter(row -> row.id().equals(paymentId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "topup.not_found",
                        "Top-up not found", "Không tìm thấy yêu cầu nạp này."));
    }

    /** Rejects a transfer that never arrived. Nothing is credited. */
    @PostMapping("/{paymentId}/reject")
    @Transactional
    public void reject(@PathVariable String paymentId) {
        int updated = jdbc.sql(
                        "UPDATE payments SET status = 'CANCELLED' WHERE id = ? AND status = 'PENDING'")
                .param(paymentId)
                .update();
        if (updated == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "topup.not_pending",
                    "Not pending", "Chỉ có thể hủy yêu cầu đang chờ xử lý.");
        }
    }

    private void recordTransaction(
            String userId, String currency, long amount, long balanceAfter, String paymentId, String code) {
        if (amount <= 0) {
            // wallet_transactions.amount is CHECK (amount <> 0).
            return;
        }
        jdbc.sql("""
                        INSERT INTO wallet_transactions
                            (id, user_id, currency, type, amount, balance_after,
                             reference_type, reference_id, description, created_at)
                        VALUES (?, ?, ?, 'DEPOSIT', ?, ?, 'TOPUP', ?, ?, NOW())
                        """)
                .params(UUID.randomUUID().toString(), userId, currency, amount, balanceAfter,
                        paymentId, "Nạp xu - mã " + code)
                .update();
    }

    private record Pending(String userId, long coin, long gem, String status, String code) {
    }
}
