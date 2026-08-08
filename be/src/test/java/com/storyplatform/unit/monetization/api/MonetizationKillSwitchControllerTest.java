package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api
        .MonetizationKillSwitchController;
import com.storyplatform.monetization.api
        .UpdateMonetizationKillSwitchRequest;
import com.storyplatform.monetization.application
        .MonetizationKillSwitchException;
import com.storyplatform.monetization.application
        .MonetizationKillSwitchOperations;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonetizationKillSwitchControllerTest {

    private static final String ADMIN =
            "10000000-0000-4000-8000-000000000001";
    private final MonetizationKillSwitchOperations operations =
            mock(MonetizationKillSwitchOperations.class);
    private final JwtPrivilegeEvaluator privileges =
            mock(JwtPrivilegeEvaluator.class);
    private final Jwt jwt = new Jwt(
            "token",
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", ADMIN, "roles", List.of("ADMIN"))
    );
    private final MonetizationKillSwitchController controller =
            new MonetizationKillSwitchController(operations, privileges);

    @Test
    void readsAndUpdatesPrivateVersionedSwitch() {
        var state = state();
        when(privileges.allows(
                jwt,
                PrivilegedCapability.SYSTEM_CONFIGURE
        )).thenReturn(true);
        when(operations.current()).thenReturn(List.of(state));
        when(operations.update(
                ADMIN,
                "grant",
                MonetizationKillSwitch.Operation.WITHDRAWAL_PAYOUT,
                0,
                true,
                "Provider incident containment."
        )).thenReturn(state);

        assertThat(controller.current(jwt).getBody())
                .containsExactly(state);
        var response = controller.update(
                jwt,
                "withdrawal-payout",
                "\"0\"",
                "grant",
                new UpdateMonetizationKillSwitchRequest(
                        true,
                        "Provider incident containment."
                )
        );
        assertThat(response.getBody()).isEqualTo(state);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"1\"");
    }

    @Test
    void rejectsPrivilegeOperationVersionAndConflict() {
        assertCode(
                () -> controller.current(jwt),
                HttpStatusValue.FORBIDDEN
        );
        when(privileges.allows(
                jwt,
                PrivilegedCapability.SYSTEM_CONFIGURE
        )).thenReturn(true);
        assertCode(() -> controller.update(
                jwt,
                "unknown",
                "\"0\"",
                "grant",
                request()
        ), HttpStatusValue.BAD_REQUEST);
        assertCode(() -> controller.update(
                jwt,
                "withdrawal-payout",
                "0",
                "grant",
                request()
        ), HttpStatusValue.BAD_REQUEST);
        when(operations.update(
                ADMIN,
                "grant",
                MonetizationKillSwitch.Operation.WITHDRAWAL_PAYOUT,
                0,
                true,
                "Provider incident containment."
        )).thenThrow(new MonetizationKillSwitchException(
                "stale",
                MonetizationKillSwitchException.Kind.CONFLICT
        ));
        assertCode(() -> controller.update(
                jwt,
                "withdrawal-payout",
                "\"0\"",
                "grant",
                request()
        ), HttpStatusValue.CONFLICT);
    }

    private static UpdateMonetizationKillSwitchRequest request() {
        return new UpdateMonetizationKillSwitchRequest(
                true,
                "Provider incident containment."
        );
    }

    private static MonetizationKillSwitch state() {
        return new MonetizationKillSwitch(
                MonetizationKillSwitch.Operation.WITHDRAWAL_PAYOUT,
                true,
                1,
                ADMIN,
                Instant.parse("2026-07-25T01:00:00Z")
        );
    }

    private static void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            HttpStatusValue status
    ) {
        assertThatThrownBy(action)
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .satisfies(value -> assertThat(
                        ((org.springframework.http.HttpStatus) value).value()
                ).isEqualTo(status.value));
    }

    private enum HttpStatusValue {
        BAD_REQUEST(400),
        FORBIDDEN(403),
        CONFLICT(409);

        private final int value;

        HttpStatusValue(int value) {
            this.value = value;
        }
    }
}
