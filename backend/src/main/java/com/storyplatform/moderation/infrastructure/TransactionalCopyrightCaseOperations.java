package com.storyplatform.moderation.infrastructure;

import com.storyplatform.moderation.application.CopyrightCaseOperations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

public class TransactionalCopyrightCaseOperations
        implements CopyrightCaseOperations {

    private final CopyrightCaseOperations delegate;

    public TransactionalCopyrightCaseOperations(
            CopyrightCaseOperations delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public CopyrightCaseView create(
            String claimantId,
            CreateCopyrightCase command
    ) {
        return delegate.create(claimantId, command);
    }

    @Override
    @Transactional
    public CopyrightCaseView appeal(
            String actorId,
            String caseId,
            String statement
    ) {
        return delegate.appeal(actorId, caseId, statement);
    }

    @Override
    @Transactional
    public CopyrightCaseView decide(
            String reviewerId,
            String caseId,
            Decision decision,
            String reasonCode,
            String note
    ) {
        return delegate.decide(
                reviewerId,
                caseId,
                decision,
                reasonCode,
                note
        );
    }
}
