package com.storyplatform.unit.publishing.infrastructure;

import com.storyplatform.publishing.application
        .PublishingPropagationOperations;
import com.storyplatform.publishing.infrastructure
        .PublishingPropagationWorker;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingPropagationWorkerTest {

    @Test
    void drainsBoundedBatchAndStopsWhenEmpty() {
        PublishingPropagationOperations operations =
                mock(PublishingPropagationOperations.class);
        when(operations.processNext(anyString()))
                .thenReturn(true, true, false);
        new PublishingPropagationWorker(operations).poll();
        verify(operations, times(3)).processNext(anyString());

        operations = mock(PublishingPropagationOperations.class);
        when(operations.processNext(anyString())).thenReturn(true);
        new PublishingPropagationWorker(operations).poll();
        verify(operations, times(16)).processNext(anyString());
    }
}
