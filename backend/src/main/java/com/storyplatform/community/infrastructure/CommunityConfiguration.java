package com.storyplatform.community.infrastructure;

import com.storyplatform.community.application.CommentOperations;
import com.storyplatform.community.application.CommentRateLimiter;
import com.storyplatform.community.application.CommentSanitizer;
import com.storyplatform.community.application.CommentService;
import com.storyplatform.community.application.port.CommentRepository;
import com.storyplatform.community.application.StoryRelationOperations;
import com.storyplatform.community.application.StoryRelationService;
import com.storyplatform.community.application.port.StoryRelationRepository;
import com.storyplatform.community.infrastructure.persistence
        .MongoCommentRepository;
import com.storyplatform.community.infrastructure.persistence
        .MongoStoryRelationRepository;
import com.storyplatform.shared.cache.RedisKeyFactory;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

@Configuration(proxyBeanMethods = false)
public class CommunityConfiguration {

    @Bean
    CommentRepository commentRepository(MongoTemplate mongo) {
        return new MongoCommentRepository(mongo);
    }

    @Bean
    CommentRateLimiter commentRateLimiter(
            StringRedisTemplate redis,
            RedisKeyFactory keys,
            @Value("${app.community.comments.rate-limit.maximum}")
            int maximum,
            @Value("${app.community.comments.rate-limit.window}")
            Duration window,
            @Value("${app.identity.login-risk.hmac-key}")
            String encodedKey
    ) {
        return new RedisCommentRateLimiter(
                redis,
                keys,
                maximum,
                window,
                Base64.getDecoder().decode(encodedKey)
        );
    }

    @Bean
    CommentOperations commentOperations(
            CommentRepository repository,
            CommentRateLimiter limiter
    ) {
        return new CommentService(
                repository,
                limiter,
                new CommentSanitizer(),
                Clock.systemUTC()
        );
    }

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
