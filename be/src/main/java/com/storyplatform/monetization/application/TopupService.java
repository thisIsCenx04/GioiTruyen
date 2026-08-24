package com.storyplatform.monetization.application;

import com.storyplatform.shared.api.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** Reader-confirmed top-ups allowed per window, per account. */
    private static final int MAX_SUBMISSIONS_PER_WINDOW = 3;
    /** Length of that window, in minutes. */
    private static final int SUBMISSION_WINDOW_MINUTES = 1;
    /** A draft nobody confirmed is reused for this long before a new one opens. */
    private static final int DRAFT_REUSE_MINUTES = 30;

    /**
     * Refuses a submission once the reader has already made
     * {@value #MAX_SUBMISSIONS_PER_WINDOW} in the current window.
     *
     * <p>The limit sits on submissions rather than on requests: a reader may
     * open the payment screen and refresh the QR as often as they like, because
     * none of that asks anyone to do anything.
     */
    public static void checkSubmissionRate(long recentSubmissions) {
        if (recentSubmissions < MAX_SUBMISSIONS_PER_WINDOW) {
            return;
        }
        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "topup.too_many_submissions",
                "Too many top-up submissions",
                ("Bạn đã gửi %d yêu cầu nạp trong %d phút vừa qua. "
                        + "Vui lòng đợi ít phút rồi thử lại, hoặc liên hệ hỗ trợ nếu yêu cầu cũ chưa được duyệt.")
                        .formatted(MAX_SUBMISSIONS_PER_WINDOW, SUBMISSION_WINDOW_MINUTES),
                Duration.ofMinutes(SUBMISSION_WINDOW_MINUTES));
    }

    /**
     * Opens a top-up and returns everything needed to pay it.
     *
     * <p>The row starts as a DRAFT, which no admin ever sees. Showing a QR code
     * is not a claim that money moved, and treating it as one meant every reload
     * of the wallet page queued another request for review. The reader turns a
     * draft into a real request with {@link #submitTopup}.
     *
     * <p>Reopening the screen with the same package and method reuses the draft
     * that is already open, so the transfer note the reader may have copied
     * stays valid.
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

        long coin = pack.coinAmount() + pack.bonusCoin();
        long gem = pack.gemAmount() + pack.bonusGem();

        Optional<String[]> reusable = jdbc.sql("""
                        SELECT id, transaction_code
                        FROM payments
                        WHERE user_id = ? AND status = 'DRAFT'
                          AND deposit_package_id = ? AND payment_method_id = ?
                          AND created_at > NOW() - INTERVAL ? MINUTE
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .params(userId.toString(), packageId, methodId, DRAFT_REUSE_MINUTES)
                .query((rs, rowNum) -> new String[] { rs.getString("id"), rs.getString("transaction_code") })
                .optional();

        if (reusable.isPresent()) {
            String[] draft = reusable.get();
            return instruction(draft[0], draft[1], pack, method, coin, gem, "DRAFT");
        }

        String paymentId = UUID.randomUUID().toString();
        // Short, unambiguous, and free of characters a bank note would strip.
        String transactionCode = "GT" + paymentId.replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);

        jdbc.sql("""
                        INSERT INTO payments
                            (id, user_id, payment_method_id, deposit_package_id, amount_vnd,
                             coin_received, gem_received, transaction_code, status, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', NOW())
                        """)
                .params(paymentId, userId.toString(), methodId, packageId, pack.priceVnd(),
                        coin, gem, transactionCode)
                .update();

        return instruction(paymentId, transactionCode, pack, method, coin, gem, "DRAFT");
    }

    /**
     * The reader states they have made the transfer, putting the request in
     * front of an admin.
     *
     * <p>This is the only path into the review queue, and the only one that is
     * rate limited: a reader may confirm at most
     * {@value #MAX_SUBMISSIONS_PER_WINDOW} transfers per
     * {@value #SUBMISSION_WINDOW_MINUTES} minute. Browsing packages and
     * regenerating QR codes stays free.
     */
    @Transactional
    public TopupInstruction submitTopup(UUID userId, String paymentId) {
        String status = jdbc.sql("SELECT status FROM payments WHERE id = ? AND user_id = ?")
                .params(paymentId, userId.toString())
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "topup.not_found",
                        "Top-up not found", "Không tìm thấy yêu cầu nạp này."));

        if ("PENDING".equals(status)) {
            // Pressing twice is not an error; the request is already queued.
            return getTopup(userId, paymentId);
        }
        if (!"DRAFT".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "topup.not_draft",
                    "Not a draft",
                    "Yêu cầu này đã được xử lý, không thể gửi lại. Hãy tạo yêu cầu nạp mới.");
        }

        long recent = jdbc.sql("""
                        SELECT COUNT(*) FROM payments
                        WHERE user_id = ? AND submitted_at > NOW() - INTERVAL ? MINUTE
                        """)
                .params(userId.toString(), SUBMISSION_WINDOW_MINUTES)
                .query(Long.class)
                .single();
        checkSubmissionRate(recent);

        jdbc.sql("""
                        UPDATE payments
                        SET status = 'PENDING', submitted_at = NOW()
                        WHERE id = ? AND user_id = ? AND status = 'DRAFT'
                        """)
                .params(paymentId, userId.toString())
                .update();

        return getTopup(userId, paymentId);
    }

    /**
     * Người nạp tự huỷ yêu cầu của chính mình.
     *
     * <p>Trước đây một yêu cầu lỡ - bấm nhầm gói, đổi ý, hay trót báo đã chuyển
     * khoản khi chưa chuyển - chỉ có hai lối thoát: chờ hai tiếng cho
     * {@link #expireStaleTopups()} dọn, hoặc phiền quản trị viên. Cả hai đều để
     * một dòng "đang chờ duyệt" nằm lại trước mắt người dùng lẫn trong hàng đợi.
     *
     * <p>Chỉ DRAFT và PENDING huỷ được. Đơn nạp chưa cộng xu vào ví ở hai
     * trạng thái này, nên huỷ không đụng gì tới số dư. Đơn đã PAID thì tiền đã
     * vào - đó là chuyện hoàn tiền, không phải huỷ - nên bị từ chối ở đây.
     */
    @Transactional
    public TopupInstruction cancelTopup(UUID userId, String paymentId) {
        String status = jdbc.sql("SELECT status FROM payments WHERE id = ? AND user_id = ?")
                .params(paymentId, userId.toString())
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "topup.not_found",
                        "Top-up not found", "Không tìm thấy yêu cầu nạp này."));

        if ("CANCELLED".equals(status)) {
            // Bấm hai lần không phải lỗi; đơn đã ở đúng trạng thái mong muốn.
            return getTopup(userId, paymentId);
        }
        if (!"DRAFT".equals(status) && !"PENDING".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "topup.not_cancellable",
                    "Top-up not cancellable",
                    "Yêu cầu nạp này đã được xử lý nên không huỷ được nữa. "
                            + "Nếu bạn đã chuyển tiền nhầm, hãy liên hệ hỗ trợ.");
        }

        // Điều kiện trạng thái nằm ngay trong câu UPDATE: giữa lúc đọc và lúc ghi,
        // quản trị viên có thể vừa duyệt xong đơn này. Huỷ đè lên một đơn đã
        // PAID sẽ để xu đã cộng nằm trên một đơn mang nhãn "đã huỷ".
        int updated = jdbc.sql("""
                        UPDATE payments
                        SET status = 'CANCELLED',
                            admin_note = 'Người dùng tự huỷ yêu cầu nạp'
                        WHERE id = ? AND user_id = ? AND status IN ('DRAFT', 'PENDING')
                        """)
                .params(paymentId, userId.toString())
                .update();

        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "topup.not_cancellable",
                    "Top-up not cancellable",
                    "Yêu cầu nạp này vừa được xử lý nên không huỷ được nữa. "
                            + "Hãy tải lại trang để xem trạng thái mới nhất.");
        }

        return getTopup(userId, paymentId);
    }

    /**
     * Drops drafts and unconfirmed requests that nobody acted on, so the admin
     * queue and the reader's history stay meaningful.
     */
    @Transactional
    public int expireStaleTopups() {
        return jdbc.sql("""
                        UPDATE payments
                        SET status = 'CANCELLED',
                            admin_note = 'Tự động hủy: quá hạn chờ xác nhận chuyển khoản'
                        WHERE status IN ('DRAFT', 'PENDING')
                          AND created_at < NOW() - INTERVAL ? MINUTE
                        """)
                .param(STALE_TOPUP_MINUTES)
                .update();
    }

    /** How long an unconfirmed request survives before it is cancelled. */
    private static final int STALE_TOPUP_MINUTES = 120;

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
                        -- Drafts are half-finished screens, not history.
                        WHERE p.user_id = ? AND p.status <> 'DRAFT'
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
                        instantText(rs.getTimestamp("created_at")),
                        instantText(rs.getTimestamp("paid_at"))))
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

    /**
     * ISO-8601, because the browser is what reads these.
     *
     * <p>{@code Timestamp.toString()} produces "2026-08-13 10:37:12.0", which is
     * not a format {@code new Date(...)} is required to understand: V8 happens
     * to accept it while Firefox and Safari return an invalid date, so the
     * wallet history showed nothing on those browsers.
     */
    private static String instantText(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    private record Payment(
            String id, String packageId, String methodId, String transactionCode,
            long coin, long gem, String status) {
    }
}
