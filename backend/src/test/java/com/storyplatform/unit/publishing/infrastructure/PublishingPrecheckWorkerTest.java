package com.storyplatform.unit.publishing.infrastructure;

import com.storyplatform.publishing.application.PublishingPrecheckOperations;
import com.storyplatform.publishing.infrastructure.PublishingPrecheckWorker;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingPrecheckWorkerTest {

    @Test
    void stopsWhenQueueIsEmpty() {
        PublishingPrecheckOperations operations =
                mock(PublishingPrecheckOperations.class);
        PublishingPrecheckWorker worker =
                new PublishingPrecheckWorker(operations);

        worker.poll();

        verify(operations).processNext(any());
    }

    @Test
    void boundsEachPollingBatch() {
        PublishingPrecheckOperations operations =
                mock(PublishingPrecheckOperations.class);
        when(operations.processNext(any())).thenReturn(true);
        PublishingPrecheckWorker worker =
                new PublishingPrecheckWorker(operations);

        worker.poll();

        verify(operations, times(8)).processNext(any());
    }
}
