package com.storyplatform.community.infrastructure;

import com.storyplatform.community.application.StoryRelationOperations;
import com.storyplatform.community.application.StoryRelationService;
import com.storyplatform.community.application.port.StoryRelationRepository;
import com.storyplatform.community.infrastructure.persistence
        .MongoStoryRelationRepository;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CommunityConfiguration {

    @Bean
    StoryRelationRepository storyRelationRepository(MongoTemplate mongo) {
        return new MongoStoryRelationRepository(mongo);
    }

    @Bean
    StoryRelationOperations storyRelationOperations(
            StoryRelationRepository repository,
            OutboxAppender outbox
    ) {
        return new TransactionalStoryRelationOperations(
                new StoryRelationService(
                        repository,
                        outbox,
                        Clock.systemUTC()
                )
        );
    }

    @Bean
    StoryRelationCounterStore storyRelationCounterStore(
            MongoTemplate mongo
    ) {
        return new StoryRelationCounterStore(mongo, Clock.systemUTC());
    }

    @Bean
    StoryRelationCounterProjector storyRelationCounterProjector(
            StoryRelationCounterStore counters,
            ObjectMapper mapper
    ) {
        return new StoryRelationCounterProjector(counters, mapper);
    }

    @Bean
    StoryRelationCounterReconciler storyRelationCounterReconciler(
            StoryRelationRepository relations,
            StoryRelationCounterStore counters
    ) {
        return new StoryRelationCounterReconciler(relations, counters);
    }
}
