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
