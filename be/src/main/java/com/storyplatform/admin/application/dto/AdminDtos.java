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

    /**
     * One placement's advertising performance.
     *
     * <p>Counts come from ad_events, which records the platform's own banners
     * and affiliate redirects. AdSense earnings are not in this database at all -
     * they live in the AdSense account and would need the AdSense Management
     * API to read - so nothing here should be labelled as AdSense revenue.
     */
    public record AdPlacementRow(
            String placement,
            long impressions,
            long clicks,
            /** Clicks per hundred impressions, rounded to two decimals. */
            double ctr,
            /** Banners currently switched on for the placement. */
            long activeUnits
    ) {}

    public record AdOverview(
            long impressions,
            long clicks,
            double ctr,
            long activeUnits,
            List<ChartPoint> impressionSeries,
            List<ChartPoint> clickSeries,
            List<AdPlacementRow> placements
    ) {}

    public record AdminOverview(
            AdminStats stats,
            List<ChartPoint> revenueSeries,
            List<ChartPoint> trafficSeries,
            List<ChartPoint> readerSeries,
            List<String> tasks,
            AdOverview ads
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
            /**
             * Bản teaser 500 ký tự (stories.short_description), dùng cho cột
             * xem nhanh trong bảng.
             */
            String synopsis,
            /**
             * Văn án đầy đủ (stories.description).
             *
             * <p>Form sửa truyện phải nạp cột này chứ không phải {@link
             * #synopsis()}: nạp teaser rồi lưu lại chính là thứ đã cắt cụt văn
             * án của mọi truyện xuống còn 500 ký tự ngay lần sửa đầu tiên, và
             * phần mất đi là mất hẳn.
             */
            String description,
            List<String> tags,
            String storyFormat,
            String storyType,
            String workflowStatus,
            String completionStatus,
            int chapterCount,
            /** Configured combo price, or null when the story has no bundle deal. */
            Long comboPriceXu,
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

    /**
     * `sortOrder` is deliberately absent: the genres table has no such column,
     * so the API only ever reported a hardcoded 0 and the admin form's
     * "Thứ tự hiển thị" input was discarded on save. Genres are ordered by name.
     */
    public record AdminCategoryRow(
            String id,
            String slug,
            String name,
            String description,
            boolean active,
            int version,
            String createdAt,
            String updatedAt,
            /** Stories carrying this genre, in any workflow state. */
            long storyCount,
            /** The published subset, which is what a reader can actually find. */
            long publishedStoryCount
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
            /**
             * True khi nhóm đã được ban quản trị xác nhận - dấu tích xanh.
             * Tách khỏi {@link #state()} vì tạm khoá rồi mở lại không được làm
             * mất dấu xác minh.
             */
            boolean verified,
            /** Mốc xác nhận, hoặc null khi chưa xác nhận. */
            String verifiedAt,
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
            List<String> tags,
            /**
             * Price for unlocking every chapter at once. Null or 0 means no bundle
             * deal, and the combo then costs the sum of the chapter prices with no
             * discount advertised.
             */
            Long comboPriceXu
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
