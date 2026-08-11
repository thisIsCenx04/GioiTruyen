package com.storyplatform.catalog.api;

import com.storyplatform.catalog.application.PublicCatalogService;
import com.storyplatform.catalog.application.dto.CatalogDtos;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicCatalogController {

    private final PublicCatalogService catalogService;

    public PublicCatalogController(PublicCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/home")
    public CatalogDtos.HomeResponse home(@RequestParam(defaultValue = "vi-VN") String locale) {
        return catalogService.home(locale);
    }

    @GetMapping("/categories")
    public CatalogDtos.CategoryTaxonomy categories() {
        return catalogService.categories();
    }

    @GetMapping("/promotions/home")
    public List<CatalogDtos.PromotedHomeStory> promotedHome() {
        return catalogService.promotedHome();
    }

    @GetMapping("/stories/sections")
    public List<CatalogDtos.TaggedStorySection> storySections() {
        return catalogService.storySections();
    }

    @GetMapping("/rankings/boards")
    public List<CatalogDtos.RankingBoard> rankingBoards() {
        return catalogService.rankingBoards();
    }

    @GetMapping("/zhihu/sections")
    public List<CatalogDtos.TaggedStorySection> zhihuSections() {
        return catalogService.zhihuSections();
    }

    @GetMapping("/zhihu/rankings")
    public List<CatalogDtos.RankingBoard> zhihuRankingBoards() {
        return catalogService.zhihuRankingBoards();
    }

    @GetMapping("/categories/{slug}/stories")
    public List<CatalogDtos.HomeStorySummary> categoryStories(@PathVariable String slug) {
        return catalogService.categoryStories(slug);
    }

    @GetMapping("/tags/{slug}/stories")
    public List<CatalogDtos.HomeStorySummary> tagStories(@PathVariable String slug) {
        return catalogService.tagStories(slug);
    }

    @GetMapping("/stories/{identifier}")
    public CatalogDtos.PublicStory story(@PathVariable String identifier) {
        return catalogService.story(identifier);
    }

    @GetMapping("/stories/{identifier}/chapters")
    public CatalogDtos.ChapterPage chapters(
            @PathVariable String identifier,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return catalogService.chapters(identifier, limit);
    }

    @GetMapping("/chapters/{chapterId}")
    public CatalogDtos.PublishedChapterDetail chapter(@PathVariable String chapterId) {
        return catalogService.chapter(chapterId);
    }

    @GetMapping("/search")
    public CatalogDtos.SearchResponse search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return catalogService.search(q, limit);
    }

    @GetMapping("/search/suggestions")
    public CatalogDtos.SuggestionResponse suggestions(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "8") int limit
    ) {
        return catalogService.suggestions(q, limit);
    }
}
