package com.storyplatform.catalog.application;

import java.util.List;

public interface CategoryOperations {

    TaxonomyView getTaxonomy();

    record TaxonomyView(
            String version,
            List<CategoryGroupView> groups
    ) {
        public TaxonomyView {
            groups = List.copyOf(groups);
        }
    }

    record CategoryGroupView(
            String group,
            String label,
            List<CategoryView> categories
    ) {
        public CategoryGroupView {
            categories = List.copyOf(categories);
        }
    }

    record CategoryView(
            String id,
            String slug,
            String name
    ) {
    }
}
