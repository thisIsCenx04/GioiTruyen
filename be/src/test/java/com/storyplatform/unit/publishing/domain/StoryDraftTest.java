package com.storyplatform.unit.publishing.domain;

import com.storyplatform.publishing.domain.StoryDraft;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryDraftTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final String CATEGORY =
            "30000000-0000-4000-8000-000000000001";

    @Test
    void preservesValidatedDraftStateAndOptionalCover() {
        StoryDraft draft = draft(
                "truyen-mot-40000000",
                "Truyện Một",
                "Tóm tắt",
                "vi",
                List.of(CATEGORY),
                "70000000-0000-4000-8000-000000000001",
                NOW,
                1
        );

        assertThat(draft.coverAssetId())
                .isEqualTo("70000000-0000-4000-8000-000000000001");
        assertThat(draft.workflowStatus())
                .isEqualTo(StoryDraft.WorkflowStatus.DRAFT);
    }

    @Test
    void rejectsInvalidSlugMetadataAndLanguage() {
        assertInvalid(() -> draft(
                "Invalid Slug", "Truyện", "Tóm tắt", "vi",
                List.of(CATEGORY), null, NOW, 1
        ));
        assertInvalid(() -> draft(
                "valid", " ", "Tóm tắt", "vi",
                List.of(CATEGORY), null, NOW, 1
        ));
        assertInvalid(() -> draft(
                "valid", "Truyện", " ", "vi",
                List.of(CATEGORY), null, NOW, 1
        ));
        assertInvalid(() -> draft(
                "valid", "Truyện", "Tóm tắt", "invalid_language",
                List.of(CATEGORY), null, NOW, 1
        ));
    }

    @Test
    void rejectsInvalidTaxonomyVersionAndTimestamps() {
        assertInvalid(() -> draft(
                "valid", "Truyện", "Tóm tắt", "vi",
                List.of(), null, NOW, 1
        ));
        assertInvalid(() -> draft(
                "valid", "Truyện", "Tóm tắt", "vi",
                List.of(CATEGORY, CATEGORY), null, NOW, 1
        ));
        assertInvalid(() -> draft(
                "valid", "Truyện", "Tóm tắt", "vi",
                List.of("bad"), null, NOW, 1
        ));
        assertInvalid(() -> draft(
                "valid", "Truyện", "Tóm tắt", "vi",
                List.of(CATEGORY), null, NOW.minusSeconds(1), 1
        ));
        assertInvalid(() -> draft(
                "valid", "Truyện", "Tóm tắt", "vi",
                List.of(CATEGORY), null, NOW, 0
        ));
    }

    private static void assertInvalid(Supplier<StoryDraft> action) {
        assertThatThrownBy(action::get)
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static StoryDraft draft(
            String slug,
            String title,
            String synopsis,
            String language,
            List<String> categories,
            String cover,
            Instant updatedAt,
            long version
    ) {
        return new StoryDraft(
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                slug,
                title,
                synopsis,
                categories,
                StoryDraft.Origin.ORIGINAL,
                language,
                StoryDraft.CompletionStatus.ONGOING,
                StoryDraft.WorkflowStatus.DRAFT,
                "50000000-0000-4000-8000-000000000001",
                cover,
                NOW,
                updatedAt,
                version
        );
    }
}
