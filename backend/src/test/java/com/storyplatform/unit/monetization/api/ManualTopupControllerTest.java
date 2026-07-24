package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api.ApproveManualTopupRequest;
import com.storyplatform.monetization.api.ManualTopupController;
import com.storyplatform.monetization.application.ManualTopupException;
import com.storyplatform.monetization.application.ManualTopupOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManualTopupControllerTest {

    private final ManualTopupOperations operations =
            mock(ManualTopupOperations.class);
    private final JwtPrivilegeEvaluator privileges =
            mock(JwtPrivilegeEvaluator.class);
    private final ManualTopupController controller =
            new ManualTopupController(operations, privileges);
    private final Jwt jwt = new Jwt(
            "token",
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "admin")
    );

    @Test
    void returnsPrivateApprovalForFinanceReviewer() {
        String topupId = "20000000-0000-4000-8000-000000000001";
        var request = new ApproveManualTopupRequest(
                "Verified settlement statement",
                "evidence/bank-statement-1"
        );
        var approval = new ManualTopupOperations.Approval(
                topupId,
                "30000000-0000-4000-8000-000000000001",
                "40000000-0000-4000-8000-000000000001",
                "CREDITED",
                Instant.EPOCH
        );
        when(privileges.allows(
                jwt,
                PrivilegedCapability.FINANCE_REVIEW
        )).thenReturn(true);
        when(operations.approve(
                "admin",
                topupId,
                "grant",
                request.reason(),
                request.evidenceReference()
        )).thenReturn(approval);

        var response = controller.approve(
                jwt, topupId, "grant", request
        );

        assertThat(response.getBody()).isEqualTo(approval);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
    }

    @Test
    void rejectsCallersWithoutFinanceReviewCapability() {
        assertThatThrownBy(() -> controller.approve(
                jwt,
                "20000000-0000-4000-8000-000000000001",
                "grant",
                new ApproveManualTopupRequest(
                        "Verified settlement statement",
                        "evidence/bank-statement-1"
                )
        )).isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void mapsInvalidMissingAndConcurrentBusinessDecisions() {
        String topupId = "20000000-0000-4000-8000-000000000001";
        var request = new ApproveManualTopupRequest(
                "Verified settlement statement",
                "evidence/bank-statement-1"
        );
        when(privileges.allows(
                jwt,
                PrivilegedCapability.FINANCE_REVIEW
        )).thenReturn(true);
        when(operations.approve(
                "admin",
                topupId,
                "grant",
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
        var expected = List.of(
                HttpStatus.BAD_REQUEST,
                HttpStatus.NOT_FOUND,
                HttpStatus.CONFLICT
        );

        for (var status : expected) {
            assertThatThrownBy(() -> controller.approve(
                    jwt, topupId, "grant", request
            )).isInstanceOf(ApiException.class)
                    .extracting("status")
                    .isEqualTo(status);
        }
    }
}
