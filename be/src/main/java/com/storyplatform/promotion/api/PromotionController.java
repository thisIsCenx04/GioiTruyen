package com.storyplatform.promotion.api;

import com.storyplatform.promotion.application.PromotionService;
import com.storyplatform.promotion.application.dto.PromotionDtos.CreatePromotionRequest;
import com.storyplatform.promotion.application.dto.PromotionDtos.ExtendPromotionRequest;
import com.storyplatform.promotion.application.dto.PromotionDtos.PromotionBooking;
import com.storyplatform.promotion.application.dto.PromotionDtos.PromotionOverview;
import com.storyplatform.promotion.application.dto.PromotionDtos.PromotionPackage;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    /** Public so the pricing table renders for signed-out visitors too. */
    @GetMapping("/packages")
    public List<PromotionPackage> packages() {
        return promotionService.packages();
    }

    /** Packages, promotable stories, existing bookings and wallet balance in one call. */
    @GetMapping("/me")
    public PromotionOverview overview(@AuthenticationPrincipal Jwt jwt) {
        return promotionService.overview(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping
    public PromotionBooking create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePromotionRequest request
    ) {
        return promotionService.create(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/{promotionId}/extend")
    public PromotionBooking extend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID promotionId,
            @Valid @RequestBody ExtendPromotionRequest request
    ) {
        return promotionService.extend(UUID.fromString(jwt.getSubject()), promotionId, request);
    }
}
