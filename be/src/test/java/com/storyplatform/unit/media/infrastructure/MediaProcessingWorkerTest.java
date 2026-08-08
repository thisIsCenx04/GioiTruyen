package com.storyplatform.unit.media.infrastructure;

import com.storyplatform.media.application.MediaProcessingOperations;
import com.storyplatform.media.infrastructure.MediaProcessingWorker;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaProcessingWorkerTest {

    @Test
    void stopsWhenQueueBecomesEmpty() {
        MediaProcessingOperations processing =
                mock(MediaProcessingOperations.class);
        when(processing.processNext(anyString()))
                .thenReturn(true, false);

        new MediaProcessingWorker(processing).poll();

        verify(processing, times(2)).processNext(anyString());
    }

    @Test
    void boundsEachPollToEightAssets() {
        MediaProcessingOperations processing =
                mock(MediaProcessingOperations.class);
        when(processing.processNext(anyString())).thenReturn(true);

        new MediaProcessingWorker(processing).poll();

        verify(processing, times(8)).processNext(anyString());
    }
}
