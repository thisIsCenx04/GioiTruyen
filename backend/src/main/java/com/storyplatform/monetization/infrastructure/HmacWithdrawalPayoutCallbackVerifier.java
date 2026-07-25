package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackVerifier;

import java.time.Clock;
import java.time.Duration;

public final class HmacWithdrawalPayoutCallbackVerifier
        implements WithdrawalPayoutCallbackVerifier {

    private final HmacPaymentWebhookVerifier delegate;

    public HmacWithdrawalPayoutCallbackVerifier(
            String provider,
            String secret,
            Duration maximumAge,
            Clock clock
    ) {
        delegate = new HmacPaymentWebhookVerifier(
                provider,
                secret,
                maximumAge,
                clock
        );
    }

    @Override
    public boolean verify(
            String provider,
            byte[] rawBody,
            String timestamp,
            String signature
    ) {
        return delegate.verify(
                provider,
                rawBody,
                timestamp,
                signature
        );
    }
}
