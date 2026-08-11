package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.TopupService;
import com.storyplatform.shared.api.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reader-facing top-up flow: browse packages and methods, open a payment, then
 * poll it while waiting for an admin to confirm the transfer.
 */
@RestController
public class TopupController {

    private final TopupService topupService;

    public TopupController(TopupService topupService) {
        this.topupService = topupService;
    }

    /** Public: the price list is worth seeing before signing up. */
    @GetMapping("/deposit-packages")
    public List<TopupService.DepositPackage> packages() {
        return topupService.packages();
    }

    @GetMapping("/payment-methods")
    public List<TopupService.PaymentMethodView> methods() {
        return topupService.methods();
    }

    public record CreateTopupRequest(String packageId, String methodId) {
    }

    @PostMapping("/topups")
    public TopupService.TopupInstruction create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateTopupRequest request
    ) {
        return topupService.createTopup(currentUser(jwt), request.packageId(), request.methodId());
    }

    @GetMapping("/topups/{paymentId}")
    public TopupService.TopupInstruction get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String paymentId
    ) {
        return topupService.getTopup(currentUser(jwt), paymentId);
    }

    @GetMapping("/topups")
    public List<TopupService.TopupHistoryRow> history(@AuthenticationPrincipal Jwt jwt) {
        return topupService.history(currentUser(jwt));
    }

    private static UUID currentUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Login required", "Bạn cần đăng nhập để nạp xu.");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
