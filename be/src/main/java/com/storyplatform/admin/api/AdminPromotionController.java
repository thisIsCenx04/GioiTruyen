package com.storyplatform.admin.api;

import com.storyplatform.promotion.application.PromotionService;
import com.storyplatform.promotion.application.dto.PromotionDtos.PromotionBooking;
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
 * Deciding which stories appear in the home page's Bố cáo strip.
 *
 * <p>A team pays coins to request a slot; nothing is shown until an admin
 * approves it. Rejecting refunds the coins and sends the reason to the buyer,
 * so no one is left paying for a slot they never got.
 */
@RestController
@RequestMapping("/admin/promotions")
public class AdminPromotionController {

    private final PromotionService promotionService;

    public AdminPromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    public record ReviewRequest(String note) {
    }

    @GetMapping
    public List<PromotionBooking> list(@RequestParam(required = false) String status) {
        return promotionService.reviewQueue(status);
    }

    @PostMapping("/{promotionId}/approve")
    public PromotionBooking approve(
            @PathVariable UUID promotionId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) ReviewRequest request
    ) {
        return promotionService.approve(promotionId, reviewer(jwt), note(request));
    }

    @PostMapping("/{promotionId}/reject")
    public PromotionBooking reject(
            @PathVariable UUID promotionId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) ReviewRequest request
    ) {
        return promotionService.reject(promotionId, reviewer(jwt), note(request));
    }

    private static String note(ReviewRequest request) {
        return request == null ? null : request.note();
    }

    private static String reviewer(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
