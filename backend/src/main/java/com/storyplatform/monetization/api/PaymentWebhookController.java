package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.PaymentWebhookException;
import com.storyplatform.monetization.application.PaymentWebhookOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@ConditionalOnProperty(
        name = "app.monetization.topup.webhook-enabled",
        havingValue = "true"
)
public final class PaymentWebhookController {

    private final PaymentWebhookOperations webhooks;

    public PaymentWebhookController(PaymentWebhookOperations webhooks) {
        this.webhooks = Objects.requireNonNull(webhooks);
    }

    @PostMapping("/webhooks/payments")
    public ResponseEntity<Void> receive(
            @RequestHeader("X-Payment-Provider") String provider,
            @RequestHeader("X-Payment-Timestamp") String timestamp,
            @RequestHeader("X-Payment-Signature") String signature,
            @RequestBody byte[] rawBody
    ) {
        try {
            webhooks.accept(provider, rawBody, timestamp, signature);
            return ResponseEntity.noContent().build();
        } catch (PaymentWebhookException exception) {
            HttpStatus status = exception.kind()
                    == PaymentWebhookException.Kind.UNAUTHORIZED
                    ? HttpStatus.UNAUTHORIZED
                    : HttpStatus.BAD_REQUEST;
            throw new ApiException(
                    status,
                    "PAYMENT_WEBHOOK_REJECTED",
                    "Payment webhook rejected",
                    exception.getMessage()
            );
        }
    }
}
