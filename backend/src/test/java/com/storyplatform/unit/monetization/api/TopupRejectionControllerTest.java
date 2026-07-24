package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api.RejectTopupRequest;
import com.storyplatform.monetization.api.TopupRejectionController;
import com.storyplatform.monetization.application.TopupRejectionOperations;
import com.storyplatform.monetization.application.ManualTopupException;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopupRejectionControllerTest {

    private final TopupRejectionOperations operations =
            mock(TopupRejectionOperations.class);
    private final JwtPrivilegeEvaluator privileges =
            mock(JwtPrivilegeEvaluator.class);
    private final TopupRejectionController controller =
            new TopupRejectionController(operations, privileges);
    private final Jwt jwt = new Jwt(
            "token",
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "admin")
    );

    @Test
    void rejectsReviewForAuthorizedFinanceOperator() {
        String topupId = "20000000-0000-4000-8000-000000000001";
        var request = request();
        var result = new TopupRejectionOperations.Rejection(
                topupId,
                "30000000-0000-4000-8000-000000000001",
                "REJECTED",
                false
        );
        when(privileges.allows(
                jwt,
                PrivilegedCapability.FINANCE_REVIEW
        )).thenReturn(true);
        when(operations.reject(
                "admin",
                topupId,
                request.reasonCode(),
                request.reason(),
                request.evidenceReference()
        )).thenReturn(result);

        var response = controller.reject(jwt, topupId, request);

        assertThat(response.getBody()).isEqualTo(result);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
    }

    @Test
    void requiresFinanceReviewCapability() {
        assertThatThrownBy(() -> controller.reject(
                jwt,
                "20000000-0000-4000-8000-000000000001",
                request()
        )).isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void mapsInvalidMissingAndConcurrentReviews() {
        String topupId = "20000000-0000-4000-8000-000000000001";
        var request = request();
        when(privileges.allows(
                jwt,
                PrivilegedCapability.FINANCE_REVIEW
        )).thenReturn(true);
        when(operations.reject(
                "admin",
                topupId,
                request.reasonCode(),
                request.reason(),
                request.evidenceReference()
        )).thenThrow(
                new ManualTopupException(
                        "invalid",
                        ManualTopupException.Kind.INVALID
                ),
                new ManualTopupException(
                        "missing",
                        ManualTopupException.Kind.NOT_FOUND
                ),
                new ManualTopupException(
                        "race",
                        ManualTopupException.Kind.CONFLICT
                )
        );

        for (var status : List.of(
                HttpStatus.BAD_REQUEST,
                HttpStatus.NOT_FOUND,
                HttpStatus.CONFLICT
        )) {
            assertThatThrownBy(() ->
                    controller.reject(jwt, topupId, request)
            ).isInstanceOf(ApiException.class)
                    .extracting("status")
                    .isEqualTo(status);
        }
    }

    private static RejectTopupRequest request() {
        return new RejectTopupRequest(
                TopupRejectionOperations.ReasonCode.AMOUNT_MISMATCH,
                "Settlement evidence verified",
                "evidence/bank-statement-1"
        );
    }
}
