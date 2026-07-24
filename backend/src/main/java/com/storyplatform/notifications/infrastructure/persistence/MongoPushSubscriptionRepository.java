package com.storyplatform.notifications.infrastructure.persistence;

import com.storyplatform.notifications.application.PushSubscriptionOperations;
import com.storyplatform.notifications.application.port
        .PushSubscriptionRepository;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

public final class MongoPushSubscriptionRepository
        implements PushSubscriptionRepository {

    public static final String COLLECTION =
            "notification_push_subscriptions";
    private final MongoTemplate mongo;

    public MongoPushSubscriptionRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public PushSubscriptionOperations.SubscriptionView save(
            String id,
            String userId,
            String endpoint,
            String p256dh,
            String auth,
            Instant now
    ) {
        String mongoId = userId + ":" + sha256(endpoint);
        SubscriptionDocument existing = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(mongoId)),
                new Update()
                        .setOnInsert("_id", mongoId)
                        .setOnInsert("id", id)
                        .setOnInsert("userId", userId)
                        .set("endpoint", endpoint)
                        .set("p256dh", p256dh)
                        .set("auth", auth)
                        .set("active", true)
                        .setOnInsert("createdAt", now)
                        .set("updatedAt", now),
                FindAndModifyOptions.options().upsert(true),
                SubscriptionDocument.class,
                COLLECTION
        );
        return existing == null
                ? new PushSubscriptionOperations.SubscriptionView(id, now, now)
                : new PushSubscriptionOperations.SubscriptionView(
                        existing.id(),
                        existing.createdAt(),
                        now
                );
    }

    @Override
    public boolean remove(String id, String userId, Instant now) {
        var result = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("id").is(id),
                        Criteria.where("userId").is(userId),
                        Criteria.where("active").is(true)
                )),
                new Update()
                        .set("active", false)
                        .set("updatedAt", now),
                COLLECTION
        );
        return result.getModifiedCount() == 1;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record SubscriptionDocument(
            @Id String mongoId,
            String id,
            String userId,
            String endpoint,
            String p256dh,
            String auth,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
