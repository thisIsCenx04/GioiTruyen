package com.storyplatform.unit.moderation.application;

import com.storyplatform.moderation.application.ModerationAppealException;
import com.storyplatform.moderation.application.ModerationAppealOperations;
import com.storyplatform.moderation.application.ModerationAppealService;
import com.storyplatform.moderation.application.port
        .ModerationAppealRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationAppealServiceTest {

    private static final String REVIEW =
            "80000000-0000-4000-8000-000000000001";
    private static final String APPEAL =
            "80000000-0000-4000-8000-000000000002";
    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String ORIGINAL =
            "10000000-0000-4000-8000-000000000002";
    private static final String REVIEWER =
            "10000000-0000-4000-8000-000000000003";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final ModerationAppealRepository repository =
            mock(ModerationAppealRepository.class);

    @Test
    void createsSanitizedAppealInsideWindow() {
        var eligible = new ModerationAppealRepository.EligibleReview(
                REVIEW,
                ORIGINAL,
                NOW.minus(Duration.ofDays(2))
        );
        var view = view("PENDING", null);
        when(repository.eligibleReview(REVIEW, ACTOR))
                .thenReturn(Optional.of(eligible));
        when(repository.createIfAbsent(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(new ModerationAppealRepository.CreateResult(
                ModerationAppealRepository.Outcome.SUCCESS,
                view
        ));

        assertThat(service().create(
                ACTOR,
                REVIEW,
                "<script>bad()</script>  Please review "
        )).isEqualTo(view);

        verify(repository).createIfAbsent(
                APPEAL,
                eligible,
                ACTOR,
                "Please review",
                NOW,
                NOW.plus(Duration.ofDays(5))
        );
    }

    @Test
    void rejectsIneligibleExpiredAndDuplicateAppeals() {
        when(repository.eligibleReview(REVIEW, ACTOR))
                .thenReturn(Optional.empty());
        assertCode(
                () -> service().create(ACTOR, REVIEW, "Reason"),
                "APPEAL_NOT_ELIGIBLE"
        );

        when(repository.eligibleReview(REVIEW, ACTOR)).thenReturn(Optional.of(
                new ModerationAppealRepository.EligibleReview(
                        REVIEW,
                        ORIGINAL,
                        NOW.minus(Duration.ofDays(8))
                )
        ));
        assertCode(
                () -> service().create(ACTOR, REVIEW, "Reason"),
                "APPEAL_WINDOW_EXPIRED"
        );

        when(repository.eligibleReview(REVIEW, ACTOR)).thenReturn(Optional.of(
                new ModerationAppealRepository.EligibleReview(
                        REVIEW,
                        ORIGINAL,
                        NOW
                )
        ));
        when(repository.createIfAbsent(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(new ModerationAppealRepository.CreateResult(
                ModerationAppealRepository.Outcome.DUPLICATE,
                view("PENDING", null)
        ));
        assertCode(
                () -> service().create(ACTOR, REVIEW, "Reason"),
                "APPEAL_ALREADY_EXISTS"
        );
    }

    @Test
    void recordsIndependentFinalDecisionAndMapsConflict() {
        var finalView = view(
                "OVERTURNED",
                ModerationAppealOperations.AppealDecision.OVERTURN
        );
        when(repository.resolve(
                any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ModerationAppealRepository.ResolveResult(
                ModerationAppealRepository.Outcome.SUCCESS,
                finalView
        ));

        assertThat(service().decide(
                REVIEWER,
                REVIEW,
                APPEAL,
                ModerationAppealOperations.AppealDecision.OVERTURN,
                "NEW_EVIDENCE",
                "<b>Accepted</b>"
        )).isEqualTo(finalView);
        verify(repository).resolve(
                REVIEW,
                APPEAL,
                REVIEWER,
                ModerationAppealOperations.AppealDecision.OVERTURN,
                "NEW_EVIDENCE",
                "Accepted",
                NOW
        );

        when(repository.resolve(
                any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new ModerationAppealRepository.ResolveResult(
                ModerationAppealRepository.Outcome.CONFLICT,
                null
        ));
        assertCode(() -> service().decide(
                REVIEWER,
                REVIEW,
                APPEAL,
                ModerationAppealOperations.AppealDecision.UPHOLD,
                "POLICY_CONFIRMED",
                "Final"
        ), "APPEAL_DECISION_CONFLICT");
    }

    @Test
    void validatesIdentifiersTextReasonAndWindow() {
        assertThatThrownBy(() -> new ModerationAppealService(
                repository,
                Clock.systemUTC(),
                Duration.ZERO,
                () -> APPEAL
        )).isInstanceOf(IllegalArgumentException.class);
        assertCode(() -> service().create("bad", REVIEW, "Reason"),
                "APPEAL_INVALID");
        assertCode(() -> service().create(ACTOR, REVIEW, " "),
                "APPEAL_INVALID");
        assertCode(() -> service().decide(
                REVIEWER, REVIEW, APPEAL, null, "VALID_CODE", "note"
        ), "APPEAL_INVALID");
        assertCode(() -> service().decide(
                REVIEWER,
                REVIEW,
                APPEAL,
                ModerationAppealOperations.AppealDecision.UPHOLD,
                "bad",
                "note"
        ), "APPEAL_INVALID");
    }

    private ModerationAppealService service() {
        return new ModerationAppealService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofDays(7),
                () -> APPEAL
        );
    }

    private static ModerationAppealOperations.AppealView view(
            String status,
            ModerationAppealOperations.AppealDecision decision
    ) {
        return new ModerationAppealOperations.AppealView(
                APPEAL,
                REVIEW,
                ACTOR,
                ORIGINAL,
                "Reason",
                status,
                decision,
                decision == null ? null : "NEW_EVIDENCE",
                decision == null ? null : "Accepted",
                decision == null ? null : REVIEWER,
                NOW,
                NOW.plus(Duration.ofDays(7)),
                decision == null ? null : NOW
        );
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ModerationAppealException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
