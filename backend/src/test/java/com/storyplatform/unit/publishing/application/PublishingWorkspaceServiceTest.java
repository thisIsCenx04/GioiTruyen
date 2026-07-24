package com.storyplatform.unit.publishing.application;

import com.storyplatform.publishing.application.PublishingWorkspaceOperations;
import com.storyplatform.publishing.application.PublishingWorkspaceService;
import com.storyplatform.publishing.application.StoryDraftException;
import com.storyplatform.publishing.application.port
        .PublishingWorkspaceRepository;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingWorkspaceServiceTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "40000000-0000-4000-8000-000000000001";
    private final TeamPermissionAuthorizer permissions =
            mock(TeamPermissionAuthorizer.class);
    private final TeamStatusDirectory teams =
            mock(TeamStatusDirectory.class);
    private final PublishingWorkspaceRepository repository =
            mock(PublishingWorkspaceRepository.class);

    @Test
    void listsOwnedStoriesForAnActiveEditor() {
        when(teams.isActive(TEAM)).thenReturn(true);
        when(permissions.allows(ACTOR, TEAM, "story:edit"))
                .thenReturn(true);
        when(repository.stories(TEAM)).thenReturn(List.of(story()));

        assertThat(service().stories(ACTOR, TEAM))
                .containsExactly(story());
    }

    @Test
    void deniesInactiveOrUnauthorizedTeamsBeforeQueryingContent() {
        assertThatThrownBy(() -> service().stories(ACTOR, TEAM))
                .isInstanceOf(StoryDraftException.class)
                .extracting("code")
                .isEqualTo("PUBLISHING_WORKSPACE_FORBIDDEN");

        verify(repository, never()).stories(TEAM);
    }

    @Test
    void verifiesStoryOwnershipBeforeListingChapters() {
        when(teams.isActive(TEAM)).thenReturn(true);
        when(permissions.allows(ACTOR, TEAM, "story:create"))
                .thenReturn(true);
        when(repository.story(TEAM, STORY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().chapters(ACTOR, TEAM, STORY))
                .isInstanceOf(StoryDraftException.class)
                .extracting("code")
                .isEqualTo("PUBLISHING_STORY_NOT_FOUND");

        verify(repository, never()).chapters(TEAM, STORY);
    }

    private PublishingWorkspaceService service() {
        return new PublishingWorkspaceService(
                permissions,
                teams,
                repository
        );
    }

    private static PublishingWorkspaceOperations.StorySummary story() {
        return new PublishingWorkspaceOperations.StorySummary(
                STORY,
                TEAM,
                "story",
                "Story",
                "Synopsis",
                "ORIGINAL",
                "vi",
                List.of("30000000-0000-4000-8000-000000000001"),
                null,
                "ONGOING",
                "DRAFT",
                "50000000-0000-4000-8000-000000000001",
                1,
                1,
                Instant.parse("2026-07-24T00:00:00Z")
        );
    }
}
