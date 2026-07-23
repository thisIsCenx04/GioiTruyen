package com.storyplatform.bootstrap.persistence;

import com.mongodb.ConnectionString;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MongoTuningProperties.class)
@EnableTransactionManagement
public class MongoInfrastructureConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    MongoClientSettingsBuilderCustomizer durableMongoClientPolicy(
            MongoProperties mongo,
            MongoTuningProperties tuning
    ) {
        requireReplicaSet(mongo.determineUri());
        WriteConcern durableWrites = durableWriteConcern(tuning);

        return settings -> settings
                .readPreference(ReadPreference.primary())
                .readConcern(ReadConcern.MAJORITY)
                .writeConcern(durableWrites)
                .retryReads(true)
                .retryWrites(true)
                .applyToConnectionPoolSettings(pool -> pool
                        .minSize(tuning.minPoolSize())
                        .maxSize(tuning.maxPoolSize())
                        .maxConnecting(tuning.maxConnecting())
                        .maxWaitTime(
                                tuning.maxWaitTime().toMillis(),
                                TimeUnit.MILLISECONDS
                        )
                        .maxConnectionIdleTime(
                                tuning.maxIdleTime().toMillis(),
                                TimeUnit.MILLISECONDS
                        ))
                .applyToSocketSettings(socket -> socket
                        .connectTimeout(
                                tuning.connectTimeout().toMillis(),
                                TimeUnit.MILLISECONDS
                        )
                        .readTimeout(
                                tuning.readTimeout().toMillis(),
                                TimeUnit.MILLISECONDS
                        ))
                .applyToClusterSettings(cluster -> cluster
                        .serverSelectionTimeout(
                                tuning.serverSelectionTimeout().toMillis(),
                                TimeUnit.MILLISECONDS
                        ));
    }

    @Bean
    MongoTransactionManager mongoTransactionManager(
            MongoDatabaseFactory databaseFactory,
            MongoTuningProperties tuning
    ) {
        TransactionOptions transactionOptions = TransactionOptions.builder()
                .readConcern(ReadConcern.SNAPSHOT)
                .readPreference(ReadPreference.primary())
                .writeConcern(durableWriteConcern(tuning))
                .build();
        return new MongoTransactionManager(databaseFactory, transactionOptions);
    }

    static void requireReplicaSet(String uri) {
        String replicaSet = new ConnectionString(uri).getRequiredReplicaSetName();
        if (replicaSet == null || replicaSet.isBlank()) {
            throw new IllegalStateException(
                    "MongoDB URI must declare a replicaSet"
            );
        }
    }

    private static WriteConcern durableWriteConcern(
            MongoTuningProperties tuning
    ) {
        return WriteConcern.MAJORITY
                .withJournal(true)
                .withWTimeout(
                        tuning.writeTimeout().toMillis(),
                        TimeUnit.MILLISECONDS
                );
    }
}
