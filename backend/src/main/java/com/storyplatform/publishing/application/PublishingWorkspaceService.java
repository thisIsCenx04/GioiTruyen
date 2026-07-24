package com.storyplatform.publishing.application;

import com.storyplatform.publishing.application.port
        .PublishingWorkspaceRepository;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PublishingWorkspaceService
        implements PublishingWorkspaceOperations {

    private static final List<String> WORKSPACE_PERMISSIONS = List.of(
            "story:create",
            "story:edit",
            "story:submit",
            "story:publish"
    );

    private final TeamPermissionAuthorizer permissions;
    private final TeamStatusDirectory teams;
    private final PublishingWorkspaceRepository repository;

    public PublishingWorkspaceService(
            TeamPermissionAuthorizer permissions,
            TeamStatusDirectory teams,
            PublishingWorkspaceRepository repository
    ) {
        this.permissions = Objects.requireNonNull(permissions);
        this.teams = Objects.requireNonNull(teams);
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public List<StorySummary> stories(String actorId, String teamId) {
        String team = authorize(actorId, teamId);
        return List.copyOf(repository.stories(team));
    }

    @Override
    public StorySummary story(
            String actorId,
            String teamId,
            String storyId
    ) {
        String team = authorize(actorId, teamId);
        return repository.story(team, uuid(storyId))
                .orElseThrow(() -> notFound());
    }

    @Override
    public List<ChapterEditor> chapters(
            String actorId,
            String teamId,
            String storyId
    ) {
        String team = authorize(actorId, teamId);
        String story = uuid(storyId);
        if (repository.story(team, story).isEmpty()) {
            throw notFound();
        }
        return List.copyOf(repository.chapters(team, story));
    }

    private String authorize(String actorId, String teamId) {
        String team = uuid(teamId);
        boolean allowed = teams.isActive(team)
                && WORKSPACE_PERMISSIONS.stream().anyMatch(permission ->
                permissions.allows(actorId, team, permission)
        );
        if (!allowed) {
            throw new StoryDraftException(
                    "PUBLISHING_WORKSPACE_FORBIDDEN",
                    "An active publishing Team membership is required.",
                    StoryDraftException.Kind.FORBIDDEN
            );
        }
        return team;
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new StoryDraftException(
                    "PUBLISHING_WORKSPACE_INVALID",
                    "A workspace identifier is invalid.",
                    StoryDraftException.Kind.INVALID
            );
        }
    }

    private static StoryDraftException notFound() {
        return new StoryDraftException(
                "PUBLISHING_STORY_NOT_FOUND",
                "The Team publishing story does not exist.",
                StoryDraftException.Kind.NOT_FOUND
        );
    }
}
