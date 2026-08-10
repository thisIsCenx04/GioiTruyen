package com.storyplatform.monetization.application.dto;

import java.time.Instant;

public record WalletResponse(long coinBalance, long gemBalance, Instant updatedAt) {
}
