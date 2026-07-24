package com.storyplatform.community.api;

import com.storyplatform.community.application.StoryNotFoundException;
import com.storyplatform.community.application.StoryRelationOperations;
import com.storyplatform.community.domain.StoryRelation;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
public final class StoryRelationController {

    private final StoryRelationOperations relations;

    public StoryRelationController(StoryRelationOperations relations) {
        this.relations = Objects.requireNonNull(relations);
    }

    @GetMapping("/stories/{storyId}/{relation}")
    public ResponseEntity<StoryRelationOperations.RelationView> status(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String storyId,
            @PathVariable String relation
    ) {
        return response(() -> relations.status(
                jwt.getSubject(), uuid(storyId), type(relation)
        ));
    }

    @PutMapping("/stories/{storyId}/{relation}")
    public ResponseEntity<StoryRelationOperations.RelationView> add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String storyId,
            @PathVariable String relation
    ) {
        return response(() -> relations.add(
                jwt.getSubject(), uuid(storyId), type(relation)
        ));
    }

    @DeleteMapping("/stories/{storyId}/{relation}")
    public ResponseEntity<StoryRelationOperations.RelationView> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String storyId,
            @PathVariable String relation
    ) {
        return response(() -> relations.remove(
                jwt.getSubject(), uuid(storyId), type(relation)
        ));
    }

    private static ResponseEntity<
            StoryRelationOperations.RelationView> response(
            java.util.function.Supplier<
                    StoryRelationOperations.RelationView> operation
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(operation.get());
        } catch (StoryNotFoundException exception) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "STORY_NOT_FOUND",
                    "Story relation request rejected",
                    exception.getMessage()
            );
        }
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid("Story identifier is invalid.");
        }
    }

    private static StoryRelation.Type type(String value) {
        return switch (value) {
            case "favorite" -> StoryRelation.Type.FAVORITE;
            case "follow" -> StoryRelation.Type.FOLLOW;
            default -> throw invalid("Story relation is invalid.");
        };
    }

    private static ApiException invalid(String detail) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "STORY_RELATION_INVALID",
                "Story relation request rejected",
                detail
        );
    }
}
