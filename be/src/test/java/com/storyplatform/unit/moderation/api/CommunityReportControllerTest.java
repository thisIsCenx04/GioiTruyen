package com.storyplatform.unit.moderation.api;

import com.storyplatform.moderation.api.CommunityReportController;
import com.storyplatform.moderation.application
        .CommunityReportException;
import com.storyplatform.moderation.application
        .CommunityReportOperations;
import com.storyplatform.moderation.domain.CommunityReport;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityReportControllerTest {

    private static final String TARGET =
            "30000000-0000-4000-8000-000000000001";

    @Test
    void mapsAllowlistedRequestToAuthenticatedReporter() {
        CommunityReportOperations operations =
                mock(CommunityReportOperations.class);
        when(operations.create(eq("reporter"), any())).thenReturn(
                new CommunityReportOperations.ReportView(
                        "80000000-0000-4000-8000-000000000001",
                        CommunityReport.TargetType.COMMENT,
                        TARGET,
                        CommunityReport.Reason.SPAM,
                        CommunityReport.Status.RECEIVED,
                        45,
                        false,
                        Instant.EPOCH
                )
        );
        CommunityReportController controller =
                new CommunityReportController(operations);

        controller.create(jwt(), new CommunityReportController
                .CreateReportRequest(
                        "comment",
                        TARGET,
                        "spam",
                        "detail",
                        List.of(
                                "70000000-0000-4000-8000-000000000001"
                        )
                ));

        verify(operations).create(eq("reporter"), any());
    }

    @Test
    void rejectsUnknownEnumsAndMalformedIdentifiers() {
        CommunityReportController controller =
                new CommunityReportController(
                        mock(CommunityReportOperations.class)
                );
        assertThatThrownBy(() -> controller.create(
                jwt(),
                new CommunityReportController.CreateReportRequest(
                        "unknown", TARGET, "spam", null, List.of()
                )
        )).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.create(
                jwt(),
                new CommunityReportController.CreateReportRequest(
                        "comment", TARGET, "unknown", null, List.of()
                )
        )).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.create(
                jwt(),
                new CommunityReportController.CreateReportRequest(
                        "comment", "bad", "spam", null, List.of()
                )
        )).isInstanceOf(ApiException.class);
    }

    @Test
    void mapsRateAndAvailabilityFailuresToStableProblems() {
        CommunityReportOperations operations =
                mock(CommunityReportOperations.class);
        when(operations.create(eq("reporter"), any()))
                .thenThrow(new CommunityReportException(
                        "REPORT_RATE_LIMITED",
                        "limited",
                        CommunityReportException.Kind.RATE_LIMITED,
                        60
                ));
        CommunityReportController controller =
                new CommunityReportController(operations);

        assertThatThrownBy(() -> controller.create(
                jwt(),
                new CommunityReportController.CreateReportRequest(
                        "comment", TARGET, "spam", null, List.of()
                )
        )).isInstanceOfSatisfying(
                ApiException.class,
                error -> {
                    org.assertj.core.api.Assertions.assertThat(
                            error.status().value()
                    ).isEqualTo(429);
                    org.assertj.core.api.Assertions.assertThat(
                            error.retryAfter()
                    ).isEqualTo(java.time.Duration.ofSeconds(60));
                }
        );
    }

    private static Jwt jwt() {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("reporter")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
