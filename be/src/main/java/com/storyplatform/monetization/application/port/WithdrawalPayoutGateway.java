package com.storyplatform.monetization.application.port;

public interface WithdrawalPayoutGateway {

    Result submit(Command command);

    record Command(
            String withdrawalId,
            String idempotencyKey,
            long amountVnd,
            String encryptedDestination
    ) {
    }

    record Result(
            Status status,
            String providerReference,
            String errorCode
    ) {
    }

    enum Status {
        PENDING,
        PAID,
        FAILED
    }
}
