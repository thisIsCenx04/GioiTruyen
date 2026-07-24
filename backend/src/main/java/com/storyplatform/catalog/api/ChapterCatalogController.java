package com.storyplatform.catalog.api;

import com.storyplatform.catalog.application.CatalogRequestException;
import com.storyplatform.catalog.application.ChapterCatalogOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Objects;

@RestController
public final class ChapterCatalogController {

    private static final CacheControl PUBLIC_CACHE =
            CacheControl.maxAge(Duration.ofMinutes(2))
                    .cachePublic()
                    .staleWhileRevalidate(Duration.ofMinutes(10));

    private final ChapterCatalogOperations chapters;

    public ChapterCatalogController(ChapterCatalogOperations chapters) {
        this.chapters = Objects.requireNonNull(chapters, "chapters");
    }

    @GetMapping("/stories/{idOrSlug}/chapters")
    public ResponseEntity<ChapterCatalogOperations.ChapterPage> list(
            @PathVariable String idOrSlug,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(PUBLIC_CACHE)
                    .body(chapters.list(idOrSlug, cursor, limit));
        } catch (CatalogRequestException exception) {
            HttpStatus status = "STORY_NOT_FOUND".equals(exception.code())
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            throw new ApiException(
                    status,
                    exception.code(),
                    "Chapter catalog request rejected",
                    exception.getMessage()
            );
        }
    }
}
