package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api.PaymentWebhookController;
import com.storyplatform.monetization.application.PaymentWebhookException;
import com.storyplatform.monetization.application.PaymentWebhookOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentWebhookControllerTest {

    private static final byte[] BODY = "{}".getBytes(
            java.nio.charset.StandardCharsets.UTF_8
    );
    private final PaymentWebhookOperations operations =
            mock(PaymentWebhookOperations.class);
    private final PaymentWebhookController controller =
            new PaymentWebhookController(operations);

    @Test
    void acknowledgesAcceptedAndDuplicateEventsWithoutDisclosure() {
        when(operations.accept("bank", BODY, "time", "signature"))
                .thenReturn(PaymentWebhookOperations.Result.ACCEPTED);

        assertThat(controller.receive(
                "bank", "time", "signature", BODY
        ).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void distinguishesAuthenticationFromPayloadFailures() {
        when(operations.accept("bank", BODY, "time", "signature"))
                .thenThrow(new PaymentWebhookException(
                        "signature",
                        PaymentWebhookException.Kind.UNAUTHORIZED
                ))
                .thenThrow(new PaymentWebhookException(
                        "payload",
                        PaymentWebhookException.Kind.INVALID
                ));
        assertThatThrownBy(() -> controller.receive(
                "bank", "time", "signature", BODY
        )).isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> controller.receive(
                "bank", "time", "signature", BODY
        )).isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
