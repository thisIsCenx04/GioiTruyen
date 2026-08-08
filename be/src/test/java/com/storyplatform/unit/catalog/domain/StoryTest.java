package com.storyplatform.unit.catalog.domain;

import com.storyplatform.catalog.domain.Story;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StoryTest {

    @Test
    void preservesBoundedMetadataAndPublicState() {
        Story story = story(
                List.of("Tên cũ"),
                List.of("30000000-0000-4000-8000-000000000001"),
                Story.WorkflowStatus.PUBLISHED,
                Instant.parse("2026-07-24T00:00:00Z")
        );

        assertThat(story.publiclyVisible()).isTrue();
        assertThat(story.aliases()).containsExactly("Tên cũ");
        assertThat(story.categoryIds()).hasSize(1);
    }

    @Test
    void rejectsDuplicateAliasesAndTaxonomyIds() {
        assertThatIllegalArgumentException().isThrownBy(() -> story(
                List.of("Tên cũ", "TÊN CŨ"),
                List.of(),
                Story.WorkflowStatus.DRAFT,
                null
        )).withMessageContaining("aliases contains duplicates");

        assertThatIllegalArgumentException().isThrownBy(() -> story(
                List.of(),
                List.of(
                        "30000000-0000-4000-8000-000000000001",
                        "30000000-0000-4000-8000-000000000001"
                ),
                Story.WorkflowStatus.DRAFT,
                null
        )).withMessageContaining("categoryIds contains duplicates");
    }

    @Test
    void requiresAPublishTimestampForVisibleStories() {
        assertThatIllegalArgumentException().isThrownBy(() -> story(
                List.of(),
                List.of(),
                Story.WorkflowStatus.PUBLISHED,
                null
        )).withMessageContaining("publishedAt");
    }

    private static Story story(
            List<String> aliases,
            List<String> categoryIds,
            Story.WorkflowStatus status,
            Instant publishedAt
    ) {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new Story(
                "20000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000002",
                "nguoi-giu-den",
                "Người giữ đèn",
                aliases,
                "Một câu chuyện dài.",
                categoryIds,
                Story.Origin.ORIGINAL,
                "vi",
                Story.CompletionStatus.ONGOING,
                status,
                "20000000-0000-4000-8000-000000000003",
                null,
                publishedAt,
                now,
                now,
                1
        );
    }
}
