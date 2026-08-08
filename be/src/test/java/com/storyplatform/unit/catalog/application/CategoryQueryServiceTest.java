package com.storyplatform.unit.catalog.application;

import com.storyplatform.catalog.application.CategoryQueryService;
import com.storyplatform.catalog.application.port.CategoryRepository;
import com.storyplatform.catalog.domain.Category;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CategoryQueryServiceTest {

    @Test
    void groupsEveryAxisAndSortsCategoriesWithinTheirGroup() {
        CategoryRepository repository = () -> List.of(
                category("id-2", "romance", Category.Group.GENRE, 20),
                category("id-3", "modern", Category.Group.SETTING, 10),
                category("id-1", "fantasy", Category.Group.GENRE, 10)
        );

        var taxonomy = new CategoryQueryService(repository).getTaxonomy();

        assertThat(taxonomy.version())
                .isEqualTo(CategoryQueryService.TAXONOMY_VERSION);
        assertThat(taxonomy.groups())
                .extracting(group -> group.group())
                .containsExactly(
                        "genre",
                        "setting",
                        "tone",
                        "ending",
                        "relationship",
                        "format"
                );
        assertThat(taxonomy.groups().getFirst().categories())
                .extracting(category -> category.slug())
                .containsExactly("fantasy", "romance");
    }

    @Test
    void permitsTheSameSlugOnDifferentTaxonomyAxes() {
        CategoryRepository repository = () -> List.of(
                category("id-1", "original", Category.Group.GENRE, 10),
                category("id-2", "original", Category.Group.FORMAT, 10)
        );

        assertThat(new CategoryQueryService(repository)
                .getTaxonomy()
                .groups())
                .extracting(group -> group.categories().size())
                .containsExactly(1, 0, 0, 0, 0, 1);
    }

    @Test
    void failsClosedWhenTheSourceContainsAGroupedDuplicate() {
        CategoryRepository repository = () -> List.of(
                category("id-1", "fantasy", Category.Group.GENRE, 10),
                category("id-2", "fantasy", Category.Group.GENRE, 20)
        );

        assertThatIllegalStateException()
                .isThrownBy(() ->
                        new CategoryQueryService(repository).getTaxonomy())
                .withMessageContaining("group and slug");
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
                slug,
                group,
                sortOrder,
                true,
                1
        );
    }
}
