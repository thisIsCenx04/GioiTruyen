package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api.CreateWithdrawalRequest;
import com.storyplatform.monetization.api.WithdrawalController;
import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application.WithdrawalOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WithdrawalControllerTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String DESTINATION =
            "30000000-0000-4000-8000-000000000001";
    private static final String KEY = "withdrawal-key-0001";
    private final WithdrawalOperations operations =
            mock(WithdrawalOperations.class);
    private final WithdrawalController controller =
            new WithdrawalController(operations);
    private final Jwt jwt = new Jwt(
            "token",
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", ACTOR)
    );

    @Test
    void createsReplaysAndListsPrivateWithdrawals() {
        var request = new CreateWithdrawalRequest(100_000, DESTINATION);
        when(operations.create(
                ACTOR, TEAM, KEY, 100_000, DESTINATION
        )).thenReturn(receipt(false), receipt(true));
        when(operations.list(ACTOR, TEAM, null, 20)).thenReturn(
                new WithdrawalOperations.Page(
                        List.of(receipt(false)),
                        null
                )
        );

        var created = controller.create(jwt, TEAM, KEY, request);
        var replayed = controller.create(jwt, TEAM, KEY, request);
        var page = controller.list(jwt, TEAM, null, 20);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(page.getHeaders().getCacheControl())
                .contains("no-store");
    }

    @Test
    void mapsEveryWithdrawalFailureToStableHttpStatus() {
        assertFailure(WithdrawalException.Kind.INVALID, HttpStatus.BAD_REQUEST);
        assertFailure(WithdrawalException.Kind.FORBIDDEN, HttpStatus.FORBIDDEN);
        assertFailure(
                WithdrawalException.Kind.DESTINATION_UNAVAILABLE,
                HttpStatus.UNPROCESSABLE_CONTENT
        );
        assertFailure(
                WithdrawalException.Kind.INSUFFICIENT_BALANCE,
                HttpStatus.UNPROCESSABLE_CONTENT
        );
        assertFailure(WithdrawalException.Kind.CONFLICT, HttpStatus.CONFLICT);
    }

    private void assertFailure(
            WithdrawalException.Kind kind,
            HttpStatus status
    ) {
        doThrow(new WithdrawalException("rejected", kind))
                .when(operations)
                .create(ACTOR, TEAM, KEY, 100_000, DESTINATION);
        var request = new CreateWithdrawalRequest(100_000, DESTINATION);

        assertThatThrownBy(() ->
                controller.create(jwt, TEAM, KEY, request)
        ).isInstanceOf(ApiException.class)
                .extracting("status", "code")
                .containsExactly(status, "WITHDRAWAL_" + kind);
    }

    private static WithdrawalOperations.Receipt receipt(
            boolean replayed
    ) {
        return new WithdrawalOperations.Receipt(
                "40000000-0000-4000-8000-000000000001",
                TEAM,
                100_000,
                "VCB •••• 1234",
                "PENDING_REVIEW",
                replayed,
                Instant.EPOCH
        );
    }
}
