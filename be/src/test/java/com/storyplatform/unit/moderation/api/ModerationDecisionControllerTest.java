package com.storyplatform.unit.moderation.api;

import com.storyplatform.moderation.api.ModerationDecisionController;
import com.storyplatform.moderation.api.ModerationDecisionRequest;
import com.storyplatform.moderation.application
        .ModerationDecisionException;
import com.storyplatform.moderation.application
        .ModerationDecisionOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.RoleCapabilityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationDecisionControllerTest {

    private static final String REVIEW =
            "80000000-0000-4000-8000-000000000001";
    private static final String REVIEWER =
            "10000000-0000-4000-8000-000000000001";
    private ModerationDecisionOperations operations;
    private ModerationDecisionController controller;

    @BeforeEach
    void setUp() {
        operations = mock(ModerationDecisionOperations.class);
        controller = new ModerationDecisionController(
                operations,
                new JwtPrivilegeEvaluator(new RoleCapabilityPolicy())
        );
    }

    @Test
    void decidesForModeratorAndReturnsVersionEtag() {
        when(operations.decide(any(), any(), anyLong(), any()))
                .thenReturn(view());
        ModerationDecisionRequest request = request();

        var response = controller.decide(
                jwt("MODERATOR"),
                REVIEW,
                "\"3\"",
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"4\"");
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
        verify(operations).decide(
                REVIEWER,
                REVIEW,
                3,
                new ModerationDecisionOperations.DecisionCommand(
                        request.decision(),
                        request.reasonCode(),
                        request.note(),
                        request.evidenceRefs(),
                        request.policyVersion()
                )
        );
    }

    @Test
    void rejectsReaderAndMalformedVersion() {
        assertThatThrownBy(() -> controller.decide(
                jwt("USER"), REVIEW, "\"3\"", request()
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(403)
        );
        assertThatThrownBy(() -> controller.decide(
                jwt("MODERATOR"), REVIEW, "3", request()
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(400)
        );
    }

    @Test
    void mapsInvalidAndOneDecisionConflict() {
        when(operations.decide(any(), any(), anyLong(), any()))
                .thenThrow(new ModerationDecisionException(
                        "MODERATION_DECISION_INVALID",
                        "invalid",
                        ModerationDecisionException.Kind.INVALID
                ));
        assertThatThrownBy(() -> controller.decide(
                jwt("MODERATOR"), REVIEW, "\"3\"", request()
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(400)
        );

        org.mockito.Mockito.reset(operations);
        when(operations.decide(any(), any(), anyLong(), any()))
                .thenThrow(new ModerationDecisionException(
                        "REVIEW_ALREADY_DECIDED",
                        "conflict",
                        ModerationDecisionException.Kind.CONFLICT
                ));
        assertThatThrownBy(() -> controller.decide(
                jwt("ADMIN"), REVIEW, "\"3\"", request()
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(409)
        );
    }

    private static ModerationDecisionRequest request() {
        return new ModerationDecisionRequest(
                ModerationDecisionOperations.Decision.APPROVE,
                "POLICY_PASSED",
                null,
                List.of("evidence:123"),
                "publishing-2026.1"
        );
    }

    private static ModerationDecisionOperations.DecisionView view() {
        return new ModerationDecisionOperations.DecisionView(
                REVIEW,
                "APPROVED",
                ModerationDecisionOperations.Decision.APPROVE,
                "POLICY_PASSED",
                "publishing-2026.1",
                REVIEWER,
                Instant.parse("2026-07-24T00:00:00Z"),
                4
        );
    }

    private static Jwt jwt(String role) {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(REVIEWER)
                .claim("roles", List.of(role))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
