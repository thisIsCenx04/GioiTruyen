package com.storyplatform.monetization.application.dto;

import java.util.UUID;

public record ChapterUnlockResponse(
        UUID chapterId,
        UUID storyId,
        long coinPaid,
        long coinBalance,
        boolean alreadyUnlocked
) {
}
