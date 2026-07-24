package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.ReferralOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalReferralOperations implements ReferralOperations {

    private final ReferralOperations delegate;

    public TransactionalReferralOperations(ReferralOperations delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public AttributionView attribute(
            String refereeId,
            String code,
            String idempotencyKey
    ) {
        return delegate.attribute(refereeId, code, idempotencyKey);
    }

    @Override
    @Transactional(readOnly = true)
    public ReferralView mine(String userId) {
        return delegate.mine(userId);
    }

    @Override
    @Transactional
    public int rewardEligible(int limit) {
        return delegate.rewardEligible(limit);
    }
}
