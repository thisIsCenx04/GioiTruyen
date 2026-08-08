package com.storyplatform.catalog.application;

import com.storyplatform.catalog.application.port.CategoryRepository;
import com.storyplatform.catalog.domain.Category;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CategoryQueryService implements CategoryOperations {

    public static final String TAXONOMY_VERSION = "2026-07-24.1";

    private final CategoryRepository categories;

    public CategoryQueryService(CategoryRepository categories) {
        this.categories = Objects.requireNonNull(categories, "categories");
    }

    @Override
    public TaxonomyView getTaxonomy() {
        Map<Category.Group, List<Category>> grouped =
                new EnumMap<>(Category.Group.class);
        for (Category.Group group : Category.Group.values()) {
            grouped.put(group, new ArrayList<>());
        }

        Set<String> ids = new HashSet<>();
        Set<String> groupedSlugs = new HashSet<>();
        for (Category category : categories.findActiveOrdered()) {
            requireUnique(ids, category.id(), "category id");
            requireUnique(
                    groupedSlugs,
                    category.group().key() + ":" + category.slug(),
                    "category group and slug"
            );
            grouped.get(category.group()).add(category);
        }

        Comparator<Category> order = Comparator
                .comparingInt(Category::sortOrder)
                .thenComparing(Category::slug);
        List<CategoryGroupView> groups = new ArrayList<>();
        for (Category.Group group : Category.Group.values()) {
            List<CategoryView> views = grouped.get(group).stream()
                    .sorted(order)
                    .map(category -> new CategoryView(
                            category.id(),
                            category.slug(),
                            category.name()
                    ))
                    .toList();
            groups.add(new CategoryGroupView(
                    group.key(),
                    group.label(),
                    views
            ));
        }
        return new TaxonomyView(TAXONOMY_VERSION, groups);
    }

    private static void requireUnique(
            Set<String> seen,
            String value,
            String field
    ) {
        if (!seen.add(value)) {
            throw new IllegalStateException(
                    "Duplicate " + field + " in taxonomy source"
            );
        }
    }
}
