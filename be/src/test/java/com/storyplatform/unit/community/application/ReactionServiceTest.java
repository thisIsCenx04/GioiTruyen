package com.storyplatform.unit.community.application;

import com.storyplatform.community.application.ReactionService;
import com.storyplatform.community.application
        .ReactionTargetNotFoundException;
import com.storyplatform.community.application.port.ReactionRepository;
import com.storyplatform.community.domain.Reaction;
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

class ReactionServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T06:00:00Z");
    private final InMemoryReactions repository =
            new InMemoryReactions();
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final ReactionService service = new ReactionService(
            repository,
            outbox,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void concurrentAddCreatesOneActorTargetRelationAndOneDelta() {
        IntStream.range(0, 32).parallel().forEach(ignored ->
                service.add(
                        "actor",
                        Reaction.TargetType.COMMENT,
                        "target"
                )
        );

        assertThat(service.status(
                "actor",
                Reaction.TargetType.COMMENT,
                "target"
        )).extracting(
                view -> view.active(),
                view -> view.count()
        ).containsExactly(true, 1L);
        ArgumentCaptor<IntegrationEvent> event =
                ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(outbox, times(1)).append(event.capture());
        assertThat(event.getValue().eventType())
                .isEqualTo(ReactionService.EVENT_TYPE);
    }

    @Test
    void addAndRemoveAreIdempotentAndTargetsAreNotEnumerable() {
        service.add("actor", Reaction.TargetType.STORY, "target");
        service.add("actor", Reaction.TargetType.STORY, "target");
        service.remove("actor", Reaction.TargetType.STORY, "target");
        service.remove("actor", Reaction.TargetType.STORY, "target");

        assertThat(service.status(
                "actor",
                Reaction.TargetType.STORY,
                "target"
        ).active()).isFalse();
        verify(outbox, times(2)).append(
                org.mockito.ArgumentMatchers.any()
        );

        repository.visible = false;
        assertThatThrownBy(() -> service.status(
                "actor",
                Reaction.TargetType.CHAPTER,
                "hidden"
        )).isInstanceOf(ReactionTargetNotFoundException.class);
    }

    private static final class InMemoryReactions
            implements ReactionRepository {

        private final Set<String> values =
                ConcurrentHashMap.newKeySet();
        private volatile boolean visible = true;

        @Override
        public boolean targetIsVisible(
                Reaction.TargetType targetType,
                String targetId
        ) {
            return visible;
        }

        @Override
        public boolean insertIfAbsent(Reaction reaction) {
            return values.add(reaction.id());
        }

        @Override
        public boolean deleteIfPresent(
                Reaction.TargetType targetType,
                String targetId,
                String actorId
        ) {
            return values.remove(
                    targetType + ":" + targetId + ":" + actorId
            );
        }

        @Override
        public boolean exists(
                Reaction.TargetType targetType,
                String targetId,
                String actorId
        ) {
            return values.contains(
                    targetType + ":" + targetId + ":" + actorId
            );
        }

        @Override
        public long count(
                Reaction.TargetType targetType,
                String targetId
        ) {
            return values.stream()
                    .filter(value -> value.startsWith(
                            targetType + ":" + targetId + ":"
                    ))
                    .count();
        }
    }
}
