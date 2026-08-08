package com.storyplatform.unit.publishing.application;

import com.storyplatform.publishing.application
        .PublishingSubmissionException;
import com.storyplatform.publishing.application
        .PublishingSubmissionService;
import com.storyplatform.publishing.application.port
        .PublishingSubmissionRepository;
import com.storyplatform.publishing.domain.PublishingReview;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingSubmissionServiceTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "40000000-0000-4000-8000-000000000001";
    private static final String STORY_REVISION =
            "50000000-0000-4000-8000-000000000001";
    private static final String CHAPTER =
            "60000000-0000-4000-8000-000000000001";
    private static final String CHAPTER_REVISION =
            "70000000-0000-4000-8000-000000000001";
    private static final String REVIEW =
            "80000000-0000-4000-8000-000000000001";
    private static final String EVENT =
            "90000000-0000-4000-8000-000000000001";
    private static final String KEY = "submit-request-001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");

    private final TeamPermissionAuthorizer permissions =
            mock(TeamPermissionAuthorizer.class);
    private final PublishingSubmissionRepository repository =
            mock(PublishingSubmissionRepository.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);

    @BeforeEach
    void setUp() {
        when(permissions.allows(
                ACTOR,
                TEAM,
                PublishingSubmissionService.SUBMIT_PERMISSION
        )).thenReturn(true);
        when(repository.findCandidate(TEAM, STORY))
                .thenReturn(Optional.of(candidate()));
        when(repository.submit(any(), any())).thenReturn(true);
    }

    @Test
    void freezesCurrentStoryAndChapterRevisionsAndEmitsEvent() {
        ArgumentCaptor<PublishingReview> review =
                ArgumentCaptor.forClass(PublishingReview.class);
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);

        var result = service().submit(ACTOR, TEAM, STORY, KEY);

        verify(repository).submit(any(), review.capture());
        verify(outbox).append(event.capture());
        assertThat(result.reviewId()).isEqualTo(REVIEW);
        assertThat(result.submittedRevision())
                .isEqualTo(STORY_REVISION);
        assertThat(result.chapterRevisions())
                .containsExactly(new com.storyplatform.publishing.application
                        .PublishingSubmissionOperations
                        .FrozenChapterRevision(
                        CHAPTER,
                        CHAPTER_REVISION,
                        1
                ));
        assertThat(review.getValue().state()).isEqualTo(
                PublishingReview.State.AUTOMATED_CHECK_PENDING
        );
        assertThat(event.getValue().eventType()).isEqualTo(
                PublishingSubmissionService.EVENT_TYPE
        );
    }

    @Test
    void retryReturnsFrozenReviewWithoutAnotherSideEffect() {
        PublishingReview previous = review();
        when(repository.findReplay(TEAM, KEY))
                .thenReturn(Optional.of(previous));

        var result = service().submit(ACTOR, TEAM, STORY, KEY);

        assertThat(result.reviewId()).isEqualTo(REVIEW);
        verify(repository, never()).findCandidate(any(), any());
        verify(repository, never()).submit(any(), any());
        verify(outbox, never()).append(any());
    }

    @Test
    void rejectsReusedKeyForAnotherStory() {
        PublishingReview previous = review();
        when(repository.findReplay(TEAM, KEY))
                .thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> service().submit(
                ACTOR,
                TEAM,
                "40000000-0000-4000-8000-000000000002",
                KEY
        )).isInstanceOf(PublishingSubmissionException.class)
                .extracting("code")
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void deniesUnauthorizedActorBeforeRepositoryLookup() {
        assertThatThrownBy(() -> service().submit(
                ACTOR,
                "20000000-0000-4000-8000-000000000002",
                STORY,
                KEY
        )).isInstanceOf(PublishingSubmissionException.class)
                .extracting("code")
                .isEqualTo("STORY_SUBMIT_FORBIDDEN");
        verify(repository, never()).findReplay(any(), any());
    }

    @Test
    void deniesActiveTeamMemberWithoutSubmitPermission() {
        when(permissions.allows(
                ACTOR,
                TEAM,
                PublishingSubmissionService.SUBMIT_PERMISSION
        )).thenReturn(false);

        assertThatThrownBy(() -> service().submit(
                ACTOR, TEAM, STORY, KEY
        )).isInstanceOf(PublishingSubmissionException.class)
                .extracting("code")
                .isEqualTo("STORY_SUBMIT_FORBIDDEN");
        verify(repository, never()).findReplay(any(), any());
    }

    @Test
    void rejectsMissingOrIncompleteStory() {
        when(repository.findCandidate(TEAM, STORY))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().submit(
                ACTOR, TEAM, STORY, KEY
        )).isInstanceOf(PublishingSubmissionException.class)
                .extracting("code")
                .isEqualTo("STORY_DRAFT_NOT_FOUND");

        when(repository.findCandidate(TEAM, STORY))
                .thenReturn(Optional.of(new PublishingSubmissionRepository
                        .SubmissionCandidate(
                        STORY,
                        TEAM,
                        STORY_REVISION,
                        2,
                        List.of()
                )));
        assertThatThrownBy(() -> service().submit(
                ACTOR, TEAM, STORY, KEY
        )).isInstanceOf(PublishingSubmissionException.class)
                .extracting("code")
                .isEqualTo("STORY_CHAPTER_REQUIRED");
    }

    @Test
    void staleCompareAndSetNeverEmitsSubmissionEvent() {
        when(repository.submit(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service().submit(
                ACTOR, TEAM, STORY, KEY
        )).isInstanceOf(PublishingSubmissionException.class)
                .extracting("code")
                .isEqualTo("STORY_SUBMISSION_STALE");
        verify(outbox, never()).append(any());
    }

    @Test
    void rejectsMalformedIdempotencyKey() {
        assertThatThrownBy(() -> service().submit(
                ACTOR, TEAM, STORY, "short"
        )).isInstanceOf(PublishingSubmissionException.class)
                .extracting("code")
                .isEqualTo("IDEMPOTENCY_KEY_INVALID");
        assertThatThrownBy(() -> service().submit(
                ACTOR, TEAM, STORY, null
        )).isInstanceOf(PublishingSubmissionException.class);
        assertThatThrownBy(() -> service().submit(
                ACTOR, TEAM, STORY, "x".repeat(129)
        )).isInstanceOf(PublishingSubmissionException.class);
        assertThatThrownBy(() -> service().submit(
                ACTOR, TEAM, STORY, "invalid$key"
        )).isInstanceOf(PublishingSubmissionException.class);
        verify(repository, never()).findReplay(any(), any());
    }

    @Test
    void rejectsStoryExceedingFrozenChapterLimit() {
        PublishingReview.ChapterRevisionRef chapter =
                new PublishingReview.ChapterRevisionRef(
                        CHAPTER,
                        CHAPTER_REVISION,
                        1
                );
        when(repository.findCandidate(TEAM, STORY)).thenReturn(Optional.of(
                new PublishingSubmissionRepository.SubmissionCandidate(
                        STORY,
                        TEAM,
                        STORY_REVISION,
                        2,
                        java.util.Collections.nCopies(10_001, chapter)
                )
        ));

        assertThatThrownBy(() -> service().submit(
                ACTOR, TEAM, STORY, KEY
        )).isInstanceOf(PublishingSubmissionException.class)
                .extracting("code")
                .isEqualTo("STORY_CHAPTER_REQUIRED");
        verify(repository, never()).submit(any(), any());
    }

    private PublishingSubmissionService service() {
        ArrayDeque<String> ids = new ArrayDeque<>(
                List.of(REVIEW, EVENT)
        );
        return new PublishingSubmissionService(
                permissions,
                TEAM::equals,
                repository,
                outbox,
                ids::removeFirst,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static PublishingSubmissionRepository.SubmissionCandidate
            candidate() {
        return new PublishingSubmissionRepository.SubmissionCandidate(
                STORY,
                TEAM,
                STORY_REVISION,
                2,
                List.of(new PublishingReview.ChapterRevisionRef(
                        CHAPTER,
                        CHAPTER_REVISION,
                        1
                ))
        );
    }

    private static PublishingReview review() {
        return new PublishingReview(
                REVIEW,
                PublishingReview.TargetType.STORY,
                STORY,
                TEAM,
                STORY_REVISION,
                2,
                candidate().chapterRevisions(),
                PublishingReview.State.AUTOMATED_CHECK_PENDING,
                ACTOR,
                NOW,
                1,
                KEY,
                sha256(STORY)
        );
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                            ))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
