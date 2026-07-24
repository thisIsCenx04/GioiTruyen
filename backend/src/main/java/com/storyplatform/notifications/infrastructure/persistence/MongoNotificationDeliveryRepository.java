package com.storyplatform.notifications.infrastructure.persistence;

import com.storyplatform.notifications.application.port
        .NotificationDeliveryRepository;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MongoNotificationDeliveryRepository
        implements NotificationDeliveryRepository {

    public static final String COLLECTION = "notification_deliveries";
    private final MongoTemplate mongo;

    public MongoNotificationDeliveryRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    static void enqueue(
            MongoTemplate mongo,
            MongoNotificationRepository.NotificationDocument notification
    ) {
        String category = category(notification.type());
        List<DeliveryDocument> jobs = java.util.Arrays.stream(
                        Channel.values()
                )
                .map(channel -> new DeliveryDocument(
                        notification.id() + ":" + channel.name(),
                        notification.id(),
                        notification.recipientId(),
                        channel.name(),
                        category,
                        notification.type(),
                        notification.title(),
                        notification.body(),
                        notification.data(),
                        "PENDING",
                        0,
                        notification.createdAt(),
                        notification.createdAt(),
                        null,
                        null,
                        null,
                        null,
                        null
                ))
                .toList();
        mongo.insert(jobs, COLLECTION);
    }

    @Override
    public Optional<DeliveryJob> claim(
            String workerId,
            Instant now,
            Instant leaseUntil,
            int maximumAttempts
    ) {
        DeliveryDocument claimed = mongo.findAndModify(
                Query.query(new Criteria().andOperator(
                                Criteria.where("state").in(
                                        "PENDING",
                                        "RETRY",
                                        "PROCESSING"
                                ),
                                Criteria.where("attempts").lt(maximumAttempts),
                                new Criteria().orOperator(
                                        Criteria.where("nextAttemptAt").lte(now),
                                        Criteria.where("leaseUntil").lte(now)
                                )
                        ))
                        .with(Sort.by(
                                Sort.Order.asc("nextAttemptAt"),
                                Sort.Order.asc("createdAt"),
                                Sort.Order.asc("_id")
                        )),
                new Update()
                        .set("state", "PROCESSING")
                        .set("leaseOwner", workerId)
                        .set("leaseUntil", leaseUntil)
                        .inc("attempts", 1),
                FindAndModifyOptions.options().returnNew(true),
                DeliveryDocument.class,
                COLLECTION
        );
        return Optional.ofNullable(claimed).map(
                MongoNotificationDeliveryRepository::job
        );
    }

    @Override
    public Optional<DeliveryTarget> target(DeliveryJob job) {
        MongoNotificationPreferenceRepository.PreferenceDocument preference =
                mongo.findById(
                        job.recipientId(),
                        MongoNotificationPreferenceRepository
                                .PreferenceDocument.class,
                        MongoNotificationPreferenceRepository.COLLECTION
                );
        if (preference == null
                || preference.consentedAt() == null
                || preference.categories() == null
                || !preference.categories().contains(job.category())) {
            return Optional.empty();
        }
        if (job.channel() == Channel.EMAIL) {
            if (!preference.emailEnabled()) {
                return Optional.empty();
            }
            EmailProjection user = mongo.findById(
                    job.recipientId(),
                    EmailProjection.class,
                    "users"
            );
            return user == null || !user.emailVerified()
                    || user.email() == null
                    ? Optional.empty()
                    : Optional.of(new DeliveryTarget(user.email()));
        }
        if (!preference.pushEnabled()) {
            return Optional.empty();
        }
        PushProjection push = mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("userId").is(job.recipientId()),
                        Criteria.where("active").is(true)
                )).with(Sort.by(Sort.Order.desc("updatedAt"))),
                PushProjection.class,
                "notification_push_subscriptions"
        );
        return push == null
                || push.p256dh() == null
                || push.auth() == null
                ? Optional.empty()
                : Optional.of(new DeliveryTarget(
                        push.endpoint(),
                        Map.of(
                                "p256dh", push.p256dh(),
                                "auth", push.auth()
                        )
                ));
    }

    @Override
    public boolean complete(
            DeliveryJob job,
            String workerId,
            String providerMessageId,
            Instant now
    ) {
        return finish(
                job,
                workerId,
                new Update()
                        .set("state", "DELIVERED")
                        .set("providerMessageId", providerMessageId)
                        .set("deliveredAt", now)
                        .unset("leaseOwner")
                        .unset("leaseUntil"),
                now
        );
    }

    @Override
    public boolean suppress(
            DeliveryJob job,
            String workerId,
            String reason,
            Instant now
    ) {
        return finish(
                job,
                workerId,
                new Update()
                        .set("state", "SUPPRESSED")
                        .set("failureCode", reason)
                        .set("completedAt", now)
                        .unset("leaseOwner")
                        .unset("leaseUntil"),
                now
        );
    }

    @Override
    public boolean reschedule(
            DeliveryJob job,
            String workerId,
            String failureCode,
            Instant nextAttemptAt,
            boolean deadLetter,
            Instant now
    ) {
        Update update = new Update()
                .set("state", deadLetter ? "DEAD_LETTER" : "RETRY")
                .set("failureCode", failureCode)
                .set("updatedAt", now)
                .unset("leaseOwner")
                .unset("leaseUntil");
        if (deadLetter) {
            update.set("deadLetteredAt", now).unset("nextAttemptAt");
        } else {
            update.set("nextAttemptAt", nextAttemptAt);
        }
        return finish(job, workerId, update, now);
    }

    private boolean finish(
            DeliveryJob job,
            String workerId,
            Update update,
            Instant now
    ) {
        update.set("updatedAt", now);
        var result = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(job.id()),
                        Criteria.where("state").is("PROCESSING"),
                        Criteria.where("leaseOwner").is(workerId)
                )),
                update,
                COLLECTION
        );
        return result.getModifiedCount() == 1;
    }

    private static DeliveryJob job(DeliveryDocument value) {
        return new DeliveryJob(
                value.id(),
                value.notificationId(),
                value.recipientId(),
                Channel.valueOf(value.channel()),
                value.category(),
                value.type(),
                value.title(),
                value.body(),
                value.data() == null ? Map.of() : value.data(),
                value.attempts()
        );
    }

    private static String category(String type) {
        if (type.startsWith("COMMENT_") || type.startsWith("REACTION_")) {
            return "COMMUNITY";
        }
        if (type.startsWith("MODERATION_")
                || type.startsWith("COPYRIGHT_")) {
            return "MODERATION";
        }
        if (type.startsWith("ACCOUNT_") || type.startsWith("SECURITY_")) {
            return "ACCOUNT";
        }
        return "STORY_UPDATES";
    }

    public record DeliveryDocument(
            @Id String id,
            String notificationId,
            String recipientId,
            String channel,
            String category,
            String type,
            String title,
            String body,
            Map<String, String> data,
            String state,
            int attempts,
            Instant nextAttemptAt,
            Instant createdAt,
            String leaseOwner,
            Instant leaseUntil,
            String failureCode,
            String providerMessageId,
            Instant deliveredAt
    ) {
    }

    public record EmailProjection(
            String id,
            String email,
            boolean emailVerified
    ) {
    }

    public record PushProjection(
            String id,
            String endpoint,
            String p256dh,
            String auth,
            Instant updatedAt
    ) {
    }
}
