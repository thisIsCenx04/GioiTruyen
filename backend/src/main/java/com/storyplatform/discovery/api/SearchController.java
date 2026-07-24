package com.storyplatform.discovery.api;

import com.storyplatform.discovery.application.SearchOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Objects;

@RestController
public final class SearchController {

    private static final CacheControl PUBLIC_CACHE =
            CacheControl.maxAge(Duration.ofSeconds(30))
                    .cachePublic()
                    .staleWhileRevalidate(Duration.ofMinutes(2));

    private final SearchOperations search;

    public SearchController(SearchOperations search) {
        this.search = Objects.requireNonNull(search, "search");
    }

    @GetMapping("/search")
    public ResponseEntity<SearchOperations.SearchResponse> search(
            @RequestParam(name = "q") String query,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(PUBLIC_CACHE)
                    .body(search.search(new SearchOperations.SearchRequest(
                            query,
                            categoryId,
                            status,
                            origin,
                            cursor,
                            limit
                    )));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SEARCH_INVALID",
                    "Search request rejected",
                    exception.getMessage()
            );
        }
    }
}
