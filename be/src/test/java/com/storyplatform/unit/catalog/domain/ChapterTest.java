package com.storyplatform.unit.catalog.domain;

import com.storyplatform.catalog.domain.Chapter;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterTest {

    @Test
    void acceptsValidPublishedAndDraftMetadata() {
        assertThat(chapter(
                Chapter.WorkflowStatus.PUBLISHED,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1),
                Instant.EPOCH,
                1,
                "chapter-1",
                "Chapter 1",
                1
        ).workflowStatus()).isEqualTo(Chapter.WorkflowStatus.PUBLISHED);
        assertThat(chapter(
                Chapter.WorkflowStatus.DRAFT,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                1,
                "chapter-1",
                "Chapter 1",
                1
        ).publishedAt()).isNull();
    }

    @Test
    void rejectsInvalidMetadataAndPublicationState() {
        assertInvalid(() -> chapter(Chapter.WorkflowStatus.DRAFT,
                Instant.EPOCH, Instant.EPOCH, null, 0,
                "chapter-1", "Chapter 1", 1));
        assertInvalid(() -> chapter(Chapter.WorkflowStatus.DRAFT,
                Instant.EPOCH, Instant.EPOCH, null, 1,
                "Bad slug", "Chapter 1", 1));
        assertInvalid(() -> chapter(Chapter.WorkflowStatus.DRAFT,
                Instant.EPOCH, Instant.EPOCH, null, 1,
                "chapter-1", " ", 1));
        assertInvalid(() -> chapter(Chapter.WorkflowStatus.DRAFT,
                Instant.EPOCH.plusSeconds(1), Instant.EPOCH, null, 1,
                "chapter-1", "Chapter 1", 1));
        assertInvalid(() -> chapter(Chapter.WorkflowStatus.DRAFT,
                Instant.EPOCH, Instant.EPOCH, null, 1,
                "chapter-1", "Chapter 1", 0));
        assertInvalid(() -> chapter(Chapter.WorkflowStatus.PUBLISHED,
                Instant.EPOCH, Instant.EPOCH, null, 1,
                "chapter-1", "Chapter 1", 1));
        assertInvalid(() -> chapter(Chapter.WorkflowStatus.HIDDEN,
                Instant.EPOCH, Instant.EPOCH, null, 1,
                "chapter-1", "Chapter 1", 1));
    }

    @Test
    void rejectsMalformedIdentifiersAndMissingRequiredValues() {
        assertThatThrownBy(() -> new Chapter(
                "bad",
                id(2),
                id(3),
                1,
                "chapter-1",
                "Chapter 1",
                Chapter.WorkflowStatus.DRAFT,
                id(4),
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                1
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Chapter(
                id(1),
                id(2),
                id(3),
                1,
                "chapter-1",
                "Chapter 1",
                null,
                id(4),
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                1
        )).isInstanceOf(NullPointerException.class);
    }

    private static void assertInvalid(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Chapter chapter(
            Chapter.WorkflowStatus status,
            Instant created,
            Instant updated,
            Instant published,
            int number,
            String slug,
            String title,
            long version
    ) {
        return new Chapter(
                id(1),
                id(2),
                id(3),
                number,
                slug,
                title,
                status,
                id(4),
                null,
                published,
                created,
                updated,
                version
        );
    }

    private static String id(int suffix) {
        return "10000000-0000-4000-8000-%012d".formatted(suffix);
    }
}
