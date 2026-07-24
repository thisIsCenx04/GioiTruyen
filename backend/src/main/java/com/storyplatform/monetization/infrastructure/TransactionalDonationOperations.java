package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.DonationOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalDonationOperations implements DonationOperations {

    private final DonationOperations delegate;

    public TransactionalDonationOperations(DonationOperations delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public Receipt donate(
            String donorId,
            String idempotencyKey,
            String teamId,
            long amountXu,
            String message
    ) {
        return delegate.donate(
                donorId,
                idempotencyKey,
                teamId,
                amountXu,
                message
        );
    }
}
