package com.storyplatform.unit.moderation.application;

import com.storyplatform.moderation.application.CopyrightCaseException;
import com.storyplatform.moderation.application.CopyrightCaseOperations;
import com.storyplatform.moderation.application.CopyrightCaseService;
import com.storyplatform.moderation.application.port.CopyrightCaseRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopyrightCaseServiceTest {

    private static final String CLAIMANT =
            "10000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "20000000-0000-4000-8000-000000000001";
    private static final String EVIDENCE =
            "30000000-0000-4000-8000-000000000001";
    private static final String CASE =
            "40000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final CopyrightCaseRepository repository =
            mock(CopyrightCaseRepository.class);

    @Test
    void createsSanitizedCaseWithSnapshottedSlaAndHold() {
        when(repository.storyIsPublic(STORY)).thenReturn(true);
        when(repository.evidenceIsPrivateAndOwned(
                CLAIMANT, List.of(EVIDENCE)
        )).thenReturn(true);
        when(repository.createAndHold(any())).thenAnswer(call ->
                new CopyrightCaseRepository.CreateResult(
                        CopyrightCaseRepository.Outcome.SUCCESS,
                        call.getArgument(0)
                ));

        var created = service().create(CLAIMANT, command());

        assertThat(created.claimantName()).isEqualTo("Author");
        assertThat(created.statement()).isEqualTo("Original work");
        assertThat(created.responseDueAt())
                .isEqualTo(NOW.plus(Duration.ofHours(48)));
        assertThat(created.holdUntil())
                .isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(created.status()).isEqualTo("PENDING");
    }

    @Test
    void rejectsMissingStoryInaccessibleEvidenceAndDuplicates() {
        assertCode(() -> service().create(CLAIMANT, command()),
                "COPYRIGHT_STORY_NOT_FOUND");
        when(repository.storyIsPublic(STORY)).thenReturn(true);
        assertCode(() -> service().create(CLAIMANT, command()),
                "COPYRIGHT_EVIDENCE_FORBIDDEN");
        when(repository.evidenceIsPrivateAndOwned(any(), any()))
                .thenReturn(true);
        when(repository.createAndHold(any())).thenReturn(
                new CopyrightCaseRepository.CreateResult(
                        CopyrightCaseRepository.Outcome.DUPLICATE,
                        null
                )
        );
        assertCode(() -> service().create(CLAIMANT, command()),
                "COPYRIGHT_CASE_EXISTS");
        when(repository.createAndHold(any())).thenReturn(
                new CopyrightCaseRepository.CreateResult(
                        CopyrightCaseRepository.Outcome.STORY_CHANGED,
                        null
                )
        );
        assertCode(() -> service().create(CLAIMANT, command()),
                "COPYRIGHT_STORY_CHANGED");
    }

    @Test
    void acceptsAppealAndFinalDecisionAndMapsConflicts() {
        var view = view();
        when(repository.appeal(any(), any(), any(), any())).thenReturn(
                new CopyrightCaseRepository.CreateResult(
                        CopyrightCaseRepository.Outcome.SUCCESS,
                        view
                )
        );
        assertThat(service().appeal(CLAIMANT, CASE, "<b>Counter</b>"))
                .isEqualTo(view);
        verify(repository).appeal(CASE, CLAIMANT, "Counter", NOW);

        when(repository.decide(any(), any(), any(), any(), any(), any()))
                .thenReturn(new CopyrightCaseRepository.CreateResult(
                        CopyrightCaseRepository.Outcome.SUCCESS,
                        view
                ));
        assertThat(service().decide(
                CLAIMANT,
                CASE,
                CopyrightCaseOperations.Decision.REINSTATE,
                "COUNTER_NOTICE_VALID",
                " Release "
        )).isEqualTo(view);

        when(repository.appeal(any(), any(), any(), any())).thenReturn(
                new CopyrightCaseRepository.CreateResult(
                        CopyrightCaseRepository.Outcome.DUPLICATE,
                        null
                )
        );
        assertCode(() -> service().appeal(
                CLAIMANT, CASE, "Counter"
        ), "COPYRIGHT_APPEAL_CONFLICT");
        when(repository.decide(any(), any(), any(), any(), any(), any()))
                .thenReturn(new CopyrightCaseRepository.CreateResult(
                        CopyrightCaseRepository.Outcome.STORY_CHANGED,
                        null
                ));
        assertCode(() -> service().decide(
                CLAIMANT,
                CASE,
                CopyrightCaseOperations.Decision.TAKEDOWN,
                "INFRINGEMENT_CONFIRMED",
                "Final"
        ), "COPYRIGHT_DECISION_CONFLICT");
    }

    @Test
    void validatesDurationsIdentifiersTextEvidenceAndDecision() {
        assertThatThrownBy(() -> new CopyrightCaseService(
                repository,
                Clock.systemUTC(),
                Duration.ZERO,
                Duration.ofDays(1),
                () -> CASE
        )).isInstanceOf(IllegalArgumentException.class);
        assertCode(() -> service().create("bad", command()),
                "COPYRIGHT_CASE_INVALID");
        assertCode(() -> service().create(CLAIMANT, null),
                "COPYRIGHT_CASE_INVALID");
        assertCode(() -> service().create(
                CLAIMANT,
                new CopyrightCaseOperations.CreateCopyrightCase(
                        STORY, "Author", "Statement", List.of()
                )
        ), "COPYRIGHT_CASE_INVALID");
        assertCode(() -> service().decide(
                CLAIMANT, CASE, null, "VALID_CODE", "note"
        ), "COPYRIGHT_CASE_INVALID");
        assertCode(() -> service().decide(
                CLAIMANT,
                CASE,
                CopyrightCaseOperations.Decision.TAKEDOWN,
                "bad",
                "note"
        ), "COPYRIGHT_CASE_INVALID");
    }

    private CopyrightCaseService service() {
        return new CopyrightCaseService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofHours(48),
                Duration.ofDays(7),
                () -> CASE
        );
    }

    private static CopyrightCaseOperations.CreateCopyrightCase command() {
        return new CopyrightCaseOperations.CreateCopyrightCase(
                STORY,
                " <b>Author</b> ",
                "<script>bad()</script><p>Original work</p>",
                List.of(EVIDENCE)
        );
    }

    private static CopyrightCaseOperations.CopyrightCaseView view() {
        return new CopyrightCaseOperations.CopyrightCaseView(
                CASE,
                STORY,
                CLAIMANT,
                "Author",
                "Statement",
                List.of(EVIDENCE),
                "PENDING",
                NOW,
                NOW.plusSeconds(1),
                NOW.plusSeconds(2),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CopyrightCaseException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
