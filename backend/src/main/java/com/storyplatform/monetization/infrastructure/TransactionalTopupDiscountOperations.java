package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.TopupDiscountOperations;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

public class TransactionalTopupDiscountOperations
        implements TopupDiscountOperations {

    private final TopupDiscountOperations delegate;

    public TransactionalTopupDiscountOperations(
            TopupDiscountOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscountView current() {
        return delegate.current();
    }

    @Override
    @Transactional
    public DiscountView update(
            String actorId,
            String reauthenticationToken,
            long expectedVersion,
            BigDecimal discountPercent,
            String reason
    ) {
        return delegate.update(
                actorId,
                reauthenticationToken,
                expectedVersion,
                discountPercent,
                reason
        );
    }
}
