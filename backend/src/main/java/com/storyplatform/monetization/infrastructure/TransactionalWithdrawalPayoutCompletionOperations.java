package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application
        .WithdrawalPayoutClaimOperations;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCompletionOperations;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalWithdrawalPayoutCompletionOperations
        implements WithdrawalPayoutCompletionOperations {

    private final WithdrawalPayoutCompletionOperations delegate;

    public TransactionalWithdrawalPayoutCompletionOperations(
            WithdrawalPayoutCompletionOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public void complete(
            WithdrawalPayoutClaimOperations.Claim claim,
            WithdrawalPayoutGateway.Result result
    ) {
        delegate.complete(claim, result);
    }

    @Override
    @Transactional
    public void retry(
            WithdrawalPayoutClaimOperations.Claim claim,
            String errorCode
    ) {
        delegate.retry(claim, errorCode);
    }
}
