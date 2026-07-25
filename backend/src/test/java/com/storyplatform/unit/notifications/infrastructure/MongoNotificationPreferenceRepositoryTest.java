package com.storyplatform.unit.notifications.infrastructure;

import com.mongodb.client.result.UpdateResult;
import com.storyplatform.notifications.application
        .NotificationPreferenceOperations;
import com.storyplatform.notifications.application.port
        .NotificationPreferenceRepository;
import com.storyplatform.notifications.infrastructure.persistence
        .MongoNotificationPreferenceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoNotificationPreferenceRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void findsPreferencesAndNormalizesLegacyNullCategories() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findById(
                eq("user"),
                eq(MongoNotificationPreferenceRepository
                        .PreferenceDocument.class),
                eq(MongoNotificationPreferenceRepository.COLLECTION)
        )).thenReturn(
                document(null),
                (MongoNotificationPreferenceRepository.PreferenceDocument) null
        );
        var repository = new MongoNotificationPreferenceRepository(mongo);

        assertThat(repository.find("user").orElseThrow().categories())
                .isEmpty();
        assertThat(repository.find("user")).isEmpty();
    }

    @Test
    void savesNewAndVersionedPreferencesAndDetectsConflicts() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoNotificationPreferenceRepository
                        .PreferenceDocument.class),
                eq(MongoNotificationPreferenceRepository.COLLECTION)
        )).thenReturn(
                document(Set.of("ACCOUNT")),
                (MongoNotificationPreferenceRepository.PreferenceDocument) null
        )
                .thenThrow(new DuplicateKeyException("race"));
        var repository = new MongoNotificationPreferenceRepository(mongo);
        var preference = view();

        assertThat(repository.save(preference, 0))
                .isEqualTo(NotificationPreferenceRepository
                        .SaveOutcome.SUCCESS);
        assertThat(repository.save(preference, 1))
                .isEqualTo(NotificationPreferenceRepository
                        .SaveOutcome.CONFLICT);
        assertThat(repository.save(preference, 0))
                .isEqualTo(NotificationPreferenceRepository
                        .SaveOutcome.CONFLICT);
    }

    @Test
    void disablesEitherChannelAndReportsMissingPreference() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        UpdateResult matched = mock(UpdateResult.class);
        UpdateResult missing = mock(UpdateResult.class);
        when(matched.getMatchedCount()).thenReturn(1L);
        when(missing.getMatchedCount()).thenReturn(0L);
        when(mongo.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(MongoNotificationPreferenceRepository.COLLECTION)
        )).thenReturn(matched, missing);
        var repository = new MongoNotificationPreferenceRepository(mongo);

        assertThat(repository.disable(
                "user",
                NotificationPreferenceRepository.Channel.EMAIL,
                NOW
        )).isEqualTo(NotificationPreferenceRepository.SaveOutcome.SUCCESS);
        assertThat(repository.disable(
                "user",
                NotificationPreferenceRepository.Channel.PUSH,
                NOW
        )).isEqualTo(NotificationPreferenceRepository.SaveOutcome.NOT_FOUND);
    }

    private static NotificationPreferenceOperations.PreferenceView view() {
        return new NotificationPreferenceOperations.PreferenceView(
                "user",
                true,
                false,
                Set.of("ACCOUNT"),
                "notifications-2026.1",
                NOW,
                NOW,
                1
        );
    }

    private static MongoNotificationPreferenceRepository.PreferenceDocument
    document(Set<String> categories) {
        return new MongoNotificationPreferenceRepository.PreferenceDocument(
                "user",
                true,
                false,
                categories,
                "notifications-2026.1",
                NOW,
                NOW,
                1
        );
    }
}
