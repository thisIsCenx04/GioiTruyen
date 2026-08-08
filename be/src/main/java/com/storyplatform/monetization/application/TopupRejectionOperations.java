package com.storyplatform.monetization.application;

public interface TopupRejectionOperations {

    Rejection reject(
            String actorId,
            String topupId,
            ReasonCode reasonCode,
            String reason,
            String evidenceReference
    );

    record Rejection(
            String topupId,
            String paymentEventId,
            String status,
            boolean replayed
    ) {
    }

    enum ReasonCode {
        AMOUNT_MISMATCH,
        REFERENCE_UNVERIFIABLE,
        DUPLICATE_PAYMENT,
        FRAUD_SUSPECTED,
        OTHER
    }
}
