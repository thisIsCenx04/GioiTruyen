package com.storyplatform.unit.moderation.application;

import com.storyplatform.moderation.application
        .ModerationDecisionException;
import com.storyplatform.moderation.application
        .ModerationDecisionOperations;
import com.storyplatform.moderation.application.ModerationDecisionService;
import com.storyplatform.moderation.application.port
        .ModerationDecisionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationDecisionServiceTest {

    private static final String REVIEW =
            "80000000-0000-4000-8000-000000000001";
    private static final String REVIEWER =
            "10000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final ModerationDecisionRepository repository =
            mock(ModerationDecisionRepository.class);

    @Test
    void recordsNormalizedVersionedDecision() {
        when(repository.decide(any(), any(), anyLong(), any(), any()))
                .thenReturn(new ModerationDecisionRepository.Result(
                        ModerationDecisionRepository.Outcome.SUCCESS,
                        "APPROVED",
                        4
                ));
        var command = new ModerationDecisionOperations.DecisionCommand(
                ModerationDecisionOperations.Decision.APPROVE,
                "POLICY_PASSED",
                "  Reviewed  ",
                List.of("evidence:123"),
                "publishing-2026.1"
        );

        var result = service().decide(REVIEWER, REVIEW, 3, command);

        assertThat(result.state()).isEqualTo("APPROVED");
        assertThat(result.version()).isEqualTo(4);
        verify(repository).decide(
                REVIEW,
                REVIEWER,
                3,
                new ModerationDecisionRepository.DecisionRecord(
                        command.decision(),
                        command.reasonCode(),
                        "Reviewed",
                        command.evidenceRefs(),
                        command.policyVersion()
                ),
                NOW
        );
    }

    @Test
    void requestChangesAndRejectRequireExplanatoryNote() {
        for (var decision : List.of(
                ModerationDecisionOperations.Decision.REQUEST_CHANGES,
                ModerationDecisionOperations.Decision.REJECT
        )) {
            assertThatThrownBy(() -> service().decide(
                    REVIEWER,
                    REVIEW,
                    3,
                    new ModerationDecisionOperations.DecisionCommand(
                            decision,
                            "CONTENT_POLICY",
                            " ",
                            List.of(),
                            "publishing-2026.1"
                    )
            )).isInstanceOf(ModerationDecisionException.class)
                    .extracting("code")
                    .isEqualTo("MODERATION_DECISION_INVALID");
        }
    }

    @Test
    void mapsOneDecisionAndStaleTargetConflicts() {
        when(repository.decide(any(), any(), anyLong(), any(), any()))
                .thenReturn(new ModerationDecisionRepository.Result(
                        ModerationDecisionRepository.Outcome.CONFLICT,
                        null,
                        3
                ));
        assertThatThrownBy(() -> service().decide(
                REVIEWER, REVIEW, 3, approve()
        )).isInstanceOf(ModerationDecisionException.class)
                .extracting("code")
                .isEqualTo("REVIEW_ALREADY_DECIDED");

        when(repository.decide(any(), any(), anyLong(), any(), any()))
                .thenReturn(new ModerationDecisionRepository.Result(
                        ModerationDecisionRepository.Outcome.STALE_TARGET,
                        "APPROVED",
                        4
                ));
        assertThatThrownBy(() -> service().decide(
                REVIEWER, REVIEW, 3, approve()
        )).isInstanceOf(ModerationDecisionException.class)
                .extracting("code")
                .isEqualTo("REVIEW_TARGET_STALE");
    }

    @Test
    void rejectsMalformedIdentifiersVersionPolicyAndEvidence() {
        assertThatThrownBy(() -> service().decide(
                "invalid", REVIEW, 3, approve()
        )).isInstanceOf(ModerationDecisionException.class);
        assertThatThrownBy(() -> service().decide(
                REVIEWER, REVIEW, 0, approve()
        )).isInstanceOf(ModerationDecisionException.class);
        assertThatThrownBy(() -> service().decide(
                REVIEWER, "invalid", 3, approve()
        )).isInstanceOf(ModerationDecisionException.class);
        assertThatThrownBy(() -> service().decide(
                REVIEWER,
                REVIEW,
                3,
                new ModerationDecisionOperations.DecisionCommand(
                        ModerationDecisionOperations.Decision.APPROVE,
                        "bad",
                        null,
                        List.of(),
                        "Bad Policy"
                )
        )).isInstanceOf(ModerationDecisionException.class);
        assertThatThrownBy(() -> service().decide(
                REVIEWER,
                REVIEW,
                3,
                new ModerationDecisionOperations.DecisionCommand(
                        ModerationDecisionOperations.Decision.APPROVE,
                        "POLICY_PASSED",
                        null,
                        List.of(),
                        null
                )
        )).isInstanceOf(ModerationDecisionException.class);
        assertThatThrownBy(() -> service().decide(
                REVIEWER,
                REVIEW,
                3,
                new ModerationDecisionOperations.DecisionCommand(
                        ModerationDecisionOperations.Decision.APPROVE,
                        "POLICY_PASSED",
                        "x".repeat(2001),
                        List.of(),
                        "publishing-2026.1"
                )
        )).isInstanceOf(ModerationDecisionException.class);
        assertThatThrownBy(() -> service().decide(
                REVIEWER,
                REVIEW,
                3,
                new ModerationDecisionOperations.DecisionCommand(
                        ModerationDecisionOperations.Decision.APPROVE,
                        "POLICY_PASSED",
                        null,
                        List.of("$invalid"),
                        "publishing-2026.1"
                )
        )).isInstanceOf(ModerationDecisionException.class);
        assertThatThrownBy(() -> service().decide(
                REVIEWER,
                REVIEW,
                3,
                new ModerationDecisionOperations.DecisionCommand(
                        ModerationDecisionOperations.Decision.APPROVE,
                        "POLICY_PASSED",
                        null,
                        List.of("duplicate", "duplicate"),
                        "publishing-2026.1"
                )
        )).isInstanceOf(ModerationDecisionException.class);
    }

    private ModerationDecisionService service() {
        return new ModerationDecisionService(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static ModerationDecisionOperations.DecisionCommand approve() {
        return new ModerationDecisionOperations.DecisionCommand(
                ModerationDecisionOperations.Decision.APPROVE,
                "POLICY_PASSED",
                null,
                List.of(),
                "publishing-2026.1"
        );
    }
}
