package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.notifications.infrastructure.persistence
        .MongoNotificationDeliveryRepository;
import com.storyplatform.notifications.infrastructure.persistence
        .MongoNotificationPreferenceRepository;
import com.storyplatform.notifications.infrastructure.persistence
        .MongoPushSubscriptionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class NotificationDeliveryIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "18d09532727740b2ee2d1ff0b05f47576d8024b332655737a4cda930da89a9df";

    @Override
    public long version() {
        return 40;
    }

    @Override
    public String name() {
        return "index notification preferences delivery retry and dlq";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoNotificationDeliveryRepository.COLLECTION)
                .createIndex(new Index()
                        .named("notification_delivery_claim")
                        .on("state", Sort.Direction.ASC)
                        .on("nextAttemptAt", Sort.Direction.ASC)
                        .on("leaseUntil", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.ASC));
        mongo.indexOps(MongoNotificationDeliveryRepository.COLLECTION)
                .createIndex(new Index()
                        .named("notification_delivery_recipient")
                        .on("recipientId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));
        mongo.indexOps(MongoNotificationPreferenceRepository.COLLECTION)
                .createIndex(new Index()
                        .named("notification_consent_audit")
                        .on("consentVersion", Sort.Direction.ASC)
                        .on("consentedAt", Sort.Direction.DESC));
        mongo.indexOps(MongoPushSubscriptionRepository.COLLECTION)
                .createIndex(new Index()
                        .named("push_subscription_delivery")
                        .on("userId", Sort.Direction.ASC)
                        .on("active", Sort.Direction.ASC)
                        .on("updatedAt", Sort.Direction.DESC));
    }
}
