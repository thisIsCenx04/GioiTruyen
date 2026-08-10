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
            long saveCount
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
            List<String> categoryIds,
            String origin,
            String language,
            String completionStatus,
            String publishedAt,
            String updatedAt,
            int version
    ) {
    }

    public record PublicChapter(
            String id,
            String storyId,
            BigDecimal number,
            String slug,
            String title,
            String publishedAt,
            int version
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
            String contentHtml,
            int wordCount,
            String etag,
            ChapterLink previous,
            ChapterLink next
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
