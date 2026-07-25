package com.storyplatform.monetization.application;

import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;

public interface MonetizationReconciliationResolutionOperations {

    MonetizationReconciliationCase resolve(
            String caseId,
            String actorId,
            String reason,
            MonetizationReconciliationCase.ResolutionAction action,
            String compensationTransactionId
    );
}
