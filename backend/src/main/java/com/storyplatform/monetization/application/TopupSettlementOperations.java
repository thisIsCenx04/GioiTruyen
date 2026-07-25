package com.storyplatform.monetization.application;

public interface TopupSettlementOperations {

    Result settle(String provider, String providerEventId);

    enum Result {
        CREDITED,
        PENDING_REVIEW,
        SUSPENDED,
        REPLAYED
    }
}
