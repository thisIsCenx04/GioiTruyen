package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.RewardOperations;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class TransactionalRewardOperations
        implements RewardOperations {

    private final RewardOperations delegate;

    public TransactionalRewardOperations(RewardOperations delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public PeriodView settle(LocalDate periodDate) {
        return delegate.settle(periodDate);
    }

    @Override
    @Transactional
    public AdjustmentView adjust(
            String settlementId,
            long correctedValidViews,
            String reasonCode,
            String idempotencyKey
    ) {
        return delegate.adjust(
                settlementId,
                correctedValidViews,
                reasonCode,
                idempotencyKey
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettlementView> recent(
            String actorId,
            String teamId,
            int limit
    ) {
        return delegate.recent(actorId, teamId, limit);
    }
}
