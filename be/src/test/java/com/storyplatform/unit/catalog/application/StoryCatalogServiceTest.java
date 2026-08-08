package com.storyplatform.unit.catalog.application;

import com.storyplatform.catalog.application.CategoryOperations;
import com.storyplatform.catalog.application.CatalogRequestException;
import com.storyplatform.catalog.application.PublicStoryProjection;
import com.storyplatform.catalog.application.StoryCatalogOperations;
import com.storyplatform.catalog.application.StoryCatalogService;
import com.storyplatform.catalog.application.port.CatalogCursorCodec;
import com.storyplatform.catalog.application.port.StoryRepository;
import com.storyplatform.catalog.domain.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryCatalogServiceTest {

    private StoryRepository repository;
    private CatalogCursorCodec cursors;
    private StoryCatalogService service;

    @BeforeEach
    void setUp() {
        repository = mock(StoryRepository.class);
        cursors = mock(CatalogCursorCodec.class);
        CategoryOperations categories = mock(CategoryOperations.class);
        when(categories.getTaxonomy()).thenReturn(
                new CategoryOperations.TaxonomyView(
                        "v1",
                        List.of(new CategoryOperations.CategoryGroupView(
                                "genre",
                                "Thể loại",
                                List.of(new CategoryOperations.CategoryView(
                                        "30000000-0000-4000-8000-000000000001",
                                        "fantasy",
                                        "Kỳ ảo"
                                ))
                        ))
                )
        );
        service = new StoryCatalogService(
                repository,
                categories,
                cursors
        );
    }

    @Test
    void resolvesFiltersAndBuildsANextCursorFromTheLastVisibleItem() {
        when(repository.findPublished(any())).thenReturn(List.of(
                projection("story-a", 2),
                projection("story-b", 1)
        ));
        when(cursors.encode(any())).thenReturn("next");

        var page = service.list(new StoryCatalogOperations.StoryFilter(
                List.of("fantasy"),
                Story.CompletionStatus.ONGOING,
                Story.Origin.ORIGINAL,
                "20000000-0000-4000-8000-000000000002",
                "published_desc",
                null,
                1
        ));

        assertThat(page.items()).hasSize(1);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo("next");
        ArgumentCaptor<StoryRepository.StoryListQuery> query =
                ArgumentCaptor.forClass(
                        StoryRepository.StoryListQuery.class
                );
        verify(repository).findPublished(query.capture());
        assertThat(query.getValue().categoryIds())
                .containsExactly(
                        "30000000-0000-4000-8000-000000000001"
                );
        assertThat(query.getValue().limit()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownCategoriesAndCursorSortMismatch() {
        assertCode(() -> service.list(
                filter(List.of("missing"), null)
        ), "CATEGORY_FILTER_INVALID");
        when(cursors.decode("cursor")).thenReturn(
                new CatalogCursorCodec.Cursor(
                        StoryRepository.Sort.UPDATED_DESC,
                        Instant.EPOCH,
                        "20000000-0000-4000-8000-000000000001"
                )
        );
        assertCode(() -> service.list(
                filter(List.of(), "cursor")
        ), "CURSOR_SORT_MISMATCH");
    }

    @Test
    void returnsOnlyPublishedRepositoryResults() {
        PublicStoryProjection story = projection("story-a", 1);
        when(repository.findPublishedByIdOrSlug("story-a"))
                .thenReturn(Optional.of(story));

        assertThat(service.get("story-a")).isSameAs(story);
        assertCode(() -> service.get("missing"), "STORY_NOT_FOUND");
    }

    private static StoryCatalogOperations.StoryFilter filter(
            List<String> categories,
            String cursor
    ) {
        return new StoryCatalogOperations.StoryFilter(
                categories,
                null,
                null,
                null,
                "published_desc",
                cursor,
                20
        );
    }

    private static PublicStoryProjection projection(
            String slug,
            int offset
    ) {
        Instant value = Instant.parse("2026-07-24T00:00:00Z")
                .plusSeconds(offset);
        return new PublicStoryProjection(
                "20000000-0000-4000-8000-%012d".formatted(offset),
                "20000000-0000-4000-8000-000000000002",
                slug,
                slug,
                "Synopsis",
                List.of(),
                Story.Origin.ORIGINAL,
                "vi",
                Story.CompletionStatus.ONGOING,
                value,
                value,
                1
        );
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        CatalogRequestException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(code)
                );
    }
}
