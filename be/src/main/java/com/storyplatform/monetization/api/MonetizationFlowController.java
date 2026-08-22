package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.MonetizationFlowService;
import com.storyplatform.monetization.application.dto.ChapterUnlockResponse;
import com.storyplatform.monetization.application.dto.DonationRequest;
import com.storyplatform.monetization.application.dto.DonationResponse;
import com.storyplatform.monetization.application.dto.WalletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MonetizationFlowController {

    private final MonetizationFlowService monetizationFlowService;

    public MonetizationFlowController(MonetizationFlowService monetizationFlowService) {
        this.monetizationFlowService = monetizationFlowService;
    }

    @GetMapping("/wallets/me")
    public WalletResponse wallet(@AuthenticationPrincipal Jwt jwt) {
        return monetizationFlowService.wallet(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/wallets/me/transactions")
    public List<MonetizationFlowService.WalletTransactionRow> walletTransactions(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return monetizationFlowService.walletTransactions(UUID.fromString(jwt.getSubject()), isAdmin(jwt));
    }

    @GetMapping("/wallets/me/withdrawals")
    public MonetizationFlowService.WithdrawalPage withdrawals(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor
    ) {
        return monetizationFlowService.withdrawals(UUID.fromString(jwt.getSubject()), isAdmin(jwt), cursor);
    }

    @PostMapping("/wallets/me/withdrawals")
    public MonetizationFlowService.WithdrawalReceipt createWithdrawal(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody MonetizationFlowService.WithdrawalRequest request
    ) {
        return monetizationFlowService.createWithdrawal(UUID.fromString(jwt.getSubject()), isAdmin(jwt), request);
    }

    /**
     * Người rút xác nhận đã nhận được tiền.
     *
     * <p>Đây là mắt xích cuối và là mắt xích duy nhất quản trị viên không tự
     * làm được: chỉ người có tài khoản ngân hàng mới biết tiền đã về hay chưa.
     */
    @PostMapping("/wallets/me/withdrawals/{id}/confirm")
    public MonetizationFlowService.WithdrawalReceipt confirmWithdrawal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return monetizationFlowService.confirmWithdrawal(UUID.fromString(jwt.getSubject()), id);
    }

    /** Người rút báo chưa nhận được tiền; ghi chú là bắt buộc. */
    @PostMapping("/wallets/me/withdrawals/{id}/dispute")
    public MonetizationFlowService.WithdrawalReceipt disputeWithdrawal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @RequestBody(required = false) WithdrawalNote request
    ) {
        return monetizationFlowService.disputeWithdrawal(UUID.fromString(jwt.getSubject()), id,
                request == null ? null : request.note());
    }

    public record WithdrawalNote(String note) {}

    @PostMapping("/chapters/{chapterId}/unlock")
    public ChapterUnlockResponse unlockChapter(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chapterId
    ) {
        return monetizationFlowService.unlockChapter(UUID.fromString(jwt.getSubject()), chapterId);
    }

    @PostMapping("/teams/{teamId}/donations")
    public DonationResponse donate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID teamId,
            @Valid @RequestBody DonationRequest request
    ) {
        return monetizationFlowService.donate(UUID.fromString(jwt.getSubject()), teamId, request);
    }

    @PostMapping("/stories/{storyId}/combo-purchase")
    public com.storyplatform.monetization.application.dto.ComboPurchaseResponse purchaseStoryCombo(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storyId
    ) {
        return monetizationFlowService.purchaseStoryCombo(UUID.fromString(jwt.getSubject()), storyId);
    }

    @GetMapping("/stories/{storyId}/combo-status")
    public java.util.Map<String, Object> getComboStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID storyId
    ) {
        boolean purchased = jwt != null && monetizationFlowService.hasPurchasedStoryCombo(UUID.fromString(jwt.getSubject()), storyId);
        var pricing = monetizationFlowService.comboPricing(storyId);
        // Pricing is public: a guest sees the combo price on the story page before
        // signing in, so only `purchased` depends on the caller.
        return java.util.Map.of(
                "storyId", storyId.toString(),
                "purchased", purchased,
                "chapterTotalXu", pricing.chapterTotalXu(),
                "comboPriceXu", pricing.comboPriceXu(),
                "configured", pricing.configured(),
                "discountPercent", pricing.discountPercent()
        );
    }

    private static boolean isAdmin(Jwt jwt) {
        if (jwt == null) {
            return false;
        }
        String role = jwt.getClaimAsString("role");
        String scope = jwt.getClaimAsString("scope");
        return "ADMIN".equalsIgnoreCase(role)
                || (scope != null && java.util.Arrays.stream(scope.split("\\s+"))
                        .anyMatch("ADMIN"::equalsIgnoreCase));
    }
}
