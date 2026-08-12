package com.storyplatform.catalog.application.dto;

import java.math.BigDecimal;
import java.util.List;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record HomeStorySummary(
            String id,
            String teamId,
            String teamName,
            String slug,
            String title,
            String coverAssetId,
            String publishedAt,
            long viewCount,
            long saveCount,
            /** SERIAL or ONESHOT - cards link straight into a one-page read. */
            String storyFormat,
            /** TEXT, AUDIO, EXCLUSIVE or ORIGINAL - drives the shelf badges. */
            String storyType
    ) {
    }

    public record HomeSection(
            String id,
            String type,
            String title,
            List<HomeStorySummary> stories
    ) {
    }

    public record TaggedStorySection(
            String id,
            String tag,
            String title,
            List<HomeStorySummary> stories
    ) {
    }

    public record HomeResponse(
            String locale,
            String version,
            String generatedAt,
            List<HomeSection> sections
    ) {
    }

    public record CategoryItem(String id, String slug, String name) {
    }

    public record CategoryGroup(String group, String label, List<CategoryItem> categories) {
    }

    public record CategoryTaxonomy(String version, List<CategoryGroup> groups) {
    }

    public record PromotedHomeStory(String bookingId, int slotPosition, String tagLabel, HomeStorySummary story) {
    }

    public record RankingStory(int rank, long metricValue, HomeStorySummary story) {
    }

    public record RankingBoard(
            String id,
            String title,
            String subtitle,
            String unit,
            List<RankingStory> stories
    ) {
    }

    public record PublicStory(
            String id,
            String teamId,
            String slug,
            String title,
            String synopsis,
            /** Public path to the cover image; null while the story has none. */
            String coverAssetId,
            List<String> categoryIds,
            String origin,
            String language,
            String completionStatus,
            /** SERIAL or ONESHOT - decides whether the reader shows a chapter list. */
            String storyFormat,
            /** TEXT, AUDIO, EXCLUSIVE or ORIGINAL. */
            String storyType,
            /** Editorial labels; each links to /tags/{slug}/stories. */
            List<StoryTag> tags,
            String publishedAt,
            String updatedAt,
            int version
    ) {
    }

    public record StoryTag(String slug, String label) {
    }

    public record PublicChapter(
            String id,
            String storyId,
            BigDecimal number,
            String slug,
            String title,
            String publishedAt,
            int version,
            /** FREE or PAID. */
            String accessType,
            /** Coins needed to unlock; 0 for a free chapter. */
            long coinPrice,
            /** True when this reader may open it: free, or already bought. */
            boolean unlocked
    ) {
    }

    public record ChapterLink(String id, BigDecimal number, String slug, String title) {
    }

    public record PublishedChapterDetail(
            String id,
            String storyId,
            BigDecimal number,
            String slug,
            String title,
            String publishedAt,
            int version,
            String revisionId,
            int revisionNo,
            /** Empty while the chapter is locked - the text is never sent. */
            String contentHtml,
            int wordCount,
            String etag,
            ChapterLink previous,
            ChapterLink next,
            /** FREE or PAID. */
            String accessType,
            /** Coins needed to unlock; 0 for a free chapter. */
            long coinPrice,
            /** False when the reader must pay before the text is returned. */
            boolean unlocked
    ) {
    }

    public record ChapterPage(List<PublicChapter> items, String nextCursor, boolean hasMore) {
    }

    public record SearchHit(HomeStorySummary story, double score, List<String> highlights) {
    }

    public record SearchResponse(
            List<SearchHit> items,
            String nextCursor,
            boolean hasMore,
            Object facets,
            long tookMs
    ) {
    }

    public record SuggestionItem(String id, String slug, String title, String coverAssetId) {
    }

    public record SuggestionResponse(List<SuggestionItem> items, String nextCursor, boolean hasMore) {
    }
}
