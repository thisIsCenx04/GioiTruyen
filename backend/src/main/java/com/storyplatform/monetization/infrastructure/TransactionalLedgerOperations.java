package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.LedgerOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalLedgerOperations
        implements LedgerOperations {

    private final LedgerOperations delegate;

    public TransactionalLedgerOperations(LedgerOperations delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    @Transactional
    public Posting post(Command command) {
        return delegate.post(command);
    }
}
