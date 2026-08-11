package com.storyplatform.quest.application.dto;

import java.util.List;

public final class QuestDtos {

    private QuestDtos() {
    }

    /** One quest as the reader sees it today, with their own progress folded in. */
    public record QuestProgress(
            String questId,
            String questType,
            String title,
            String description,
            int targetValue,
            int progressValue,
            int rewardCoin,
            int rewardGem,
            boolean completed,
            boolean claimed
    ) {
    }

    public record QuestBoard(
            String questDate,
            int completedCount,
            int totalCount,
            List<QuestProgress> quests
    ) {
    }

    /** Result of claiming a reward, so the client can update the wallet display. */
    public record ClaimResult(
            String questId,
            int rewardCoin,
            int rewardGem,
            long coinBalance,
            long gemBalance
    ) {
    }

    /** Progress ping sent while the reader is active. */
    public record ProgressRequest(
            String questType,
            Integer amount,
            String storyId
    ) {
    }

    public record AdminQuestRow(
            String id,
            String questType,
            String title,
            String description,
            int targetValue,
            int rewardCoin,
            int rewardGem,
            int sortOrder,
            boolean active
    ) {
    }

    public record UpsertQuestRequest(
            String questType,
            String title,
            String description,
            Integer targetValue,
            Integer rewardCoin,
            Integer rewardGem,
            Integer sortOrder,
            Boolean active
    ) {
    }

    /** A quest type the admin can choose, with the rule that measures it. */
    public record QuestTypeOption(
            String value,
            String label,
            String unit,
            String measuredBy
    ) {
    }
}
