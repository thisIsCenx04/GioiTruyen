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

    /* ----------------------------------------------------------- board ----- */

    /** The twelve slots plus the queue, in one call - the screen needs both. */
    @GetMapping("/board")
    public PromotionService.PromotionBoard board() {
        return promotionService.board();
    }

    /** {@code slots} maps a position (1..12) to the promotion id sitting in it. */
    public record ReorderRequest(java.util.Map<Integer, String> slots) {
    }

    @PostMapping("/board/reorder")
    public PromotionService.PromotionBoard reorder(@RequestBody ReorderRequest request) {
        return promotionService.reorder(
                request.slots() == null ? java.util.Map.of() : request.slots());
    }

    @PostMapping("/board/{promotionId}/clear")
    public PromotionService.PromotionBoard clearSlot(@PathVariable UUID promotionId) {
        return promotionService.clearSlot(promotionId);
    }

    public record AdminCreateRequest(UUID storyId, UUID packageId) {
    }

    /** Places a story on the board directly, without charging its team. */
    @PostMapping("/board")
    public PromotionBooking createDirect(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AdminCreateRequest request
    ) {
        return promotionService.adminCreate(request.storyId(), request.packageId(), reviewer(jwt));
    }

    public record ScheduleRequest(String scheduledAt) {
    }

    /**
     * Accepts a booking but promises it a date instead of a slot - what the
     * board-full case offers instead of a rejection.
     */
    @PostMapping("/{promotionId}/schedule")
    public PromotionBooking schedule(
            @PathVariable UUID promotionId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ScheduleRequest request
    ) {
        return promotionService.scheduleLater(promotionId, reviewer(jwt), request.scheduledAt());
    }

    /* ------------------------------------------------------- package pricing */

    public record PackagePriceRequest(Long priceCoin, Boolean active) {
    }

    /** The price list as an admin edits it, inactive packages included. */
    @GetMapping("/packages")
    public List<PromotionService.AdminPackageRow> packages() {
        return promotionService.adminPackages();
    }

    @PostMapping("/packages/{packageId}")
    public PromotionService.AdminPackageRow updatePackage(
            @PathVariable UUID packageId,
            @RequestBody PackagePriceRequest request
    ) {
        return promotionService.updatePackage(packageId, request.priceCoin(), request.active());
    }

    /* ------------------------------------------------------- team discounts */

    public record TeamDiscountRequest(Long discountCoin) {
    }

    /**
     * Standing bố cáo discounts, per team.
     *
     * <p>Admin-only by placement: this controller sits under {@code /admin/**},
     * which the security chain gates on SCOPE_ADMIN. There is deliberately no
     * publisher-facing equivalent - a team is shown the price it pays and
     * nothing about how that price was reached.
     */
    @GetMapping("/team-discounts")
    public List<PromotionService.TeamDiscountRow> teamDiscounts() {
        return promotionService.teamDiscounts();
    }

    @PostMapping("/team-discounts/{teamId}")
    public PromotionService.TeamDiscountRow updateTeamDiscount(
            @PathVariable UUID teamId,
            @RequestBody TeamDiscountRequest request
    ) {
        return promotionService.updateTeamDiscount(teamId, request.discountCoin());
    }

    private static String note(ReviewRequest request) {
        return request == null ? null : request.note();
    }

    private static String reviewer(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }
}
