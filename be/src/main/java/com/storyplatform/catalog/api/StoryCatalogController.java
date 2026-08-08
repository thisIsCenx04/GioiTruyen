package com.storyplatform.catalog.api;

import com.storyplatform.catalog.application.CatalogRequestException;
import com.storyplatform.catalog.application.PublicStoryProjection;
import com.storyplatform.catalog.application.StoryCatalogOperations;
import com.storyplatform.catalog.domain.Story;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@RestController
public final class StoryCatalogController {

    private static final CacheControl PUBLIC_CACHE =
            CacheControl.maxAge(Duration.ofMinutes(2))
                    .cachePublic()
                    .staleWhileRevalidate(Duration.ofMinutes(10));

    private final StoryCatalogOperations stories;

    public StoryCatalogController(StoryCatalogOperations stories) {
        this.stories = Objects.requireNonNull(stories, "stories");
    }

    @GetMapping("/stories")
    public ResponseEntity<StoryCatalogOperations.StoryPage> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String teamId,
            @RequestParam(defaultValue = "published_desc") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        try {
            var page = stories.list(new StoryCatalogOperations.StoryFilter(
                    categories(category),
                    completion(status),
                    origin(origin),
                    teamId,
                    sort,
                    cursor,
                    limit
            ));
            return ResponseEntity.ok()
                    .cacheControl(PUBLIC_CACHE)
                    .body(page);
        } catch (CatalogRequestException exception) {
            throw problem(exception);
        }
    }

    @GetMapping("/stories/{idOrSlug}")
    public ResponseEntity<PublicStoryProjection> get(
            @PathVariable String idOrSlug,
            @RequestHeader(
                    name = "If-None-Match",
                    required = false
            ) String ifNoneMatch
    ) {
        try {
            PublicStoryProjection story = stories.get(idOrSlug);
            String etag = "\"story-%s-v%d\"".formatted(
                    story.id(),
                    story.version()
            );
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .cacheControl(PUBLIC_CACHE)
                        .build();
            }
            return ResponseEntity.ok()
                    .eTag(etag)
                    .cacheControl(PUBLIC_CACHE)
                    .body(story);
        } catch (CatalogRequestException exception) {
            throw problem(exception);
        }
    }

    private static List<String> categories(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .toList();
    }

    private static Story.CompletionStatus completion(String value) {
        return parse(
                value,
                Story.CompletionStatus.class,
                "STATUS_INVALID"
        );
    }

    private static Story.Origin origin(String value) {
        return parse(value, Story.Origin.class, "ORIGIN_INVALID");
    }

    private static <T extends Enum<T>> T parse(
            String value,
            Class<T> type,
            String code
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(
                    type,
                    value.toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    code,
                    "Catalog request rejected",
                    "filter value is not supported"
            );
        }
    }

    private static ApiException problem(CatalogRequestException exception) {
        HttpStatus status = "STORY_NOT_FOUND".equals(exception.code())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return new ApiException(
                status,
                exception.code(),
                "Catalog request rejected",
                exception.getMessage()
        );
    }
}
