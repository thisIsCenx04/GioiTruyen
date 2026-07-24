package com.storyplatform.community.api;

import com.storyplatform.community.application.ReactionOperations;
import com.storyplatform.community.application
        .ReactionTargetNotFoundException;
import com.storyplatform.community.domain.Reaction;
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

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@RestController
public final class ReactionController {

    private final ReactionOperations reactions;

    public ReactionController(ReactionOperations reactions) {
        this.reactions = Objects.requireNonNull(reactions);
    }

    @GetMapping("/reactions/{targetType}/{targetId}")
    public ResponseEntity<ReactionOperations.ReactionView> status(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String targetType,
            @PathVariable String targetId
    ) {
        return response(() -> reactions.status(
                jwt.getSubject(),
                type(targetType),
                uuid(targetId)
        ));
    }

    @PutMapping("/reactions/{targetType}/{targetId}")
    public ResponseEntity<ReactionOperations.ReactionView> add(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String targetType,
            @PathVariable String targetId
    ) {
        return response(() -> reactions.add(
                jwt.getSubject(),
                type(targetType),
                uuid(targetId)
        ));
    }

    @DeleteMapping("/reactions/{targetType}/{targetId}")
    public ResponseEntity<ReactionOperations.ReactionView> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String targetType,
            @PathVariable String targetId
    ) {
        return response(() -> reactions.remove(
                jwt.getSubject(),
                type(targetType),
                uuid(targetId)
        ));
    }

    private static ResponseEntity<
            ReactionOperations.ReactionView> response(
            java.util.function.Supplier<
                    ReactionOperations.ReactionView> operation
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(operation.get());
        } catch (ReactionTargetNotFoundException exception) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "REACTION_TARGET_NOT_FOUND",
                    "Reaction request rejected",
                    exception.getMessage()
            );
        }
    }

    private static Reaction.TargetType type(String value) {
        try {
            return Reaction.TargetType.valueOf(
                    value.toUpperCase(Locale.ROOT)
            );
        } catch (RuntimeException exception) {
            throw invalid("Reaction target type is invalid.");
        }
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid("Reaction target identifier is invalid.");
        }
    }

    private static ApiException invalid(String detail) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "REACTION_INVALID",
                "Reaction request rejected",
                detail
        );
    }
}
