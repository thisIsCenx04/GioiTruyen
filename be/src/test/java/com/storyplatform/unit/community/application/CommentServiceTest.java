package com.storyplatform.unit.community.application;

import com.storyplatform.community.application.CommentException;
import com.storyplatform.community.application.CommentOperations;
import com.storyplatform.community.application.CommentRateLimiter;
import com.storyplatform.community.application.CommentSanitizer;
import com.storyplatform.community.application.CommentService;
import com.storyplatform.community.application.port.CommentRepository;
import com.storyplatform.community.domain.Comment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CommentServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T05:00:00Z");
    private static final String TARGET =
            "20000000-0000-4000-8000-000000000001";
    private final CommentRepository repository =
            mock(CommentRepository.class);
    private final CommentRateLimiter limiter =
            mock(CommentRateLimiter.class);
    private CommentService service;

    @BeforeEach
    void setUp() {
        service = new CommentService(
                repository,
                limiter,
                new CommentSanitizer(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(repository.targetIsPublished(any(), eq(TARGET)))
                .thenReturn(true);
        when(repository.author(anyString())).thenAnswer(invocation ->
                new CommentRepository.Author(
                        invocation.getArgument(0),
                        "Độc giả",
                        null
                ));
        when(repository.authors(anySet())).thenAnswer(invocation -> {
            java.util.Set<?> ids = invocation.getArgument(0);
            return ids.stream()
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.toMap(
                    java.util.function.Function.identity(),
                    id -> new CommentRepository.Author(id, "Độc giả", null)
            ));
        });
        when(limiter.allow(anyString())).thenReturn(true);
    }

    @Test
    void createsSanitizedRootComment() {
        CommentOperations.CommentView result = service.create(
                "author",
                new CommentOperations.CreateCommand(
                        Comment.TargetType.STORY,
                        TARGET,
                        null,
                        "<b>Xin chào</b><img onerror=alert(1)>"
                )
        );

        assertThat(result.body()).isEqualTo("Xin chào");
        assertThat(result.depth()).isZero();
        assertThat(result.rootId()).isEqualTo(result.id());
        assertThat(result.version()).isEqualTo(1);
        verify(repository).insert(any(Comment.class), anyString());
    }

    @Test
    void rejectsRateAndRecentDuplicateSpam() {
        when(limiter.allow("author")).thenReturn(false);
        when(limiter.retryAfterSeconds()).thenReturn(60L);

        assertThatThrownBy(() -> create(null))
                .isInstanceOfSatisfying(
                        CommentException.class,
                        error -> {
                            assertThat(error.kind()).isEqualTo(
                                    CommentException.Kind.RATE_LIMITED
                            );
                            assertThat(error.retryAfterSeconds())
                                    .isEqualTo(60);
                        }
                );
        verify(repository, never()).insert(any(), anyString());

        when(limiter.allow("author")).thenReturn(true);
        when(repository.duplicateExists(
                eq("author"),
                any(),
                eq(TARGET),
                anyString(),
                any()
        )).thenReturn(true);
        assertThatThrownBy(() -> create(null))
                .isInstanceOfSatisfying(
                        CommentException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("COMMENT_DUPLICATE")
                );
    }

    @Test
    void enforcesSameTargetAndThreeLevelThreadLimit() {
        String parentId = "30000000-0000-4000-8000-000000000001";
        when(repository.find(parentId)).thenReturn(Optional.of(comment(
                parentId,
                "author",
                2,
                Comment.Status.VISIBLE,
                1
        )));

        assertThatThrownBy(() -> create(parentId))
                .isInstanceOfSatisfying(
                        CommentException.class,
                        error -> assertThat(error.getMessage())
                                .contains("3 levels")
                );
    }

    @Test
    void preventsBolaAndReportsStaleVersionWithoutMutating() {
        String id = "30000000-0000-4000-8000-000000000002";
        when(repository.find(id)).thenReturn(Optional.of(comment(
                id,
                "other-user",
                0,
                Comment.Status.VISIBLE,
                3
        )));
        when(repository.updateOwned(
                eq(id),
                eq("attacker"),
                eq(3L),
                anyString(),
                anyString(),
                eq(NOW)
        )).thenReturn(false);

        assertThatThrownBy(() -> service.update(
                "attacker",
                id,
                3,
                "chiếm quyền"
        )).isInstanceOfSatisfying(
                CommentException.class,
                error -> assertThat(error.kind()).isEqualTo(
                        CommentException.Kind.NOT_FOUND
                )
        );

        when(repository.find(id)).thenReturn(Optional.of(comment(
                id,
                "attacker",
                0,
                Comment.Status.VISIBLE,
                4
        )));
        assertThatThrownBy(() -> service.delete("attacker", id, 3))
                .isInstanceOfSatisfying(
                        CommentException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("COMMENT_VERSION_CONFLICT")
                );
    }

    @Test
    void listsWithOpaqueKeysetCursorAndTombstonesDeletedBody() {
        Comment first = comment(
                "30000000-0000-4000-8000-000000000003",
                "author",
                0,
                Comment.Status.VISIBLE,
                1
        );
        Comment second = comment(
                "30000000-0000-4000-8000-000000000004",
                "author",
                1,
                Comment.Status.DELETED,
                2
        );
        when(repository.list(
                Comment.TargetType.STORY,
                TARGET,
                null,
                null,
                2
        )).thenReturn(List.of(first, second));

        CommentOperations.CommentPage page = service.list(
                new CommentOperations.ListQuery(
                        Comment.TargetType.STORY,
                        TARGET,
                        null,
                        1
                )
        );

        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isNotBlank();
        assertThat(page.items()).hasSize(1);
        when(repository.list(
                Comment.TargetType.STORY,
                TARGET,
                NOW,
                first.id(),
                2
        )).thenReturn(List.of(second));
        CommentOperations.CommentPage last = service.list(
                new CommentOperations.ListQuery(
                        Comment.TargetType.STORY,
                        TARGET,
                        page.nextCursor(),
                        1
                )
        );
        assertThat(last.hasMore()).isFalse();
        assertThat(last.nextCursor()).isNull();
        assertThat(last.items().getFirst().body())
                .isEqualTo("Bình luận đã được xóa.");

        assertThatThrownBy(() -> service.list(
                new CommentOperations.ListQuery(
                        Comment.TargetType.STORY,
                        TARGET,
                        "not-a-cursor",
                        20
                )
        )).isInstanceOf(CommentException.class);
    }

    @Test
    void createsReplyAndCompletesOwnedUpdateDeleteLifecycle() {
        String parentId = "30000000-0000-4000-8000-000000000005";
        Comment parent = comment(
                parentId,
                "other",
                1,
                Comment.Status.VISIBLE,
                1
        );
        when(repository.find(parentId)).thenReturn(Optional.of(parent));

        CommentOperations.CommentView reply = create(parentId);

        assertThat(reply.parentId()).isEqualTo(parentId);
        assertThat(reply.rootId()).isEqualTo(parent.rootId());
        assertThat(reply.depth()).isEqualTo(2);

        String id = "30000000-0000-4000-8000-000000000006";
        Comment visible = comment(
                id,
                "author",
                0,
                Comment.Status.VISIBLE,
                2
        );
        Comment deleted = comment(
                id,
                "author",
                0,
                Comment.Status.DELETED,
                3
        );
        when(repository.updateOwned(
                eq(id),
                eq("author"),
                eq(1L),
                anyString(),
                anyString(),
                eq(NOW)
        )).thenReturn(true);
        when(repository.deleteOwned(id, "author", 2, NOW))
                .thenReturn(true);
        when(repository.find(id))
                .thenReturn(Optional.of(visible), Optional.of(deleted));

        assertThat(service.update("author", id, 1, "Đã sửa").version())
                .isEqualTo(2);
        assertThat(service.delete("author", id, 2).status())
                .isEqualTo(Comment.Status.DELETED);
    }

    @Test
    void rejectsMissingTargetsParentsAndOutOfRangePageSizes() {
        when(repository.targetIsPublished(
                Comment.TargetType.CHAPTER,
                TARGET
        )).thenReturn(false);
        assertThatThrownBy(() -> service.list(
                new CommentOperations.ListQuery(
                        Comment.TargetType.CHAPTER,
                        TARGET,
                        null,
                        20
                )
        )).isInstanceOfSatisfying(
                CommentException.class,
                error -> assertThat(error.code())
                        .isEqualTo("COMMENT_TARGET_NOT_FOUND")
        );
        assertThatThrownBy(() -> service.list(
                new CommentOperations.ListQuery(null, TARGET, null, 20)
        )).isInstanceOf(CommentException.class);
        assertThatThrownBy(() -> service.list(
                new CommentOperations.ListQuery(
                        Comment.TargetType.STORY,
                        TARGET,
                        null,
                        0
                )
        )).isInstanceOf(CommentException.class);
        assertThatThrownBy(() -> service.list(
                new CommentOperations.ListQuery(
                        Comment.TargetType.STORY,
                        TARGET,
                        null,
                        101
                )
        )).isInstanceOf(CommentException.class);

        String parentId = "30000000-0000-4000-8000-000000000007";
        when(repository.find(parentId)).thenReturn(Optional.of(new Comment(
                parentId,
                Comment.TargetType.CHAPTER,
                TARGET,
                null,
                parentId,
                0,
                "author",
                "body",
                Comment.Status.VISIBLE,
                1,
                NOW,
                NOW
        )));
        assertThatThrownBy(() -> create(parentId))
                .isInstanceOf(CommentException.class);
    }

    private CommentOperations.CommentView create(String parentId) {
        return service.create(
                "author",
                new CommentOperations.CreateCommand(
                        Comment.TargetType.STORY,
                        TARGET,
                        parentId,
                        "Nội dung"
                )
        );
    }

    private static Comment comment(
            String id,
            String author,
            int depth,
            Comment.Status status,
            long version
    ) {
        return new Comment(
                id,
                Comment.TargetType.STORY,
                TARGET,
                depth == 0 ? null
                        : "30000000-0000-4000-8000-000000000000",
                id,
                depth,
                author,
                status == Comment.Status.DELETED ? "" : "Nội dung",
                status,
                version,
                NOW,
                NOW
        );
    }
}
