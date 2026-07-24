package com.storyplatform.unit.analytics.infrastructure;

import com.storyplatform.analytics.application.TrafficFraudOperations;
import com.storyplatform.analytics.infrastructure.TrafficFraudWorker;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrafficFraudWorkerTest {

    @Test
    void stopsOnEmptyQueueAndBoundsOnePoll() {
        TrafficFraudOperations empty = mock(TrafficFraudOperations.class);
        when(empty.processNext(anyString())).thenReturn(false);
        new TrafficFraudWorker(empty).poll();
        verify(empty).processNext(anyString());

        TrafficFraudOperations busy = mock(TrafficFraudOperations.class);
        when(busy.processNext(anyString())).thenReturn(true);
        new TrafficFraudWorker(busy).poll();
        verify(busy, times(50)).processNext(anyString());
    }
}
