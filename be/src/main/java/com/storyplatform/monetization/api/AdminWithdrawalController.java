package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.MonetizationFlowService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bàn duyệt rút tiền của quản trị viên.
 *
 * <p>Trước khi có màn hình này, bảng `withdrawal_requests` có cột `state` mà
 * không một dòng mã nào ghi vào: mọi yêu cầu nằm mãi ở "Chờ duyệt", và xu đã bị
 * trừ lúc gửi thì không có đường nào quay lại ví. Người rút chỉ còn cách nhắn
 * riêng cho quản trị viên rồi chờ.
 *
 * <p>Mọi lối thoát khỏi hàng đợi đều để lại dấu vết đọc được: duyệt và chuyển
 * tiền ghi kèm mã giao dịch ngân hàng, huỷ bắt buộc kèm lý do và hoàn xu ngay
 * trong cùng một giao dịch cơ sở dữ liệu.
 *
 * <p>Toàn bộ đường dẫn /admin/** đã yêu cầu quyền SCOPE_ADMIN ở tầng bảo mật,
 * nên ở đây không kiểm tra quyền lần nữa.
 */
@RestController
@RequestMapping("/admin/withdrawals")
public class AdminWithdrawalController {

    private final MonetizationFlowService service;

    public AdminWithdrawalController(MonetizationFlowService service) {
        this.service = service;
    }

    /** Ghi chú đi kèm mọi quyết định; với huỷ thì bắt buộc, service tự kiểm. */
    public record ReviewRequest(String note, String transferReference) {}

    @GetMapping
    public List<MonetizationFlowService.AdminWithdrawalRow> list(
            @RequestParam(defaultValue = "PENDING_REVIEW") String state
    ) {
        return service.adminWithdrawals(state);
    }

    @PostMapping("/{id}/approve")
    public MonetizationFlowService.WithdrawalReceipt approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewRequest request
    ) {
        return service.approveWithdrawal(adminId(jwt), id, note(request));
    }

    @PostMapping("/{id}/paid")
    public MonetizationFlowService.WithdrawalReceipt markPaid(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewRequest request
    ) {
        return service.markWithdrawalPaid(adminId(jwt), id,
                request == null ? null : request.transferReference(), note(request));
    }

    @PostMapping("/{id}/reject")
    public MonetizationFlowService.WithdrawalReceipt reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) ReviewRequest request
    ) {
        return service.rejectWithdrawal(adminId(jwt), id, note(request));
    }

    private static String note(ReviewRequest request) {
        return request == null ? null : request.note();
    }

    private static UUID adminId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
