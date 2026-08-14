package com.storyplatform.engagement.api;

import com.storyplatform.catalog.application.PublicCatalogService;
import com.storyplatform.catalog.application.dto.CatalogDtos;
import com.storyplatform.engagement.application.StoryRecommendationService;
import com.storyplatform.engagement.application.StoryRelationService;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Keeping and following a story, and the library those build up.
 *
 * <p>The story page has always shown a heart and a bookmark, and the security
 * rules have always listed these paths - but no controller answered them, so
 * every click failed. This is that missing half.
 */
@RestController
public class StoryEngagementController {

    private final StoryRelationService relations;
    private final PublicCatalogService catalogService;
    private final StoryRecommendationService recommendations;

    public StoryEngagementController(
            StoryRelationService relations,
            PublicCatalogService catalogService,
            StoryRecommendationService recommendations
    ) {
        this.relations = relations;
        this.catalogService = catalogService;
        this.recommendations = recommendations;
    }

    @GetMapping("/stories/{storyId}/{relation:favorite|follow}")
    public StoryRelationService.RelationState status(
            @PathVariable String storyId,
            @PathVariable String relation,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relations.status(storyId, relation, currentUser(jwt));
    }

    @PostMapping("/stories/{storyId}/{relation:favorite|follow}")
    public StoryRelationService.RelationState add(
            @PathVariable String storyId,
            @PathVariable String relation,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relations.add(storyId, relation, currentUser(jwt));
    }

    @DeleteMapping("/stories/{storyId}/{relation:favorite|follow}")
    public StoryRelationService.RelationState remove(
            @PathVariable String storyId,
            @PathVariable String relation,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return relations.remove(storyId, relation, currentUser(jwt));
    }

    @GetMapping("/me/library")
    public List<CatalogDtos.HomeStorySummary> library(@AuthenticationPrincipal Jwt jwt) {
        return catalogService.library(currentUser(jwt));
    }

    /**
     * Spends gems to recommend a story.
     *
     * <p>The reply carries the reader's own gift and their remaining balance,
     * never the story's running total - that figure belongs to the admin
     * dashboard alone.
     */
    @PostMapping("/stories/{storyId}/recommend")
    public StoryRecommendationService.RecommendationReceipt recommend(
            @PathVariable String storyId,
            @RequestBody RecommendRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return recommendations.recommend(storyId, currentUser(jwt), request.gemAmount());
    }

    /** What this reader has already given the story; their own figure to see. */
    @GetMapping("/stories/{storyId}/recommend")
    public MyRecommendation myRecommendation(
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return new MyRecommendation(recommendations.myContribution(storyId, currentUser(jwt)));
    }

    public record RecommendRequest(@Positive long gemAmount) {
    }

    public record MyRecommendation(long myGemAmount) {
    }

    private static String currentUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Authentication required", "Vui lòng đăng nhập để dùng tính năng này.");
        }
        return jwt.getSubject();
    }
}
