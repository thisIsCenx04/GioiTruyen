package com.storyplatform.promotion.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Payloads for the "bố cáo" (story promotion) flow. */
public final class PromotionDtos {

    private PromotionDtos() {}

    public record PromotionPackage(
            String id,
            String code,
            String name,
            int durationDays,
            long priceCoin,
            long pricePerDayCoin,
            String description
    ) {}

    /** A story the caller is allowed to promote, plus its current booking state. */
    public record PromotableStory(
            String storyId,
            String slug,
            String title,
            String coverUrl,
            String teamId,
            String teamName,
            boolean promoting,
            String activeUntil,
            int activeDaysRemaining
    ) {}

    public record PromotionBooking(
            String id,
            String storyId,
            String storyTitle,
            String storySlug,
            String teamId,
            String teamName,
            int durationDays,
            long coinPaid,
            String startsAt,
            String endsAt,
            String status,
            int daysRemaining,
            /** Why an admin approved or rejected it; null while pending. */
            String reviewNote,
            String reviewedAt,
            String createdAt,
            /** Who paid, so the review queue can name them. */
            String purchasedByEmail
    ) {}

    public record PromotionOverview(
            List<PromotionPackage> packages,
            List<PromotableStory> stories,
            List<PromotionBooking> bookings,
            long walletCoinBalance,
            int maxTotalDays
    ) {}

    public record CreatePromotionRequest(
            @NotNull UUID storyId,
            @NotNull UUID packageId
    ) {}

    public record ExtendPromotionRequest(
            @NotNull UUID packageId
    ) {}
}
