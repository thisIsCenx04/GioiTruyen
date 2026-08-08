package com.storyplatform.media.application.port;

import com.storyplatform.media.application.MediaProcessingOperations;

public interface MediaProcessingGateway {

    byte[] downloadOriginal(
            MediaProcessingOperations.Candidate candidate,
            long maximumBytes
    );

    MediaProcessingOperations.PublishedAsset publishNormalized(
            MediaProcessingOperations.Candidate candidate,
            byte[] source
    );
}
