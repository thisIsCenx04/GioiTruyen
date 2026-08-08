package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.ReferralOperations;
import com.storyplatform.monetization.infrastructure.ReferralRewardWorker;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReferralRewardWorkerTest {

    @Test
    void processesBoundedReplaySafeBatch() {
        ReferralOperations operations = mock(ReferralOperations.class);

        new ReferralRewardWorker(operations).rewardEligible();

        verify(operations).rewardEligible(100);
    }
}
