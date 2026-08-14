package com.storyplatform.catalog.api;

import com.storyplatform.catalog.application.PublicCatalogService;
import com.storyplatform.catalog.application.dto.CatalogDtos;
import com.storyplatform.engagement.application.StoryViewRecorder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicCatalogController {

    private final PublicCatalogService catalogService;
    private final StoryViewRecorder viewRecorder;

    public PublicCatalogController(
            PublicCatalogService catalogService,
            StoryViewRecorder viewRecorder
    ) {
        this.catalogService = catalogService;
        this.viewRecorder = viewRecorder;
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

    // Both endpoints stay public: reading is open to guests, and a paid chapter
    // is withheld by the service rather than by requiring a login to ask.
    @GetMapping("/stories/{identifier}/chapters")
    public CatalogDtos.ChapterPage chapters(
            @PathVariable String identifier,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return catalogService.chapters(identifier, page, size, readerId(jwt));
    }

    /**
     * Resolves a chapter from its number within a story.
     *
     * <p>The reader URL carries "chuong-12", and the page used to find that in
     * the chapter list - which only worked while the whole list came back in one
     * response. With the list paged, chapter 500 lives on a page the reader page
     * never asked for, so the lookup belongs on the server.
     */
    @GetMapping("/stories/{identifier}/chapters/by-number/{number}")
    public CatalogDtos.PublishedChapterDetail chapterByNumber(
            @PathVariable String identifier,
            @PathVariable String number,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        CatalogDtos.PublishedChapterDetail detail =
                catalogService.chapterByNumber(identifier, number, readerId(jwt));
        if (detail.unlocked()) {
            viewRecorder.recordChapterView(
                    detail.storyId(), detail.id(), readerId(jwt), clientIp(request));
        }
        return detail;
    }

    @GetMapping("/chapters/{chapterId}")
    public CatalogDtos.PublishedChapterDetail chapter(
            @PathVariable String chapterId,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        CatalogDtos.PublishedChapterDetail detail = catalogService.chapter(chapterId, readerId(jwt));
        // Only a chapter the reader can actually read counts. A locked one comes
        // back without its text, so counting it would inflate the figure with
        // paywall bounces rather than reading.
        if (detail.unlocked()) {
            viewRecorder.recordChapterView(
                    detail.storyId(), detail.id(), readerId(jwt), clientIp(request));
        }
        return detail;
    }

    private static String readerId(Jwt jwt) {
        return jwt == null ? null : jwt.getSubject();
    }

    /**
     * The caller's address as far as it can be trusted.
     *
     * <p>Behind nginx every request arrives from localhost, so the forwarded
     * header is read first; its leftmost entry is the original client. It is
     * only ever hashed for de-duplication, never stored or shown.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
