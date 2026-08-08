package com.storyplatform.unit.publishing.application;

import com.storyplatform.publishing.application.PublishingPrecheckEngine;
import com.storyplatform.publishing.application.PublishingPrecheckService;
import com.storyplatform.publishing.application
        .PublishingPrecheckTimeoutException;
import com.storyplatform.publishing.application.port
        .PublishingPrecheckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingPrecheckServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final PublishingPrecheckRepository repository =
            mock(PublishingPrecheckRepository.class);
    private final PublishingPrecheckEngine engine =
            mock(PublishingPrecheckEngine.class);

    @BeforeEach
    void setUp() {
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.of(review()));
        when(repository.loadEvidence(any()))
                .thenReturn(Optional.of(evidence()));
    }

    @Test
    void completesPassingChecksWithoutManualFallback() {
        List<PublishingPrecheckEngine.CheckResult> results = List.of(
                result(
                        PublishingPrecheckEngine.Rule.SCHEMA,
                        PublishingPrecheckEngine.Outcome.PASS,
                        "FROZEN_EVIDENCE_VALID"
                )
        );
        when(engine.check(any(), any())).thenReturn(results);

        assertThat(service().processNext("worker-1")).isTrue();

        verify(repository).complete(
                review(),
                "worker-1",
                results,
                false,
                NOW
        );
    }

    @Test
    void flaggedCheckRoutesToManualReview() {
        List<PublishingPrecheckEngine.CheckResult> results = List.of(
                result(
                        PublishingPrecheckEngine.Rule.QR_POLICY,
                        PublishingPrecheckEngine.Outcome.FLAG,
                        "PAYMENT_QR_SIGNAL"
                )
        );
        when(engine.check(any(), any())).thenReturn(results);

        assertThat(service().processNext("worker-1")).isTrue();

        verify(repository).complete(
                review(),
                "worker-1",
                results,
                true,
                NOW
        );
    }

    @Test
    void timeoutAndEngineFailureFallBackWithoutRawError() {
        ArgumentCaptor<List<PublishingPrecheckEngine.CheckResult>> checks =
                ArgumentCaptor.forClass(List.class);
        when(engine.check(any(), any()))
                .thenThrow(new PublishingPrecheckTimeoutException());

        service().processNext("worker-1");

        verify(repository).complete(
                eq(review()),
                eq("worker-1"),
                checks.capture(),
                eq(true),
                eq(NOW)
        );
        assertThat(checks.getValue().getFirst().code())
                .isEqualTo("PRECHECK_TIMEOUT");

        org.mockito.Mockito.reset(repository, engine);
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.of(review()));
        when(repository.loadEvidence(any()))
                .thenReturn(Optional.of(evidence()));
        when(engine.check(any(), any()))
                .thenThrow(new IllegalStateException("sensitive detail"));
        service().processNext("worker-2");
        verify(repository).complete(
                eq(review()),
                eq("worker-2"),
                checks.capture(),
                eq(true),
                eq(NOW)
        );
        assertThat(checks.getAllValues().getLast().getFirst().code())
                .isEqualTo("PRECHECK_UNAVAILABLE");
    }

    @Test
    void missingFrozenEvidenceFallsBackToManualQueue() {
        ArgumentCaptor<List<PublishingPrecheckEngine.CheckResult>> checks =
                ArgumentCaptor.forClass(List.class);
        when(repository.loadEvidence(any())).thenReturn(Optional.empty());

        service().processNext("worker-1");

        verify(repository).complete(
                eq(review()),
                eq("worker-1"),
                checks.capture(),
                eq(true),
                eq(NOW)
        );
        assertThat(checks.getValue().getFirst().code())
                .isEqualTo("FROZEN_EVIDENCE_MISSING");
        verify(engine, never()).check(any(), any());
    }

    @Test
    void emptyQueueReturnsFalseAndInvalidWorkerIsRejected() {
        when(repository.claim(any(), any(), any()))
                .thenReturn(Optional.empty());
        assertThat(service().processNext("worker-1")).isFalse();
        verify(repository, never()).loadEvidence(any());

        assertThatThrownBy(() -> service().processNext(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PublishingPrecheckService service() {
        return new PublishingPrecheckService(
                repository,
                engine,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5)
        );
    }

    private static PublishingPrecheckEngine.CheckResult result(
            PublishingPrecheckEngine.Rule rule,
            PublishingPrecheckEngine.Outcome outcome,
            String code
    ) {
        return new PublishingPrecheckEngine.CheckResult(
                rule,
                outcome,
                code,
                PublishingPrecheckService.POLICY_VERSION
        );
    }

    static PublishingPrecheckRepository.ClaimedReview review() {
        return new PublishingPrecheckRepository.ClaimedReview(
                "80000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "40000000-0000-4000-8000-000000000001",
                "50000000-0000-4000-8000-000000000001",
                List.of(new PublishingPrecheckRepository.FrozenChapter(
                        "60000000-0000-4000-8000-000000000001",
                        "70000000-0000-4000-8000-000000000001",
                        1
                )),
                2,
                NOW.plusSeconds(30)
        );
    }

    static PublishingPrecheckRepository.ReviewEvidence evidence() {
        return new PublishingPrecheckRepository.ReviewEvidence(
                review(),
                "Story",
                "Synopsis",
                null,
                null,
                List.of(new PublishingPrecheckRepository.ChapterEvidence(
                        review().chapters().getFirst().chapterId(),
                        "<p>Hello world</p>",
                        "Hello world",
                        "a".repeat(64)
                ))
        );
    }
}
