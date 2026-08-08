package com.storyplatform.unit.publishing.domain;

import com.storyplatform.publishing.domain.PublishingSchedule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublishingScheduleTest {

    private static final String ID =
            "10000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void rejectsDuplicateChaptersInvalidZoneAndChronology() {
        var chapter = new PublishingSchedule.FrozenChapterRevision(
                "20000000-0000-4000-8000-000000000001",
                "30000000-0000-4000-8000-000000000001",
                1
        );
        assertThatThrownBy(() -> schedule(
                List.of(chapter, chapter), "UTC", NOW
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> schedule(
                List.of(chapter), "Invalid/Zone", NOW
        )).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> schedule(
                List.of(chapter), "UTC", NOW.minusSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublishingSchedule
                .FrozenChapterRevision(ID, ID, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PublishingSchedule schedule(
            List<PublishingSchedule.FrozenChapterRevision> chapters,
            String zone,
            Instant updated
    ) {
        return new PublishingSchedule(
                ID,
                PublishingSchedule.TargetType.STORY,
                ID,
                ID,
                ID,
                chapters,
                PublishingSchedule.State.SCHEDULED,
                NOW.plusSeconds(60),
                zone,
                ID,
                NOW,
                updated,
                1
        );
    }
}
