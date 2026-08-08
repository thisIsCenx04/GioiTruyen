package com.storyplatform.community.api;

import com.storyplatform.community.application.StoryRelationOperations;
import com.storyplatform.community.domain.StoryRelation;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
public final class StoryRelationController {

    private final StoryRelationOperations operations;

    public StoryRelationController(StoryRelationOperations operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    @GetMapping("/stories/{storyId}/relations/{type}")
    public StoryRelationOperations.RelationView status(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String storyId,
            @PathVariable String type
    ) {
        return operations.status(
                jwt.getSubject(),
                validateUuid(storyId),
                parseType(type)
        );
    }

    @PutMapping("/stories/{storyId}/relations/{type}")
    public StoryRelationOperations.RelationView add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String storyId,
            @PathVariable String type
    ) {
        return operations.add(
                jwt.getSubject(),
                validateUuid(storyId),
                parseType(type)
        );
    }

    @DeleteMapping("/stories/{storyId}/relations/{type}")
    public StoryRelationOperations.RelationView remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String storyId,
            @PathVariable String type
    ) {
        return operations.remove(
                jwt.getSubject(),
                validateUuid(storyId),
                parseType(type)
        );
    }

    private static String validateUuid(String id) {
        if (id == null || !id.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        )) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "STORY_RELATION_INVALID_STORY_ID",
                    "Invalid story identifier",
                    "Not a valid UUID: " + id
            );
        }
        return id;
    }

    private static StoryRelation.Type parseType(String value) {
        try {
            return StoryRelation.Type.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "STORY_RELATION_INVALID_TYPE",
                    "Invalid relation type",
                    "Unknown relation type: " + value
            );
        }
    }
}
