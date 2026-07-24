package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api.CreateReferralAttributionRequest;
import com.storyplatform.monetization.api.ReferralController;
import com.storyplatform.monetization.application.ReferralException;
import com.storyplatform.monetization.application.ReferralOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReferralControllerTest {

    private static final String USER =
            "10000000-0000-4000-8000-000000000001";
    private static final String KEY = "referral-key-0001";
    private final ReferralOperations operations =
            mock(ReferralOperations.class);
    private final ReferralController controller =
            new ReferralController(operations);
    private final Jwt jwt = new Jwt(
            "token",
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", USER)
    );

    @Test
    void createsReplaysAndReadsPrivateReferralState() {
        String code = "a".repeat(43);
        var request = new CreateReferralAttributionRequest(code);
        when(operations.attribute(USER, code, KEY))
                .thenReturn(attribution(false), attribution(true));
        when(operations.mine(USER)).thenReturn(new ReferralOperations
                .ReferralView(code, null, null, 0, 0, 0, 0));

        var created = controller.attribute(jwt, KEY, request);
        var replayed = controller.attribute(jwt, KEY, request);
        var mine = controller.mine(jwt);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation())
                .hasToString("/api/v1/referrals/me");
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(mine.getHeaders().getCacheControl())
                .contains("no-store");
    }

    @Test
    void mapsEveryReferralFailureToStableHttpStatus() {
        assertFailure(ReferralException.Kind.INVALID, HttpStatus.BAD_REQUEST);
        assertFailure(ReferralException.Kind.NOT_FOUND, HttpStatus.NOT_FOUND);
        assertFailure(ReferralException.Kind.CONFLICT, HttpStatus.CONFLICT);
        assertFailure(
                ReferralException.Kind.RATE_LIMITED,
                HttpStatus.TOO_MANY_REQUESTS
        );
    }

    private void assertFailure(
            ReferralException.Kind kind,
            HttpStatus status
    ) {
        var request = new CreateReferralAttributionRequest("a".repeat(43));
        doThrow(new ReferralException("rejected", kind))
                .when(operations)
                .attribute(USER, request.code(), KEY);

        assertThatThrownBy(() ->
                controller.attribute(jwt, KEY, request)
        ).isInstanceOf(ApiException.class)
                .extracting("status", "code")
                .containsExactly(status, "REFERRAL_" + kind);
    }

    private static ReferralOperations.AttributionView attribution(
            boolean replayed
    ) {
        return new ReferralOperations.AttributionView(
                "20000000-0000-4000-8000-000000000001",
                "PENDING",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(60),
                replayed
        );
    }
}
