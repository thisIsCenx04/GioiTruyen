package com.storyplatform.unit.catalog.domain;

import com.storyplatform.catalog.domain.Category;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CategoryTest {

    @Test
    void exposesStableExternalGroupKeys() {
        assertThat(Category.Group.values())
                .extracting(Category.Group::key)
                .containsExactly(
                        "genre",
                        "setting",
                        "tone",
                        "ending",
                        "relationship",
                        "format"
                );
        assertThat(Category.Group.fromKey("GENRE"))
                .isEqualTo(Category.Group.GENRE);
    }

    @Test
    void rejectsUnknownGroupsAndUnsafeSlugs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Category.Group.fromKey("audience"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> category(
                        "id-1",
                        "Not Safe",
                        Category.Group.GENRE,
                        10
                ));
    }

    private static Category category(
            String id,
            String slug,
            Category.Group group,
            int sortOrder
    ) {
        return new Category(
                id,
                slug,
                "Tên",
                group,
                sortOrder,
                true,
                1
        );
    }
}
