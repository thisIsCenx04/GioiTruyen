package com.storyplatform.reading.api;

import com.storyplatform.reading.application.ReadingHistoryOperations;
import com.storyplatform.reading.application.ReadingProgressException;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public final class ReadingHistoryController {

    private final ReadingHistoryOperations history;

    public ReadingHistoryController(ReadingHistoryOperations history) {
        this.history = Objects.requireNonNull(history);
    }

    @GetMapping("/me/reading-history")
    public ResponseEntity<ReadingHistoryOperations.HistoryPage> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(history.list(
                            jwt.getSubject(),
                            cursor,
                            limit
                    ));
        } catch (ReadingProgressException exception) {
            throw problem(exception);
        }
    }

    @DeleteMapping("/me/reading-history/{storyId}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String storyId
    ) {
        try {
            history.delete(jwt.getSubject(), storyId);
            return ResponseEntity.noContent()
                    .cacheControl(CacheControl.noStore())
                    .build();
        } catch (ReadingProgressException exception) {
            throw problem(exception);
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
                "Reading history request rejected",
                exception.getMessage()
        );
    }
}
