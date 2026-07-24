package com.storyplatform.publishing.infrastructure;

import com.storyplatform.catalog.application.contract.ActiveCategoryDirectory;
import com.storyplatform.publishing.application.StoryDraftOperations;
import com.storyplatform.publishing.application.StoryDraftService;
import com.storyplatform.publishing.application.ChapterContentSanitizer;
import com.storyplatform.publishing.application.ChapterDraftOperations;
import com.storyplatform.publishing.application.ChapterDraftService;
import com.storyplatform.publishing.application.port.ChapterDraftRepository;
import com.storyplatform.publishing.application.port.StoryDraftRepository;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoChapterDraftRepository;
import com.storyplatform.publishing.infrastructure.persistence
        .MongoStoryDraftRepository;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class PublishingConfiguration {

    @Bean
    StoryDraftRepository storyDraftRepository(MongoTemplate mongo) {
        return new MongoStoryDraftRepository(mongo);
    }

    @Bean
    ChapterDraftRepository chapterDraftRepository(MongoTemplate mongo) {
        return new MongoChapterDraftRepository(mongo);
    }

    @Bean
    StoryDraftOperations storyDraftOperations(
            TeamPermissionAuthorizer permissions,
            TeamStatusDirectory teams,
            ActiveCategoryDirectory categories,
            StoryDraftRepository drafts,
            OutboxAppender outbox
    ) {
        StoryDraftService service = new StoryDraftService(
                permissions,
                teams,
                categories,
                drafts,
                outbox,
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC()
        );
        return new TransactionalStoryDraftOperations(service);
    }

    @Bean
    ChapterDraftOperations chapterDraftOperations(
            TeamPermissionAuthorizer permissions,
            TeamStatusDirectory teams,
            StoryDraftRepository stories,
            ChapterDraftRepository chapters
    ) {
        ChapterDraftService service = new ChapterDraftService(
                permissions,
                teams,
                stories,
                chapters,
                new ChapterContentSanitizer(),
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC()
        );
        return new TransactionalChapterDraftOperations(service);
    }
}
