package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api
        .WithdrawalPayoutCallbackController;
import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCallbackOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class WithdrawalPayoutCallbackControllerTest {

    private final WithdrawalPayoutCallbackOperations operations =
            mock(WithdrawalPayoutCallbackOperations.class);
    private final WithdrawalPayoutCallbackController controller =
            new WithdrawalPayoutCallbackController(operations);

    @Test
    void acknowledgesAuthenticatedCallbackWithoutDisclosure() {
        assertThat(controller.receive(
                "bank-provider",
                "timestamp",
                "signature",
                new byte[]{1}
        ).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void mapsSignatureConflictAndPayloadFailures() {
        assertMapped(WithdrawalException.Kind.FORBIDDEN, 401);
        assertMapped(WithdrawalException.Kind.CONFLICT, 409);
        assertMapped(WithdrawalException.Kind.INVALID, 400);
    }

    private void assertMapped(
            WithdrawalException.Kind kind,
            int expectedStatus
    ) {
        byte[] body = new byte[]{1};
        doThrow(new WithdrawalException("rejected", kind))
                .when(operations)
                .accept("bank-provider", body, "timestamp", "signature");

        assertThatThrownBy(() -> controller.receive(
                "bank-provider",
                "timestamp",
                "signature",
                body
        )).isInstanceOf(ApiException.class)
                .extracting("status")
                .satisfies(status -> assertThat(
                        ((org.springframework.http.HttpStatus) status).value()
                ).isEqualTo(expectedStatus));
    }
}
