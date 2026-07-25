package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCallbackOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.boot.autoconfigure.condition
        .ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@ConditionalOnProperty(
        name = "app.monetization.withdrawals.payout.enabled",
        havingValue = "true"
)
public final class WithdrawalPayoutCallbackController {

    private final WithdrawalPayoutCallbackOperations callbacks;

    public WithdrawalPayoutCallbackController(
            WithdrawalPayoutCallbackOperations callbacks
    ) {
        this.callbacks = Objects.requireNonNull(callbacks);
    }

    @PostMapping("/webhooks/withdrawal-payouts")
    public ResponseEntity<Void> receive(
            @RequestHeader("X-Payout-Provider") String provider,
            @RequestHeader("X-Payout-Timestamp") String timestamp,
            @RequestHeader("X-Payout-Signature") String signature,
            @RequestBody byte[] rawBody
    ) {
        try {
            callbacks.accept(provider, rawBody, timestamp, signature);
            return ResponseEntity.noContent().build();
        } catch (WithdrawalException exception) {
            HttpStatus status = switch (exception.kind()) {
                case FORBIDDEN -> HttpStatus.UNAUTHORIZED;
                case CONFLICT -> HttpStatus.CONFLICT;
                default -> HttpStatus.BAD_REQUEST;
            };
            throw new ApiException(
                    status,
                    "WITHDRAWAL_PAYOUT_CALLBACK_REJECTED",
                    "Withdrawal payout callback rejected",
                    exception.getMessage()
            );
        }
    }
}
