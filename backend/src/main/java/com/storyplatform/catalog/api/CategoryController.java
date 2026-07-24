package com.storyplatform.catalog.api;

import com.storyplatform.catalog.application.CategoryOperations;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Objects;

@RestController
public final class CategoryController {

    private final CategoryOperations categories;

    public CategoryController(CategoryOperations categories) {
        this.categories = Objects.requireNonNull(categories, "categories");
    }

    @GetMapping("/categories")
    public ResponseEntity<CategoryOperations.TaxonomyView> getTaxonomy() {
        CacheControl cache = CacheControl.maxAge(Duration.ofMinutes(5))
                .cachePublic()
                .staleWhileRevalidate(Duration.ofHours(1));
        return ResponseEntity.ok()
                .cacheControl(cache)
                .body(categories.getTaxonomy());
    }
}
