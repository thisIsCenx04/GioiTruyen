package com.storyplatform.community.api;

import com.storyplatform.community.application.ReactionOperations;
import com.storyplatform.community.domain.Reaction;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
public final class ReactionController {

    private final ReactionOperations operations;

    public ReactionController(ReactionOperations operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    @GetMapping("/reactions/{targetType}/{targetId}")
    public ReactionOperations.ReactionView status(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String targetType,
            @PathVariable String targetId
    ) {
        return operations.status(
                jwt.getSubject(),
                parseTargetType(targetType),
                validateUuid(targetId)
        );
    }

    @PutMapping("/reactions/{targetType}/{targetId}")
    public ReactionOperations.ReactionView add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String targetType,
            @PathVariable String targetId
    ) {
        return operations.add(
                jwt.getSubject(),
                parseTargetType(targetType),
                validateUuid(targetId)
        );
    }

    @DeleteMapping("/reactions/{targetType}/{targetId}")
    public ReactionOperations.ReactionView remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String targetType,
            @PathVariable String targetId
    ) {
        return operations.remove(
                jwt.getSubject(),
                parseTargetType(targetType),
                validateUuid(targetId)
        );
    }

    private static Reaction.TargetType parseTargetType(String value) {
        try {
            return Reaction.TargetType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REACTION_INVALID_TARGET_TYPE",
                    "Invalid target type",
                    "Unknown target type: " + value
            );
        }
    }

    private static String validateUuid(String id) {
        if (id == null || !id.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        )) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REACTION_INVALID_ID",
                    "Invalid identifier",
                    "Not a valid UUID: " + id
            );
        }
        return id;
    }
}
