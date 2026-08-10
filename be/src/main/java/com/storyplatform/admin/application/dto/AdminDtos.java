package com.storyplatform.admin.application.dto;

import java.util.List;

/**
 * Response and request payloads for the admin dashboard.
 * Field names mirror the contract expected by the web admin workspace.
 */
public final class AdminDtos {

    private AdminDtos() {}

    public record ChartPoint(String label, long value) {}

    public record AdminStats(
            long revenueXu,
            long visits,
            long readers,
            long teams,
            long stories
    ) {}

    public record AdminOverview(
            AdminStats stats,
            List<ChartPoint> revenueSeries,
            List<ChartPoint> trafficSeries,
            List<ChartPoint> readerSeries,
            List<String> tasks
    ) {}

    public record AdminStoryRow(
            String id,
            String slug,
            String title,
            String authorName,
            String teamName,
            String teamId,
            String categoryId,
            String categoryName,
            String coverUrl,
            String synopsis,
            List<String> tags,
            String workflowStatus,
            String completionStatus,
            String updatedAt
    ) {}

    public record AdminCategoryRow(
            String id,
            String slug,
            String name,
            String description,
            int sortOrder,
            boolean active,
            int version
    ) {}

    public record AdminTeamRow(
            String id,
            String slug,
            String name,
            String ownerName,
            String ownerUserId,
            String description,
            String state,
            long memberCount,
            String updatedAt
    ) {}

    public record AdminUserRow(
            String id,
            String email,
            String displayName,
            String bio,
            String state,
            String roles,
            long availableXu,
            String createdAt
    ) {}

    public record AdminCashFlowRow(
            String id,
            String entryType,
            long amountXu,
            String referenceType,
            String referenceId,
            String description,
            String userId,
            String userEmail,
            String createdAt
    ) {}

    public record UpsertCategoryRequest(
            String name,
            String slug,
            String description,
            Integer sortOrder,
            Boolean active
    ) {}

    public record UpsertStoryRequest(
            String title,
            String slug,
            String authorName,
            String teamId,
            String categoryId,
            String synopsis,
            String summary,
            String contentType,
            String workflowStatus,
            String completionStatus,
            List<String> tags
    ) {}

    public record UpsertTeamRequest(
            String name,
            String slug,
            String description,
            String ownerUserId,
            String state
    ) {}

    public record UpsertUserRequest(
            String email,
            String displayName,
            String bio,
            String password,
            List<String> roles,
            String state
    ) {}

    public record CreateCashFlowRequest(
            String entryType,
            Long amountXu,
            String description,
            String referenceType,
            String referenceId,
            String userId
    ) {}

    public record ReverseCashFlowRequest(String reason) {}
}
