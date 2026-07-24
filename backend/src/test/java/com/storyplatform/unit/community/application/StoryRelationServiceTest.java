package com.storyplatform.unit.community.application;

import com.storyplatform.community.application.StoryNotFoundException;
import com.storyplatform.community.application.StoryRelationService;
import com.storyplatform.community.application.port.StoryRelationRepository;
import com.storyplatform.community.domain.StoryRelation;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StoryRelationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T00:00:00Z");
    private final InMemoryRelations relations = new InMemoryRelations();
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final StoryRelationService service = new StoryRelationService(
            relations,
            outbox,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void concurrentFavoriteIsCreatedOnceAndEmitsOneCounterDelta() {
        IntStream.range(0, 32).parallel().forEach(ignored ->
                service.add(
                        "user-1",
                        "story-1",
                        StoryRelation.Type.FAVORITE
                )
        );

        assertThat(service.status(
                "user-1",
                "story-1",
                StoryRelation.Type.FAVORITE
        )).extracting(
                view -> view.active(),
                view -> view.count()
        ).containsExactly(true, 1L);
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(outbox, times(1)).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo(StoryRelationService.EVENT_TYPE);
    }

    @Test
    void favoriteAndFollowAreIndependentAndRemovalIsIdempotent() {
        service.add("user-1", "story-1", StoryRelation.Type.FAVORITE);
        service.add("user-1", "story-1", StoryRelation.Type.FOLLOW);
        service.remove("user-1", "story-1", StoryRelation.Type.FAVORITE);
        service.remove("user-1", "story-1", StoryRelation.Type.FAVORITE);

        assertThat(service.status(
                "user-1", "story-1", StoryRelation.Type.FAVORITE
        ).active()).isFalse();
        assertThat(service.status(
                "user-1", "story-1", StoryRelation.Type.FOLLOW
        ).active()).isTrue();
        verify(outbox, times(3)).append(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void hiddenStoryCannotBeEnumeratedThroughRelations() {
        relations.published = false;
        assertThatThrownBy(() -> service.add(
                "user-1", "story-1", StoryRelation.Type.FOLLOW
        )).isInstanceOf(StoryNotFoundException.class);
    }

    private static final class InMemoryRelations
            implements StoryRelationRepository {

        private final Set<String> values = ConcurrentHashMap.newKeySet();
        private volatile boolean published = true;

        @Override
        public boolean storyIsPublished(String storyId) {
            return published;
        }

        @Override
        public boolean insertIfAbsent(StoryRelation relation) {
            return values.add(relation.id());
        }

        @Override
        public boolean deleteIfPresent(
                String storyId,
                String userId,
                StoryRelation.Type type
        ) {
            return values.remove(storyId + ":" + userId + ":" + type);
        }

        @Override
        public boolean exists(
                String storyId,
                String userId,
                StoryRelation.Type type
        ) {
            return values.contains(storyId + ":" + userId + ":" + type);
        }

        @Override
        public long count(String storyId, StoryRelation.Type type) {
            return values.stream()
                    .filter(value -> value.startsWith(storyId + ":"))
                    .filter(value -> value.endsWith(":" + type))
                    .count();
        }
    }
}
