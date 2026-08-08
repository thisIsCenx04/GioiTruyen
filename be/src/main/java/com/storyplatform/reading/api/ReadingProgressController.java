package com.storyplatform.reading.api;

import com.storyplatform.reading.application.ReadingProgressException;
import com.storyplatform.reading.application.ReadingProgressOperations;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public final class ReadingProgressController {

    private final ReadingProgressOperations progress;

    public ReadingProgressController(ReadingProgressOperations progress) {
        this.progress = Objects.requireNonNull(progress);
    }

    @GetMapping("/me/reading-progress/{storyId}")
    public ResponseEntity<ReadingProgressOperations.ProgressView> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String storyId
    ) {
        try {
            return response(progress.get(jwt.getSubject(), storyId));
        } catch (ReadingProgressException exception) {
            throw problem(exception);
        }
    }

    @PutMapping("/me/reading-progress/{storyId}")
    public ResponseEntity<ReadingProgressOperations.ProgressView>
            synchronize(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String storyId,
            @RequestHeader(
                    value = HttpHeaders.IF_MATCH,
                    required = false
            ) String ifMatch,
            @Valid @RequestBody SynchronizeReadingProgressRequest request
    ) {
        try {
            return response(progress.synchronize(
                    jwt.getSubject(),
                    storyId,
                    version(ifMatch),
                    new ReadingProgressOperations.SyncCommand(
                            request.chapterId(),
                            request.position(),
                            request.deviceUpdatedAt()
                    )
            ));
        } catch (ReadingProgressException exception) {
            throw problem(exception);
        }
    }

    private static ResponseEntity<
            ReadingProgressOperations.ProgressView> response(
            ReadingProgressOperations.ProgressView view
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .eTag(Long.toString(view.version()))
                .body(view);
    }

    private static Long version(String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches("\"[1-9][0-9]*\"")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Reading progress request rejected",
                    "If-Match must contain one quoted positive version."
            );
        }
        try {
            return Long.parseLong(
                    value.substring(1, value.length() - 1)
            );
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Reading progress request rejected",
                    "If-Match version is outside the supported range."
            );
        }
    }

    private static ApiException problem(
            ReadingProgressException exception
    ) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return new ApiException(
                status,
                exception.code(),
                "Reading progress request rejected",
                exception.getMessage()
        );
    }
}
