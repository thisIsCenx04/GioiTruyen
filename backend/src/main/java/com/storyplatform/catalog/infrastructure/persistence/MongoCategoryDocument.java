package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.domain.Category;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = MongoCategoryDocument.COLLECTION)
public record MongoCategoryDocument(
        @Id String id,
        String slug,
        String name,
        Category.Group group,
        int groupOrder,
        int sortOrder,
        boolean active,
        long version
) {
    public static final String COLLECTION = "categories";

    Category toDomain() {
        return new Category(
                id,
                slug,
                name,
                group,
                sortOrder,
                active,
                version
        );
    }
}
