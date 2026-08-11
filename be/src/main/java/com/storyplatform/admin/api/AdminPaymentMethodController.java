package com.storyplatform.admin.api;

import com.storyplatform.shared.api.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Payment methods offered to readers buying coins.
 *
 * <p>Each method carries a free-form JSON {@code config} because the fields
 * differ per type: a bank transfer needs an account number, PayPal needs a
 * merchant address, a QR needs an image URL.
 */
@RestController
@RequestMapping("/admin/payment-methods")
public class AdminPaymentMethodController {

    /** Mirrors the payment_methods.type enum. */
    private static final Set<String> TYPES =
            Set.of("BANK_TRANSFER", "QR", "PAYPAL", "OTHER");

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public AdminPaymentMethodController(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record PaymentMethodRow(
            String id,
            String name,
            String type,
            Map<String, Object> config,
            String instructions,
            boolean active,
            int sortOrder
    ) {
    }

    public record UpsertPaymentMethodRequest(
            String name,
            String type,
            Map<String, Object> config,
            String instructions,
            Boolean active,
            Integer sortOrder
    ) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PaymentMethodRow> list() {
        return jdbc.sql("""
                        SELECT id, name, type, config, instructions, is_active, sort_order
                        FROM payment_methods
                        ORDER BY sort_order, name
                        """)
                .query((rs, rowNum) -> new PaymentMethodRow(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("type"),
                        readConfig(rs.getString("config")),
                        rs.getString("instructions"),
                        rs.getBoolean("is_active"),
                        rs.getInt("sort_order")))
                .list();
    }

    @PostMapping
    @Transactional
    public PaymentMethodRow create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpsertPaymentMethodRequest request
    ) {
        Validated valid = validate(request);
        String id = UUID.randomUUID().toString();
        jdbc.sql("""
                        INSERT INTO payment_methods
                            (id, name, type, config, instructions, is_active, sort_order, created_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id, valid.name(), valid.type(), valid.config(), valid.instructions(),
                        valid.active(), valid.sortOrder(), currentUser(jwt))
                .update();
        return find(id);
    }

    @PutMapping("/{id}")
    @Transactional
    public PaymentMethodRow update(
            @PathVariable String id,
            @RequestBody UpsertPaymentMethodRequest request
    ) {
        find(id);
        Validated valid = validate(request);
        jdbc.sql("""
                        UPDATE payment_methods
                        SET name = ?, type = ?, config = ?, instructions = ?,
                            is_active = ?, sort_order = ?, updated_at = NOW()
                        WHERE id = ?
                        """)
                .params(valid.name(), valid.type(), valid.config(), valid.instructions(),
                        valid.active(), valid.sortOrder(), id)
                .update();
        return find(id);
    }

    /**
     * Removed outright when nothing references it, hidden otherwise: a payment
     * that already went through must keep pointing at the method that took it.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable String id) {
        find(id);
        long used = jdbc.sql("SELECT COUNT(*) FROM payments WHERE payment_method_id = ?")
                .param(id).query(Long.class).optional().orElse(0L);
        if (used > 0) {
            jdbc.sql("UPDATE payment_methods SET is_active = FALSE, updated_at = NOW() WHERE id = ?")
                    .param(id).update();
            return;
        }
        jdbc.sql("DELETE FROM payment_methods WHERE id = ?").param(id).update();
    }

    private PaymentMethodRow find(String id) {
        return list().stream()
                .filter(row -> row.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "payment_method.not_found",
                        "Payment method not found", "Không tìm thấy phương thức thanh toán này."));
    }

    private Map<String, Object> readConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
            return parsed;
        } catch (Exception exception) {
            // A malformed row must not break the whole listing.
            return Map.of();
        }
    }

    private Validated validate(UpsertPaymentMethodRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "payment_method.invalid_name",
                    "Missing name", "Chưa nhập tên phương thức thanh toán.");
        }
        String type = request.type() == null ? "" : request.type().trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "payment_method.invalid_type",
                    "Invalid type",
                    "Loại thanh toán phải là một trong: chuyển khoản, QR, PayPal hoặc khác.");
        }

        Map<String, Object> config = request.config() == null ? Map.of() : request.config();
        requireConfigFields(type, config);

        String json;
        try {
            json = objectMapper.writeValueAsString(config);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "payment_method.invalid_config",
                    "Invalid config", "Thông tin cấu hình không hợp lệ.");
        }

        return new Validated(
                request.name().trim(),
                type,
                json,
                request.instructions() == null ? null : request.instructions().trim(),
                request.active() == null || request.active(),
                request.sortOrder() == null ? 0 : request.sortOrder());
    }

    /** Each type has fields a reader cannot pay without. */
    private static void requireConfigFields(String type, Map<String, Object> config) {
        List<String> required = switch (type) {
            case "BANK_TRANSFER" -> List.of("bank", "accountNumber", "accountName");
            case "PAYPAL" -> List.of("paypalEmail");
            case "QR" -> List.of("qrImageUrl");
            default -> List.of();
        };
        for (String field : required) {
            Object value = config.get(field);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "payment_method.missing_config",
                        "Missing config field",
                        "Thiếu thông tin bắt buộc: \"%s\".".formatted(vietnameseField(field)));
            }
        }
    }

    private static String vietnameseField(String field) {
        return switch (field) {
            case "bank" -> "Tên ngân hàng";
            case "accountNumber" -> "Số tài khoản";
            case "accountName" -> "Tên chủ tài khoản";
            case "paypalEmail" -> "Email PayPal";
            case "qrImageUrl" -> "Ảnh mã QR";
            default -> field;
        };
    }

    private static String currentUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Login required", "Bạn cần đăng nhập lại.");
        }
        return jwt.getSubject();
    }

    private record Validated(
            String name, String type, String config, String instructions,
            boolean active, int sortOrder) {
    }
}
