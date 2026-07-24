package com.storyplatform.unit.publishing.domain;

import com.storyplatform.publishing.domain.ChapterDraft;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChapterDraftTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void acceptsAChapterPointingAtALaterImmutableRevision() {
        ChapterDraft chapter = chapter(
                1,
                "chapter-1",
                "Title",
                9,
                NOW,
                7
        );

        assertThat(chapter.currentRevisionNo()).isEqualTo(9);
        assertThat(chapter.version()).isEqualTo(7);
    }

    @Test
    void rejectsEveryInvalidVersionedMetadataBoundary() {
        assertInvalid(() -> chapter(0, "chapter-1", "Title", 1, NOW, 1));
        assertInvalid(() -> chapter(1, null, "Title", 1, NOW, 1));
        assertInvalid(() -> chapter(1, "Bad slug", "Title", 1, NOW, 1));
        assertInvalid(() -> chapter(
                1,
                "a".repeat(101),
                "Title",
                1,
                NOW,
                1
        ));
        assertInvalid(() -> chapter(1, "chapter-1", " ", 1, NOW, 1));
        assertInvalid(() -> chapter(
                1,
                "chapter-1",
                "a".repeat(201),
                1,
                NOW,
                1
        ));
        assertInvalid(() -> chapter(
                1, "chapter-1", "Title", 0, NOW, 1
        ));
        assertInvalid(() -> chapter(
                1, "chapter-1", "Title", 1, NOW, 0
        ));
        assertInvalid(() -> chapter(
                1,
                "chapter-1",
                "Title",
                1,
                NOW.minusSeconds(1),
                1
        ));
    }

    private static ChapterDraft chapter(
            int number,
            String slug,
            String title,
            long revision,
            Instant updatedAt,
            long version
    ) {
        return new ChapterDraft(
                "60000000-0000-4000-8000-000000000001",
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                number,
                slug,
                title,
                ChapterDraft.WorkflowStatus.DRAFT,
                "70000000-0000-4000-8000-000000000001",
                revision,
                NOW,
                updatedAt,
                version
        );
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(IllegalArgumentException.class);
    }
}
