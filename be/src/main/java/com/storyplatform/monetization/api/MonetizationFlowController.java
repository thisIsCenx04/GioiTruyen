package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.MonetizationFlowService;
import com.storyplatform.monetization.application.dto.ChapterUnlockResponse;
import com.storyplatform.monetization.application.dto.DonationRequest;
import com.storyplatform.monetization.application.dto.DonationResponse;
import com.storyplatform.monetization.application.dto.WalletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
}
