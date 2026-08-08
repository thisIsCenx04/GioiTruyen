package com.storyplatform.community.infrastructure;

import com.storyplatform.community.application.CommentOperations;
import com.storyplatform.community.application.CommentRateLimiter;
import com.storyplatform.community.application.CommentSanitizer;
import com.storyplatform.community.application.CommentService;
import com.storyplatform.community.application.ReactionOperations;
import com.storyplatform.community.application.ReactionService;
import com.storyplatform.community.application.StoryRelationOperations;
import com.storyplatform.community.application.StoryRelationService;
import com.storyplatform.community.application.port.CommentRepository;
import com.storyplatform.community.application.port.ReactionRepository;
import com.storyplatform.community.application.port.StoryRelationRepository;
import com.storyplatform.community.infrastructure.persistence.DisabledCommentRepository;
import com.storyplatform.community.infrastructure.persistence.DisabledReactionRepository;
import com.storyplatform.community.infrastructure.persistence.DisabledStoryRelationRepository;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class CommunityConfiguration {

    @Bean
    CommentRepository commentRepository() {
        return new DisabledCommentRepository();
    }

    @Bean
    ReactionRepository reactionRepository() {
        return new DisabledReactionRepository();
    }

    @Bean
    StoryRelationRepository storyRelationRepository() {
        return new DisabledStoryRelationRepository();
    }

    @Bean
    CommentRateLimiter commentRateLimiter() {
        return new CommentRateLimiter() {
            @Override
            public boolean allow(String userId) {
                return true;
            }

            @Override
            public long retryAfterSeconds() {
                return 0;
            }
        };
    }

    @Bean
    CommentSanitizer commentSanitizer() {
        return new CommentSanitizer();
    }

    @Bean
    CommentOperations commentOperations(
            CommentRepository comments,
            CommentRateLimiter limiter,
            CommentSanitizer sanitizer
    ) {
        return new CommentService(
                comments,
                limiter,
                sanitizer,
                Clock.systemUTC()
        );
    }

    @Bean
    ReactionOperations reactionOperations(
            ReactionRepository reactions,
            OutboxAppender outbox
    ) {
        return new ReactionService(
                reactions,
                outbox,
                Clock.systemUTC()
        );
    }

    @Bean
    StoryRelationOperations storyRelationOperations(
            StoryRelationRepository relations,
            OutboxAppender outbox
    ) {
        return new StoryRelationService(
                relations,
                outbox,
                Clock.systemUTC()
        );
    }
}
