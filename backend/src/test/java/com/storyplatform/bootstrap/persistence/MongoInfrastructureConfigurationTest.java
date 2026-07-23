package com.storyplatform.bootstrap.persistence;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.boot.mongodb.autoconfigure.MongoProperties;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class MongoInfrastructureConfigurationTest {

    private final MongoTuningProperties tuning = new MongoTuningProperties(
            3,
            40,
            5,
            Duration.ofSeconds(2),
            Duration.ofSeconds(45),
            Duration.ofMillis(750),
            Duration.ofSeconds(4),
            Duration.ofSeconds(3),
            Duration.ofSeconds(6)
    );

    @Test
    void replicaSetIsRequiredForTransactionalSafety() {
        assertThatIllegalStateException().isThrownBy(() ->
                MongoInfrastructureConfiguration.requireReplicaSet(
                        "mongodb://localhost/story_platform"
                )
        ).withMessage("MongoDB URI must declare a replicaSet");
    }

    @Test
    void replicaSetUriIsAccepted() {
        MongoInfrastructureConfiguration.requireReplicaSet(
                "mongodb://localhost/story_platform?replicaSet=rs0"
        );
    }

    @Test
    void atlasSrvUriIsAcceptedBecauseTopologyIsDiscoveredFromDns() {
        MongoInfrastructureConfiguration.requireReplicaSet(
                "mongodb+srv://reader:secret@cluster.example.net/story_platform"
        );
    }

    @Test
    void clientPolicyAppliesDurabilityTimeoutAndPoolBounds() {
        MongoProperties properties = new MongoProperties();
        properties.setUri(
                "mongodb://localhost/story_platform?replicaSet=rs0"
        );
        MongoInfrastructureConfiguration configuration =
                new MongoInfrastructureConfiguration();
        MongoClientSettingsBuilderCustomizer customizer =
                configuration.durableMongoClientPolicy(properties, tuning);
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(
                        properties.determineUri()
                ));

        customizer.customize(builder);
        MongoClientSettings settings = builder.build();

        assertThat(settings.getReadPreference())
                .isEqualTo(ReadPreference.primary());
        assertThat(settings.getReadConcern()).isEqualTo(ReadConcern.MAJORITY);
        assertThat(settings.getWriteConcern().getWObject())
                .isEqualTo(WriteConcern.MAJORITY.getWObject());
        assertThat(settings.getWriteConcern().getJournal()).isTrue();
        assertThat(settings.getRetryReads()).isTrue();
        assertThat(settings.getRetryWrites()).isTrue();
        assertThat(settings.getConnectionPoolSettings().getMinSize())
                .isEqualTo(3);
        assertThat(settings.getConnectionPoolSettings().getMaxSize())
                .isEqualTo(40);
        assertThat(settings.getConnectionPoolSettings().getMaxConnecting())
                .isEqualTo(5);
        assertThat(settings.getSocketSettings().getConnectTimeout(
                TimeUnit.MILLISECONDS
        )).isEqualTo(750);
        assertThat(settings.getClusterSettings().getServerSelectionTimeout(
                TimeUnit.MILLISECONDS
        )).isEqualTo(3_000);
    }
}
