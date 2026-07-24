package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.WalletOperations;
import com.storyplatform.monetization.domain.WalletAccount;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalWalletOperations implements WalletOperations {

    private final WalletOperations delegate;

    public TransactionalWalletOperations(WalletOperations delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletBalance balance(String userId) {
        return delegate.balance(userId);
    }

    @Override
    @Transactional
    public WalletBalance open(
            WalletAccount.OwnerType ownerType,
            String ownerId
    ) {
        return delegate.open(ownerType, ownerId);
    }
}
