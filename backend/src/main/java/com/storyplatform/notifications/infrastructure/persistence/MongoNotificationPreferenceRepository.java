package com.storyplatform.notifications.infrastructure.persistence;

import com.storyplatform.notifications.application
        .NotificationPreferenceOperations;
import com.storyplatform.notifications.application.port
        .NotificationPreferenceRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public final class MongoNotificationPreferenceRepository
        implements NotificationPreferenceRepository {

    public static final String COLLECTION = "notification_preferences";
    private final MongoTemplate mongo;

    public MongoNotificationPreferenceRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<NotificationPreferenceOperations.PreferenceView> find(
            String userId
    ) {
        return Optional.ofNullable(mongo.findById(
                userId,
                PreferenceDocument.class,
                COLLECTION
        )).map(PreferenceDocument::toView);
    }

    @Override
    public SaveOutcome save(
            NotificationPreferenceOperations.PreferenceView preference,
            long expectedVersion
    ) {
        Criteria version = expectedVersion == 0
                ? new Criteria().orOperator(
                        Criteria.where("version").exists(false),
                        Criteria.where("version").is(0)
                )
                : Criteria.where("version").is(expectedVersion);
        try {
            PreferenceDocument updated = mongo.findAndModify(
                    Query.query(new Criteria().andOperator(
                            Criteria.where("_id").is(preference.userId()),
                            version
                    )),
                    new Update()
                            .setOnInsert("_id", preference.userId())
                            .set("emailEnabled", preference.emailEnabled())
                            .set("pushEnabled", preference.pushEnabled())
                            .set("categories", preference.categories())
                            .set(
                                    "consentVersion",
                                    preference.consentVersion()
                            )
                            .set("consentedAt", preference.consentedAt())
                            .set("updatedAt", preference.updatedAt())
                            .set("version", preference.version()),
                    FindAndModifyOptions.options()
                            .upsert(expectedVersion == 0)
                            .returnNew(true),
                    PreferenceDocument.class,
                    COLLECTION
            );
            return updated == null ? SaveOutcome.CONFLICT : SaveOutcome.SUCCESS;
        } catch (DuplicateKeyException exception) {
            return SaveOutcome.CONFLICT;
        }
    }

    @Override
    public SaveOutcome disable(
            String userId,
            Channel channel,
            Instant now
    ) {
        String field = channel == Channel.EMAIL
                ? "emailEnabled"
                : "pushEnabled";
        var result = mongo.updateFirst(
                Query.query(Criteria.where("_id").is(userId)),
                new Update()
                        .set(field, false)
                        .set("updatedAt", now)
                        .inc("version", 1),
                COLLECTION
        );
        if (result.getMatchedCount() == 1) {
            return SaveOutcome.SUCCESS;
        }
        return SaveOutcome.NOT_FOUND;
    }

    public record PreferenceDocument(
            @Id String userId,
            boolean emailEnabled,
            boolean pushEnabled,
            Set<String> categories,
            String consentVersion,
            Instant consentedAt,
            Instant updatedAt,
            long version
    ) {
        NotificationPreferenceOperations.PreferenceView toView() {
            return new NotificationPreferenceOperations.PreferenceView(
                    userId,
                    emailEnabled,
                    pushEnabled,
                    categories == null ? Set.of() : categories,
                    consentVersion,
                    consentedAt,
                    updatedAt,
                    version
            );
        }
    }
}
