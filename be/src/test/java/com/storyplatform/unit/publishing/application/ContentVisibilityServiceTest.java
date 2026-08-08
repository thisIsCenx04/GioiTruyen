package com.storyplatform.unit.publishing.application;

import com.storyplatform.publishing.application.ContentVisibilityException;
import com.storyplatform.publishing.application.ContentVisibilityOperations;
import com.storyplatform.publishing.application.ContentVisibilityService;
import com.storyplatform.publishing.application.port
        .ContentVisibilityRepository;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentVisibilityServiceTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "30000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final TeamPermissionAuthorizer permissions =
            mock(TeamPermissionAuthorizer.class);
    private final ContentVisibilityRepository repository =
            mock(ContentVisibilityRepository.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);

    @BeforeEach
    void setUp() {
        when(permissions.allows(
                ACTOR,
                TEAM,
                ContentVisibilityService.PUBLISH_PERMISSION
        )).thenReturn(true);
        when(repository.change(
                any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(true);
    }

    @Test
    void teamHidesAndReinstatesWithAuditOutboxContract() {
        when(repository.find(STORY))
                .thenReturn(Optional.of(candidate("PUBLISHED", null, 5)));
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);

        var hidden = service().teamChange(
                ACTOR, TEAM, STORY, 5, command(
                        ContentVisibilityOperations.Action.HIDE
                )
        );

        assertThat(hidden.state()).isEqualTo("HIDDEN");
        assertThat(hidden.version()).isEqualTo(6);
        verify(outbox).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo(ContentVisibilityService.EVENT_TYPE);

        when(repository.find(STORY))
                .thenReturn(Optional.of(candidate("HIDDEN", null, 6)));
        assertThat(service().teamChange(
                ACTOR, TEAM, STORY, 6, command(
                        ContentVisibilityOperations.Action.REINSTATE
                )
        ).state()).isEqualTo("PUBLISHED");
    }

    @Test
    void moderatorSuspendsAndRestoresPreviousVisibility() {
        when(repository.find(STORY))
                .thenReturn(Optional.of(candidate("HIDDEN", null, 4)));
        assertThat(service().moderationChange(
                ACTOR, STORY, 4, command(
                        ContentVisibilityOperations.Action.SUSPEND
                )
        ).state()).isEqualTo("SUSPENDED");

        when(repository.find(STORY)).thenReturn(Optional.of(
                candidate("SUSPENDED", "HIDDEN", 5)
        ));
        assertThat(service().moderationChange(
                ACTOR, STORY, 5, command(
                        ContentVisibilityOperations.Action.REINSTATE
                )
        ).state()).isEqualTo("HIDDEN");
    }

    @Test
    void rejectsUnauthorizedOwnershipAndInvalidTransitions() {
        ContentVisibilityService inactive =
                new ContentVisibilityService(
                        permissions,
                        ignored -> false,
                        repository,
                        outbox,
                        () -> ACTOR,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );
        assertCode(() -> inactive.teamChange(
                ACTOR, TEAM, STORY, 1, command(
                        ContentVisibilityOperations.Action.HIDE
                )
        ), "CONTENT_VISIBILITY_FORBIDDEN");
        when(repository.find(STORY)).thenReturn(Optional.of(
                new ContentVisibilityRepository.Candidate(
                        STORY,
                        "20000000-0000-4000-8000-000000000002",
                        "PUBLISHED",
                        null,
                        1
                )
        ));
        assertCode(() -> service().teamChange(
                ACTOR, TEAM, STORY, 1, command(
                        ContentVisibilityOperations.Action.HIDE
                )
        ), "CONTENT_NOT_FOUND");
        when(repository.find(STORY))
                .thenReturn(Optional.of(candidate("PUBLISHED", null, 1)));
        assertCode(() -> service().teamChange(
                ACTOR, TEAM, STORY, 1, command(
                        ContentVisibilityOperations.Action.SUSPEND
                )
        ), "CONTENT_VISIBILITY_INVALID");
        assertCode(() -> service().moderationChange(
                ACTOR, STORY, 1, command(
                        ContentVisibilityOperations.Action.HIDE
                )
        ), "CONTENT_VISIBILITY_INVALID");
    }

    @Test
    void rejectsMissingStaleMalformedAndRacedRequests() {
        assertCode(() -> service().moderationChange(
                ACTOR, "bad", 1, null
        ), "CONTENT_VISIBILITY_INVALID");
        when(repository.find(STORY)).thenReturn(Optional.empty());
        assertCode(() -> service().moderationChange(
                ACTOR, STORY, 1, null
        ), "CONTENT_NOT_FOUND");
        when(repository.find(STORY))
                .thenReturn(Optional.of(candidate("PUBLISHED", null, 2)));
        assertCode(() -> service().teamChange(
                ACTOR, TEAM, STORY, 1, command(
                        ContentVisibilityOperations.Action.HIDE
                )
        ), "CONTENT_VISIBILITY_STALE");
        assertCode(() -> service().teamChange(
                ACTOR, TEAM, STORY, 2,
                new ContentVisibilityOperations.VisibilityCommand(
                        ContentVisibilityOperations.Action.HIDE,
                        "x",
                        null
                )
        ), "CONTENT_VISIBILITY_INVALID");
        when(repository.change(
                any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(false);
        assertCode(() -> service().teamChange(
                ACTOR, TEAM, STORY, 2, command(
                        ContentVisibilityOperations.Action.HIDE
                )
        ), "CONTENT_VISIBILITY_RACE");
        verify(outbox, never()).append(any());
    }

    private ContentVisibilityService service() {
        return new ContentVisibilityService(
                permissions,
                TEAM::equals,
                repository,
                outbox,
                () -> "90000000-0000-4000-8000-000000000001",
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static ContentVisibilityOperations.VisibilityCommand command(
            ContentVisibilityOperations.Action action
    ) {
        return new ContentVisibilityOperations.VisibilityCommand(
                action, "OWNER_REQUEST", " requested "
        );
    }

    private static ContentVisibilityRepository.Candidate candidate(
            String state,
            String previous,
            long version
    ) {
        return new ContentVisibilityRepository.Candidate(
                STORY, TEAM, state, previous, version
        );
    }

    private static void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            String code
    ) {
        assertThatThrownBy(action)
                .isInstanceOf(ContentVisibilityException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
