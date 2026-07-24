package com.storyplatform.catalog.api;

import com.storyplatform.catalog.application.CatalogRequestException;
import com.storyplatform.catalog.application.ChapterCatalogOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @GetMapping("/chapters/{chapterId}")
    public ResponseEntity<ChapterCatalogOperations.ChapterDetail> detail(
            @PathVariable String chapterId,
            @RequestHeader(
                    value = HttpHeaders.IF_NONE_MATCH,
                    required = false
            ) String ifNoneMatch
    ) {
        try {
            var chapter = chapters.detail(chapterId);
            String etag = "\"" + chapter.etag() + "\"";
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .cacheControl(PUBLIC_CACHE)
                        .eTag(chapter.etag())
                        .build();
            }
            return ResponseEntity.ok()
                    .cacheControl(PUBLIC_CACHE)
                    .eTag(chapter.etag())
                    .body(chapter);
        } catch (CatalogRequestException exception) {
            HttpStatus status = exception.code().endsWith("_NOT_FOUND")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            throw new ApiException(
                    status,
                    exception.code(),
                    "Chapter detail request rejected",
                    exception.getMessage()
            );
        }
    }
}
