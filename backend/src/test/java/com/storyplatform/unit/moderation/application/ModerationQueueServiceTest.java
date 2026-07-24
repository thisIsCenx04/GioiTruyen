package com.storyplatform.unit.moderation.application;

import com.storyplatform.moderation.application.ModerationQueueException;
import com.storyplatform.moderation.application.ModerationQueueOperations;
import com.storyplatform.moderation.application.ModerationQueueService;
import com.storyplatform.moderation.application.port
        .ModerationQueueCursorCodec;
import com.storyplatform.moderation.application.port
        .ModerationQueueRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationQueueServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final String REVIEW =
            "80000000-0000-4000-8000-000000000001";
    private static final String REVIEWER =
            "10000000-0000-4000-8000-000000000001";
    private final ModerationQueueRepository repository =
            mock(ModerationQueueRepository.class);
    private final ModerationQueueCursorCodec cursors =
            mock(ModerationQueueCursorCodec.class);

    @Test
    void listsByKeysetAndEncodesCursorOnlyWhenMoreExist() {
        var first = review(REVIEW, 90, NOW);
        var second = review(
                "80000000-0000-4000-8000-000000000002",
                70,
                NOW.plusSeconds(1)
        );
        when(repository.findClaimable(null, 2, NOW))
                .thenReturn(List.of(first, second));
        when(cursors.encode(any())).thenReturn("next");

        var page = service().list(1, null);

        assertThat(page.items()).containsExactly(first);
        assertThat(page.nextCursor()).isEqualTo("next");
        verify(cursors).encode(new ModerationQueueCursorCodec.Cursor(
                90,
                NOW,
                REVIEW
        ));
    }

    @Test
    void decodesCursorAndReturnsTerminalPage() {
        var cursor = new ModerationQueueCursorCodec.Cursor(
                70, NOW, REVIEW
        );
        when(cursors.decode("cursor")).thenReturn(cursor);
        when(repository.findClaimable(cursor, 21, NOW))
                .thenReturn(List.of(review(REVIEW, 70, NOW)));

        var page = service().list(20, "cursor");

        assertThat(page.items()).hasSize(1);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void claimsWithVersionAndBoundedLease() {
        var claimed = review(REVIEW, 90, NOW);
        when(repository.claim(
                REVIEW,
                REVIEWER,
                2,
                NOW,
                NOW.plus(Duration.ofMinutes(15))
        )).thenReturn(Optional.of(claimed));

        assertThat(service().claim(REVIEWER, REVIEW, 2))
                .isEqualTo(claimed);
    }

    @Test
    void rejectsInvalidPageCursorVersionAndClaimRace() {
        assertThatThrownBy(() -> service().list(0, null))
                .isInstanceOf(ModerationQueueException.class);
        assertThatThrownBy(() -> service().list(101, null))
                .isInstanceOf(ModerationQueueException.class);
        when(cursors.decode("bad")).thenThrow(
                new IllegalArgumentException("bad")
        );
        assertThatThrownBy(() -> service().list(20, "bad"))
                .isInstanceOf(ModerationQueueException.class)
                .extracting("code")
                .isEqualTo("MODERATION_QUEUE_INVALID");
        assertThatThrownBy(() -> service().claim(
                REVIEWER, REVIEW, 0
        )).isInstanceOf(ModerationQueueException.class);
        assertThatThrownBy(() -> service().claim(
                "invalid", REVIEW, 2
        )).isInstanceOf(ModerationQueueException.class);
        assertThatThrownBy(() -> service().claim(
                REVIEWER, "invalid", 2
        )).isInstanceOf(ModerationQueueException.class);
        when(repository.claim(
                eq(REVIEW), eq(REVIEWER), eq(2L), any(), any()
        )).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().claim(
                REVIEWER, REVIEW, 2
        )).isInstanceOf(ModerationQueueException.class)
                .extracting("code")
                .isEqualTo("REVIEW_CLAIM_CONFLICT");
    }

    @Test
    void rejectsNonPositiveClaimLeaseAtConfigurationBoundary() {
        assertThatThrownBy(() -> new ModerationQueueService(
                repository,
                cursors,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ZERO
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModerationQueueService(
                repository,
                cursors,
                Clock.fixed(NOW, ZoneOffset.UTC),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private ModerationQueueService service() {
        return new ModerationQueueService(
                repository,
                cursors,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15)
        );
    }

    static ModerationQueueOperations.ReviewCase review(
            String id,
            int priority,
            Instant submittedAt
    ) {
        return new ModerationQueueOperations.ReviewCase(
                id,
                "STORY",
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "OPEN",
                priority,
                false,
                List.of(new ModerationQueueOperations.CheckSummary(
                        "SCHEMA",
                        "PASS",
                        "FROZEN_EVIDENCE_VALID",
                        "publishing-precheck-v1"
                )),
                null,
                null,
                submittedAt,
                2
        );
    }
}
