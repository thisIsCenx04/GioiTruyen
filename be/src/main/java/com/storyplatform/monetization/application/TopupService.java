package com.storyplatform.monetization.application;

import com.storyplatform.shared.api.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buying coins: pick a package, pick a method, get payment details back.
 *
 * <p>A top-up starts PENDING and only credits the wallet when an admin confirms
 * the money arrived. Every request carries a unique transaction code that the
 * payer must copy into the transfer note, which is what lets the admin match a
 * bank line to an account.
 */
@Service
public class TopupService {

    private final JdbcClient jdbc;

    public TopupService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record DepositPackage(
            String id,
            String name,
            long priceVnd,
            long coinAmount,
            long gemAmount,
            long bonusCoin,
            long bonusGem,
            boolean active
    ) {
    }

    public record PaymentMethodView(
            String id,
            String name,
            String type,
            Map<String, Object> config,
            String instructions
    ) {
    }

    /** What the reader needs on screen to complete a transfer. */
    public record TopupInstruction(
            String paymentId,
            String transactionCode,
            long amountVnd,
            long coinAmount,
            long gemAmount,
            String methodName,
            String methodType,
            String accountName,
            String accountNumber,
            String bankName,
            String transferNote,
            String qrImageUrl,
            String qrPayload,
            String paypalLink,
            String instructions,
            String status
    ) {
    }

    public record TopupHistoryRow(
            String id,
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

    @Transactional(readOnly = true)
    public List<DepositPackage> packages() {
        return jdbc.sql("""
                        SELECT id, name, price_vnd, coin_amount, gem_amount,
                               bonus_coin, bonus_gem, is_active
                        FROM deposit_packages
                        WHERE is_active = TRUE
                        ORDER BY price_vnd
                        """)
                .query((rs, rowNum) -> new DepositPackage(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getLong("price_vnd"),
                        rs.getLong("coin_amount"),
                        rs.getLong("gem_amount"),
                        rs.getLong("bonus_coin"),
                        rs.getLong("bonus_gem"),
                        rs.getBoolean("is_active")))
                .list();
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodView> methods() {
        return jdbc.sql("""
                        SELECT id, name, type, config, instructions
                        FROM payment_methods
                        WHERE is_active = TRUE
                        ORDER BY sort_order, name
                        """)
                .query((rs, rowNum) -> new PaymentMethodView(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("type"),
                        JsonConfig.parse(rs.getString("config")),
                        rs.getString("instructions")))
                .list();
    }

    /**
     * Opens a top-up and returns everything needed to pay it. Nothing is
     * credited here - that waits for an admin to confirm the transfer.
     */
    @Transactional
    public TopupInstruction createTopup(UUID userId, String packageId, String methodId) {
        DepositPackage pack = packages().stream()
                .filter(candidate -> candidate.id().equals(packageId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "topup.invalid_package",
                        "Unknown package", "Gói nạp không tồn tại hoặc đã ngừng bán."));

        PaymentMethodView method = methods().stream()
                .filter(candidate -> candidate.id().equals(methodId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "topup.invalid_method",
                        "Unknown payment method", "Phương thức thanh toán không khả dụng."));

        String paymentId = UUID.randomUUID().toString();
        // Short, unambiguous, and free of characters a bank note would strip.
        String transactionCode = "GT" + paymentId.replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);

        long coin = pack.coinAmount() + pack.bonusCoin();
        long gem = pack.gemAmount() + pack.bonusGem();

        jdbc.sql("""
                        INSERT INTO payments
                            (id, user_id, payment_method_id, deposit_package_id, amount_vnd,
                             coin_received, gem_received, transaction_code, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', NOW())
                        """)
                .params(paymentId, userId.toString(), methodId, packageId, pack.priceVnd(),
                        coin, gem, transactionCode)
                .update();

        return instruction(paymentId, transactionCode, pack, method, coin, gem, "PENDING");
    }

    /** Re-reads an existing top-up, so a reader can reopen the payment screen. */
    @Transactional(readOnly = true)
    public TopupInstruction getTopup(UUID userId, String paymentId) {
        Payment payment = jdbc.sql("""
                        SELECT id, deposit_package_id, payment_method_id, transaction_code,
                               coin_received, gem_received, status
                        FROM payments
                        WHERE id = ? AND user_id = ?
                        """)
                .params(paymentId, userId.toString())
                .query((rs, rowNum) -> new Payment(
                        rs.getString("id"),
                        rs.getString("deposit_package_id"),
                        rs.getString("payment_method_id"),
                        rs.getString("transaction_code"),
                        rs.getLong("coin_received"),
                        rs.getLong("gem_received"),
                        rs.getString("status")))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "topup.not_found",
                        "Top-up not found", "Không tìm thấy yêu cầu nạp này."));

        DepositPackage pack = packages().stream()
                .filter(candidate -> candidate.id().equals(payment.packageId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "topup.package_gone",
                        "Package removed", "Gói nạp của yêu cầu này đã bị gỡ."));
        PaymentMethodView method = methods().stream()
                .filter(candidate -> candidate.id().equals(payment.methodId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "topup.method_gone",
                        "Method removed", "Phương thức thanh toán của yêu cầu này đã bị gỡ."));

        return instruction(payment.id(), payment.transactionCode(), pack, method,
                payment.coin(), payment.gem(), payment.status());
    }

    @Transactional(readOnly = true)
    public List<TopupHistoryRow> history(UUID userId) {
        return jdbc.sql("""
                        SELECT p.id, p.transaction_code, p.amount_vnd, p.coin_received,
                               p.gem_received, p.status, p.created_at, p.paid_at,
                               m.name AS method_name
                        FROM payments p
                        LEFT JOIN payment_methods m ON m.id = p.payment_method_id
                        WHERE p.user_id = ?
                        ORDER BY p.created_at DESC
                        LIMIT 50
                        """)
                .param(userId.toString())
                .query((rs, rowNum) -> new TopupHistoryRow(
                        rs.getString("id"),
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

    /** Assembles the payment screen, generating a VietQR when the method is a bank. */
    private TopupInstruction instruction(
            String paymentId, String transactionCode, DepositPackage pack,
            PaymentMethodView method, long coin, long gem, String status) {

        Map<String, Object> config = method.config();
        String accountNumber = text(config.get("accountNumber"));
        String accountName = text(config.get("accountName"));
        String bankBin = text(config.get("bankBin"));
        String bankName = text(config.get("bank"));
        String note = "%s %s".formatted("NAPXU", transactionCode);

        String qrImageUrl = null;
        String qrPayload = null;
        // A bank with a Napas id can carry the amount and note inside the QR, so
        // the payer never types them and the admin can match on the code alone.
        if (!bankBin.isBlank() && !accountNumber.isBlank()) {
            qrPayload = VietQrCodec.payload(bankBin, accountNumber, pack.priceVnd(), note);
            qrImageUrl = VietQrCodec.imageUrl(bankBin, accountNumber, pack.priceVnd(), note, accountName);
        } else if (!text(config.get("qrImageUrl")).isBlank()) {
            qrImageUrl = text(config.get("qrImageUrl"));
        }

        return new TopupInstruction(
                paymentId,
                transactionCode,
                pack.priceVnd(),
                coin,
                gem,
                method.name(),
                method.type(),
                accountName,
                accountNumber,
                bankName,
                note,
                qrImageUrl,
                qrPayload,
                text(config.get("paypalMeLink")),
                method.instructions(),
                status
        );
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record Payment(
            String id, String packageId, String methodId, String transactionCode,
            long coin, long gem, String status) {
    }
}
