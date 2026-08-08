package com.storyplatform.media.application;

import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;

import java.time.Instant;
import java.util.Optional;

public interface MediaProcessingOperations {

    boolean processNext(String workerId);

    record Candidate(
            String assetId,
            String publicId,
            long cloudinaryVersion,
            String format,
            String declaredSha256,
            long bytes,
            int width,
            int height,
            MediaOwnerType ownerType,
            String ownerId,
            UploadPurpose purpose,
            int attempt,
            Instant leaseUntil
    ) {
    }

    record PublishedAsset(
            String publicId,
            long version,
            String format,
            long bytes
    ) {
    }

    interface Repository {

        Optional<Candidate> claim(
                String workerId,
                Instant now,
                Instant leaseUntil,
                int maximumAttempts
        );

        boolean complete(
                Candidate candidate,
                String workerId,
                PublishedAsset published,
                Instant completedAt
        );

        void reject(
                Candidate candidate,
                String workerId,
                String reason,
                Instant completedAt
        );

        void reschedule(
                Candidate candidate,
                String workerId,
                String reason,
                Instant nextAttemptAt,
                boolean exhausted
        );
    }
}
