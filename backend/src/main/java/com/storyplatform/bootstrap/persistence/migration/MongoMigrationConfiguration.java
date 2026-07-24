package com.storyplatform.bootstrap.persistence.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MongoMigrationProperties.class)
public class MongoMigrationConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            MongoMigrationConfiguration.class
    );

    @Bean
    MigrationMetadataIndexes migrationMetadataIndexes() {
        return new MigrationMetadataIndexes();
    }

    @Bean
    OutboxInboxIndexes outboxInboxIndexes() {
        return new OutboxInboxIndexes();
    }

    @Bean
    UserIndexes userIndexes() {
        return new UserIndexes();
    }

    @Bean
    EmailVerificationIndexes emailVerificationIndexes() {
        return new EmailVerificationIndexes();
    }

    @Bean
    RefreshSessionIndexes refreshSessionIndexes() {
        return new RefreshSessionIndexes();
    }

    @Bean
    PasswordResetIndexes passwordResetIndexes() {
        return new PasswordResetIndexes();
    }

    @Bean
    MfaFactorIndexes mfaFactorIndexes() {
        return new MfaFactorIndexes();
    }

    @Bean
    ReauthenticationGrantIndexes reauthenticationGrantIndexes() {
        return new ReauthenticationGrantIndexes();
    }

    @Bean
    TeamIndexes teamIndexes() {
        return new TeamIndexes();
    }

    @Bean
    TeamInvitationIndexes teamInvitationIndexes() {
        return new TeamInvitationIndexes();
    }

    @Bean
    TeamFollowIndexes teamFollowIndexes() {
        return new TeamFollowIndexes();
    }

    @Bean
    CategoryTaxonomyMigration categoryTaxonomyMigration() {
        return new CategoryTaxonomyMigration();
    }

    @Bean
    StoryIndexes storyIndexes() {
        return new StoryIndexes();
    }

    @Bean
    StoryCatalogIndexes storyCatalogIndexes() {
        return new StoryCatalogIndexes();
    }

    @Bean
    ChapterIndexes chapterIndexes() {
        return new ChapterIndexes();
    }

    @Bean
    HomeReadModelIndexes homeReadModelIndexes() {
        return new HomeReadModelIndexes();
    }

    @Bean
    StoryTextSearchIndex storyTextSearchIndex() {
        return new StoryTextSearchIndex();
    }

    @Bean
    MediaAssetIndexes mediaAssetIndexes() {
        return new MediaAssetIndexes();
    }

    @Bean
    MediaProcessingIndexes mediaProcessingIndexes() {
        return new MediaProcessingIndexes();
    }

    @Bean
    StoryDraftIndexes storyDraftIndexes() {
        return new StoryDraftIndexes();
    }

    @Bean
    ChapterRevisionIndexes chapterRevisionIndexes() {
        return new ChapterRevisionIndexes();
    }

    @Bean
    PublishingReviewIndexes publishingReviewIndexes() {
        return new PublishingReviewIndexes();
    }

    @Bean
    PublishingPrecheckIndexes publishingPrecheckIndexes() {
        return new PublishingPrecheckIndexes();
    }

    @Bean
    PublishingScheduleIndexes publishingScheduleIndexes() {
        return new PublishingScheduleIndexes();
    }

    @Bean
    PublishingDueIndexes publishingDueIndexes() {
        return new PublishingDueIndexes();
    }

    @Bean
    PublishingPropagationIndexes publishingPropagationIndexes() {
        return new PublishingPropagationIndexes();
    }

    @Bean
    ModerationQueueIndexes moderationQueueIndexes() {
        return new ModerationQueueIndexes();
    }

    @Bean
    ModerationAuditIndexes moderationAuditIndexes() {
        return new ModerationAuditIndexes();
    }

    @Bean
    ReadingProgressIndexes readingProgressIndexes() {
        return new ReadingProgressIndexes();
    }

    @Bean
    ReadingHistoryIndexes readingHistoryIndexes() {
        return new ReadingHistoryIndexes();
    }

    @Bean
    ReadingSessionIndexes readingSessionIndexes() {
        return new ReadingSessionIndexes();
    }

    @Bean
    StoryRelationIndexes storyRelationIndexes() {
        return new StoryRelationIndexes();
    }

    @Bean
    CommentIndexes commentIndexes() {
        return new CommentIndexes();
    }

    @Bean
    ReactionIndexes reactionIndexes() {
        return new ReactionIndexes();
    }

    @Bean
    CommunityReportIndexes communityReportIndexes() {
        return new CommunityReportIndexes();
    }

    @Bean
    ModerationAppealIndexes moderationAppealIndexes() {
        return new ModerationAppealIndexes();
    }

    @Bean
    CopyrightCaseIndexes copyrightCaseIndexes() {
        return new CopyrightCaseIndexes();
    }

    @Bean
    NotificationInboxIndexes notificationInboxIndexes() {
        return new NotificationInboxIndexes();
    }

    @Bean
    NotificationDeliveryIndexes notificationDeliveryIndexes() {
        return new NotificationDeliveryIndexes();
    }

    @Bean
    RawReadingEventIndexes rawReadingEventIndexes() {
        return new RawReadingEventIndexes();
    }

    @Bean
    ReadingViewValidationIndexes readingViewValidationIndexes() {
        return new ReadingViewValidationIndexes();
    }

    @Bean
    TrafficFraudIndexes trafficFraudIndexes() {
        return new TrafficFraudIndexes();
    }

    @Bean
    ViewAggregateIndexes viewAggregateIndexes() {
        return new ViewAggregateIndexes();
    }

    @Bean
    TeamAnalyticsIndexes teamAnalyticsIndexes() {
        return new TeamAnalyticsIndexes();
    }

    @Bean
    LedgerIndexes ledgerIndexes() {
        return new LedgerIndexes();
    }

    @Bean
    MongoMigrationStore mongoMigrationStore(MongoTemplate mongoTemplate) {
        return new MongoMigrationStore(mongoTemplate);
    }

    @Bean
    MongoMigrationRunner mongoMigrationRunner(
            List<MongoMigration> migrations,
            MongoMigrationStore store,
            MongoTemplate mongoTemplate
    ) {
        return new MongoMigrationRunner(
                migrations,
                store,
                mongoTemplate,
                Clock.systemUTC()
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.mongodb.migrations",
            name = "enabled",
            havingValue = "true"
    )
    ApplicationRunner mongoMigrationJob(
            MongoMigrationRunner runner,
            MongoMigrationProperties properties
    ) {
        String owner = UUID.randomUUID().toString();
        return arguments -> {
            MongoMigrationRunner.MigrationRunResult result = runner.run(
                    properties.dryRun(),
                    owner,
                    properties.lockDuration()
            );
            if (result.dryRun()) {
                LOGGER.info(
                        "MongoDB migration dry-run found {} pending version(s): {}",
                        result.pending().size(),
                        versions(result.pending())
                );
            } else {
                LOGGER.info(
                        "MongoDB migration job applied {} version(s): {}",
                        result.executed().size(),
                        versions(result.executed())
                );
            }
        };
    }

    private static List<Long> versions(
            List<MongoMigrationRunner.MigrationDescriptor> migrations
    ) {
        return migrations.stream()
                .map(MongoMigrationRunner.MigrationDescriptor::version)
                .toList();
    }
}
