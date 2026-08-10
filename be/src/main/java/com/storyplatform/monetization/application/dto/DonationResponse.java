package com.storyplatform.monetization.application.dto;

import java.util.UUID;

public record DonationResponse(
        UUID donationId,
        UUID teamId,
        UUID storyId,
        long grossCoin,
        long platformFeeCoin,
        long teamNetCoin,
        long coinBalance
) {
}
