package com.storyplatform.unit.publishing.infrastructure;

import com.storyplatform.publishing.application.PublishingDueOperations;
import com.storyplatform.publishing.infrastructure.PublishingDueWorker;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingDueWorkerTest {

    @Test
    void drainsAvailableBatchAndStopsAtFirstEmptyClaim() {
        PublishingDueOperations operations =
                mock(PublishingDueOperations.class);
        when(operations.processNext(
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(true, true, false);

        new PublishingDueWorker(operations).poll();

        verify(operations, times(3)).processNext(
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void capsOnePollToProtectTheSchedulerThread() {
        PublishingDueOperations operations =
                mock(PublishingDueOperations.class);
        when(operations.processNext(
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(true);

        new PublishingDueWorker(operations).poll();

        verify(operations, times(16)).processNext(
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
