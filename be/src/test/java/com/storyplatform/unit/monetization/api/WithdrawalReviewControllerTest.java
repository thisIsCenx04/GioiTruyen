package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api.ReviewWithdrawalRequest;
import com.storyplatform.monetization.api.WithdrawalReviewController;
import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application
        .WithdrawalReviewOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WithdrawalReviewControllerTest {

    private static final String REVIEWER =
            "10000000-0000-4000-8000-000000000001";
    private static final String WITHDRAWAL =
            "20000000-0000-4000-8000-000000000001";
    private static final String KEY = "withdrawal-review-key-0001";
    private static final String REASON =
            "Verified finance review evidence.";
    private final WithdrawalReviewOperations reviews =
            mock(WithdrawalReviewOperations.class);
    private final JwtPrivilegeEvaluator privileges =
            mock(JwtPrivilegeEvaluator.class);
    private final WithdrawalReviewController controller =
            new WithdrawalReviewController(reviews, privileges);
    private final Jwt jwt = new Jwt(
            "token",
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", REVIEWER)
    );

    @Test
    void approvesAndRejectsAsPrivateFinanceActions() {
        when(privileges.allows(
                jwt,
                PrivilegedCapability.FINANCE_REVIEW
        )).thenReturn(true);
        when(reviews.approve(
                REVIEWER, WITHDRAWAL, "grant", KEY, REASON
        )).thenReturn(decision("APPROVED", null));
        when(reviews.reject(
                REVIEWER, WITHDRAWAL, "grant", KEY, REASON
        )).thenReturn(decision(
                "REJECTED",
                "30000000-0000-4000-8000-000000000001"
        ));
        var request = new ReviewWithdrawalRequest(REASON);

        var approved = controller.approve(
                jwt, WITHDRAWAL, "grant", KEY, request
        );
        var rejected = controller.reject(
                jwt, WITHDRAWAL, "grant", KEY, request
        );

        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getHeaders().getCacheControl())
                .contains("no-store");
    }

    @Test
    void deniesMissingPrivilegeBeforeBusinessOperation() {
        var request = new ReviewWithdrawalRequest(REASON);

        assertThatThrownBy(() -> controller.approve(
                jwt, WITHDRAWAL, "grant", KEY, request
        )).isInstanceOf(ApiException.class)
                .extracting("status", "code")
                .containsExactly(
                        HttpStatus.FORBIDDEN,
                        "WITHDRAWAL_FORBIDDEN"
                );
        verify(reviews, never()).approve(
                REVIEWER, WITHDRAWAL, "grant", KEY, REASON
        );
    }

    @Test
    void mapsReviewBusinessFailures() {
        when(privileges.allows(
                jwt,
                PrivilegedCapability.FINANCE_REVIEW
        )).thenReturn(true);
        assertFailure(
                WithdrawalException.Kind.INVALID,
                HttpStatus.BAD_REQUEST
        );
        assertFailure(
                WithdrawalException.Kind.NOT_FOUND,
                HttpStatus.NOT_FOUND
        );
        assertFailure(
                WithdrawalException.Kind.CONFLICT,
                HttpStatus.CONFLICT
        );
    }

    private void assertFailure(
            WithdrawalException.Kind kind,
            HttpStatus status
    ) {
        doThrow(new WithdrawalException("rejected", kind))
                .when(reviews)
                .approve(REVIEWER, WITHDRAWAL, "grant", KEY, REASON);

        assertThatThrownBy(() -> controller.approve(
                jwt,
                WITHDRAWAL,
                "grant",
                KEY,
                new ReviewWithdrawalRequest(REASON)
        )).isInstanceOf(ApiException.class)
                .extracting("status", "code")
                .containsExactly(status, "WITHDRAWAL_" + kind);
    }

    private static WithdrawalReviewOperations.Decision decision(
            String state,
            String release
    ) {
        return new WithdrawalReviewOperations.Decision(
                WITHDRAWAL,
                state,
                REVIEWER,
                REASON,
                "STANDARD",
                "withdrawal-risk-2026.1",
                release,
                false,
                Instant.EPOCH
        );
    }
}
