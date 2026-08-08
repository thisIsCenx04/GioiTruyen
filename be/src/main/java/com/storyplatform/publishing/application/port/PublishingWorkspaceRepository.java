package com.storyplatform.publishing.application.port;

import com.storyplatform.publishing.application
        .PublishingWorkspaceOperations;

import java.util.List;
import java.util.Optional;

public interface PublishingWorkspaceRepository {

    List<PublishingWorkspaceOperations.StorySummary> stories(
            String teamId
    );

    Optional<PublishingWorkspaceOperations.StorySummary> story(
            String teamId,
            String storyId
    );

    List<PublishingWorkspaceOperations.ChapterEditor> chapters(
            String teamId,
            String storyId
    );
}
