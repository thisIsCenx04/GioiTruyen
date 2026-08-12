package com.storyplatform.admin.application.dto;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Response and request payloads for the admin dashboard.
 * Field names mirror the contract expected by the web admin workspace.
 */
public final class AdminDtos {

    private AdminDtos() {}

    /**
     * Reads a nullable timestamp column as an ISO string.
     *
     * <p>Every admin list shows when a record was created and last changed;
     * without those, two similar rows cannot be told apart.
     */
    public static String timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

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
            /** First genre, kept so existing table columns keep rendering. */
            String categoryId,
            String categoryName,
            /** Every genre the story belongs to. */
            List<String> categoryIds,
            List<String> categoryNames,
            String coverUrl,
            String synopsis,
            List<String> tags,
            String storyFormat,
            String storyType,
            String workflowStatus,
            String completionStatus,
            String updatedAt,
            String createdAt
    ) {}

    public record AdminChapterRow(
            String id,
            java.math.BigDecimal chapterNumber,
            String title,
            String slug,
            String content,
            String accessType,
            long coinPrice,
            String status
    ) {}

    public record AdminCategoryRow(
            String id,
            String slug,
            String name,
            String description,
            int sortOrder,
            boolean active,
            int version,
            String createdAt,
            String updatedAt
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
            String updatedAt,
            String createdAt
    ) {}

    public record AdminUserRow(
            String id,
            String email,
            String displayName,
            String bio,
            String state,
            String roles,
            long availableXu,
            String createdAt,
            String updatedAt
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
            /** Legacy single-genre field; superseded by {@link #categoryIds()}. */
            String categoryId,
            /** A story can sit in several genres at once. */
            List<String> categoryIds,
            String synopsis,
            String summary,
            String contentType,
            /** SERIAL or ONESHOT; defaults to SERIAL when absent. */
            String storyFormat,
            /** TEXT, AUDIO, EXCLUSIVE or ORIGINAL; defaults to TEXT when absent. */
            String storyType,
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
