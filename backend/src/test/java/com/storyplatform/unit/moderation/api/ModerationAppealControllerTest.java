package com.storyplatform.unit.moderation.api;

import com.storyplatform.moderation.api.ModerationAppealController;
import com.storyplatform.moderation.application.ModerationAppealException;
import com.storyplatform.moderation.application.ModerationAppealOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.RoleCapabilityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationAppealControllerTest {

    private static final String REVIEW =
            "80000000-0000-4000-8000-000000000001";
    private static final String APPEAL =
            "80000000-0000-4000-8000-000000000002";
    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private ModerationAppealOperations operations;
    private ModerationAppealController controller;

    @BeforeEach
    void setUp() {
        operations = mock(ModerationAppealOperations.class);
        controller = new ModerationAppealController(
                operations,
                new JwtPrivilegeEvaluator(new RoleCapabilityPolicy())
        );
    }

    @Test
    void createsAppealAndModeratorMakesFinalDecision() {
        when(operations.create(any(), any(), any())).thenReturn(view());
        var created = controller.create(
                jwt("USER"),
                REVIEW,
                new ModerationAppealController.CreateAppealRequest("Reason")
        );
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getHeaders().getLocation().toString())
                .endsWith("/appeals/" + APPEAL);

        when(operations.decide(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(view());
        var decided = controller.decide(
                jwt("MODERATOR"),
                REVIEW,
                APPEAL,
                new ModerationAppealController.AppealDecisionRequest(
                        ModerationAppealOperations.AppealDecision.UPHOLD,
                        "POLICY_CONFIRMED",
                        "Final"
                )
        );
        assertThat(decided.getStatusCode().value()).isEqualTo(200);
        verify(operations).decide(
                ACTOR,
                REVIEW,
                APPEAL,
                ModerationAppealOperations.AppealDecision.UPHOLD,
                "POLICY_CONFIRMED",
                "Final"
        );
    }

    @Test
    void blocksUnprivilegedReviewerAndMapsFailures() {
        assertThatThrownBy(() -> controller.decide(
                jwt("USER"),
                REVIEW,
                APPEAL,
                new ModerationAppealController.AppealDecisionRequest(
                        ModerationAppealOperations.AppealDecision.UPHOLD,
                        "POLICY_CONFIRMED",
                        "Final"
                )
        )).isInstanceOfSatisfying(ApiException.class, error ->
                assertThat(error.status().value()).isEqualTo(403)
        );

        for (var kind : ModerationAppealException.Kind.values()) {
            org.mockito.Mockito.reset(operations);
            when(operations.create(any(), any(), any())).thenThrow(
                    new ModerationAppealException("FAIL", "failure", kind)
            );
            assertThatThrownBy(() -> controller.create(
                    jwt("USER"),
                    REVIEW,
                    new ModerationAppealController.CreateAppealRequest(
                            "Reason"
                    )
            )).isInstanceOf(ApiException.class);
        }
    }

    private static ModerationAppealOperations.AppealView view() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new ModerationAppealOperations.AppealView(
                APPEAL,
                REVIEW,
                ACTOR,
                "10000000-0000-4000-8000-000000000002",
                "Reason",
                "PENDING",
                null,
                null,
                null,
                null,
                now,
                now.plusSeconds(604800),
                null
        );
    }

    private static Jwt jwt(String role) {
        Instant now = Instant.now();
        return new Jwt(
                "token",
                now,
                now.plusSeconds(60),
                java.util.Map.of("alg", "none"),
                java.util.Map.of("sub", ACTOR, "roles", java.util.List.of(role))
        );
    }
}
