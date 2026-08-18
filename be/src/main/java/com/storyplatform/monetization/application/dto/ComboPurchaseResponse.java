package com.storyplatform.monetization.application.dto;

import java.util.UUID;

public record ComboPurchaseResponse(
        UUID storyId,
        long priceXu,
        long newBalance,
        boolean purchased
) {}
