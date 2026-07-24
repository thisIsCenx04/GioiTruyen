package com.storyplatform.bootstrap.persistence.migration;

import com.storyplatform.notifications.infrastructure.persistence
        .MongoNotificationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

public final class NotificationInboxIndexes implements MongoMigration {

    private static final String CHECKSUM =
            "7a688470f6d63310db597693ec93e6070eaf4f41ade6886b0202e32460498115";

    @Override
    public long version() {
        return 39;
    }

    @Override
    public String name() {
        return "index notification inbox and unread queries";
    }

    @Override
    public String checksum() {
        return CHECKSUM;
    }

    @Override
    public void apply(MongoTemplate mongo) {
        mongo.indexOps(MongoNotificationRepository.COLLECTION)
                .createIndex(new Index()
                        .named("notification_inbox_keyset")
                        .on("recipientId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC)
                        .on("id", Sort.Direction.DESC));
        mongo.indexOps(MongoNotificationRepository.COLLECTION)
                .createIndex(new Index()
                        .named("notification_unread")
                        .on("recipientId", Sort.Direction.ASC)
                        .on("readAt", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC));
    }
}
