package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api.CreateDonationRequest;
import com.storyplatform.monetization.api.DonationController;
import com.storyplatform.monetization.application.DonationException;
import com.storyplatform.monetization.application.DonationOperations;
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

class DonationControllerTest {

    private static final String KEY = "donation-key-0001";
    private final DonationOperations operations =
            mock(DonationOperations.class);
    private final DonationController controller =
            new DonationController(operations);
    private final Jwt jwt = new Jwt(
            "token",
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "reader")
    );

    @Test
    void createsAndReplaysPrivateDonationReceipts() {
        var request = new CreateDonationRequest("team", 500, "Thanks");
        when(operations.donate("reader", KEY, "team", 500, "Thanks"))
                .thenReturn(receipt(false), receipt(true));

        var created = controller.donate(jwt, KEY, request);
        var replayed = controller.donate(jwt, KEY, request);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation())
                .hasToString("/api/v1/donations/"
                        + receipt(false).donationId());
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(replayed.getHeaders().getCacheControl())
                .contains("no-store");
    }

    @Test
    void mapsEveryBusinessFailureToStableHttpSemantics() {
        assertFailure(
                DonationException.Kind.INVALID,
                HttpStatus.BAD_REQUEST
        );
        assertFailure(
                DonationException.Kind.TEAM_NOT_FOUND,
                HttpStatus.NOT_FOUND
        );
        assertFailure(
                DonationException.Kind.INSUFFICIENT_BALANCE,
                HttpStatus.UNPROCESSABLE_CONTENT
        );
        assertFailure(
                DonationException.Kind.CONFLICT,
                HttpStatus.CONFLICT
        );
    }

    private void assertFailure(
            DonationException.Kind kind,
            HttpStatus status
    ) {
        var request = new CreateDonationRequest("team", 500, null);
        doThrow(new DonationException("rejected", kind))
                .when(operations)
                .donate("reader", KEY, "team", 500, null);

        assertThatThrownBy(() -> controller.donate(jwt, KEY, request))
                .isInstanceOf(ApiException.class)
                .extracting("status", "code")
                .containsExactly(status, "DONATION_" + kind);
    }

    private static DonationOperations.Receipt receipt(boolean replayed) {
        return new DonationOperations.Receipt(
                "10000000-0000-4000-8000-000000000001",
                "team",
                500,
                "Thanks",
                "20000000-0000-4000-8000-000000000001",
                "POSTED",
                replayed,
                Instant.EPOCH
        );
    }
}
