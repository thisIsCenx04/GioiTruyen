package com.storyplatform.unit.analytics.infrastructure;

import com.storyplatform.analytics.application
        .ReadingViewValidationOperations;
import com.storyplatform.analytics.infrastructure
        .ReadingViewValidationWorker;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingViewValidationWorkerTest {

    @Test
    void stopsAtEmptyQueueAndBoundsBusyBatch() {
        ReadingViewValidationOperations validation =
                mock(ReadingViewValidationOperations.class);
        when(validation.processNext(anyString()))
                .thenReturn(true, false);
        new ReadingViewValidationWorker(validation).poll();
        verify(validation, times(2)).processNext(anyString());

        ReadingViewValidationOperations busy =
                mock(ReadingViewValidationOperations.class);
        when(busy.processNext(anyString())).thenReturn(true);
        new ReadingViewValidationWorker(busy).poll();
        verify(busy, times(20)).processNext(anyString());
    }
}
