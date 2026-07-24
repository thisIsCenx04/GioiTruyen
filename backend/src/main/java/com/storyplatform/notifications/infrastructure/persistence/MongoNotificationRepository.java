package com.storyplatform.notifications.infrastructure.persistence;

import com.storyplatform.notifications.application.NotificationOperations;
import com.storyplatform.notifications.application.port
        .NotificationCursorCodec;
import com.storyplatform.notifications.application.port
        .NotificationRepository;
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

public final class MongoNotificationRepository
        implements NotificationRepository {

    public static final String COLLECTION = "notifications";
    public static final String STATE_COLLECTION = "notification_states";
    private final MongoTemplate mongo;

    public MongoNotificationRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public Slice list(
            String recipientId,
            int limit,
            NotificationCursorCodec.Position after
    ) {
        Instant watermark = watermark(recipientId);
        Criteria criteria = Criteria.where("recipientId").is(recipientId);
        if (after != null) {
            criteria = new Criteria().andOperator(
                    criteria,
                    new Criteria().orOperator(
                            Criteria.where("createdAt").lt(after.createdAt()),
                            new Criteria().andOperator(
                                    Criteria.where("createdAt").is(
                                            after.createdAt()
                                    ),
                                    Criteria.where("id").lt(after.id())
                            )
                    )
            );
        }
        List<NotificationDocument> documents = mongo.find(
                Query.query(criteria)
                        .with(Sort.by(
                                Sort.Order.desc("createdAt"),
                                Sort.Order.desc("id")
                        ))
                        .limit(limit + 1),
                NotificationDocument.class,
                COLLECTION
        );
        boolean hasMore = documents.size() > limit;
        return new Slice(
                documents.stream()
                        .limit(limit)
                        .map(value -> view(value, watermark))
                        .toList(),
                hasMore
        );
    }

    @Override
    public Optional<NotificationOperations.NotificationView> markRead(
            String recipientId,
            String notificationId,
            Instant now
    ) {
        Query unread = Query.query(new Criteria().andOperator(
                Criteria.where("id").is(notificationId),
                Criteria.where("recipientId").is(recipientId),
                Criteria.where("readAt").is(null)
        ));
        NotificationDocument updated = mongo.findAndModify(
                unread,
                new Update().set("readAt", now),
                FindAndModifyOptions.options().returnNew(true),
                NotificationDocument.class,
                COLLECTION
        );
        if (updated == null) {
            updated = mongo.findOne(
                    Query.query(new Criteria().andOperator(
                            Criteria.where("id").is(notificationId),
                            Criteria.where("recipientId").is(recipientId)
                    )),
                    NotificationDocument.class,
                    COLLECTION
            );
        }
        Instant watermark = watermark(recipientId);
        return Optional.ofNullable(updated)
                .map(value -> view(value, watermark));
    }

    @Override
    public Instant markAllRead(String recipientId, Instant now) {
        NotificationState state = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(recipientId)),
                new Update()
                        .setOnInsert("_id", recipientId)
                        .max("readBefore", now),
                FindAndModifyOptions.options()
                        .upsert(true)
                        .returnNew(true),
                NotificationState.class,
                STATE_COLLECTION
        );
        return state == null ? now : state.readBefore();
    }

    @Override
    public long unreadCount(String recipientId) {
        Instant watermark = watermark(recipientId);
        Criteria criteria = new Criteria().andOperator(
                Criteria.where("recipientId").is(recipientId),
                Criteria.where("readAt").is(null)
        );
        if (watermark != null) {
            criteria = new Criteria().andOperator(
                    criteria,
                    Criteria.where("createdAt").gt(watermark)
            );
        }
        return mongo.count(Query.query(criteria), COLLECTION);
    }

    @Override
    public SaveResult saveIfAbsent(
            String id,
            String recipientId,
            String eventKey,
            String type,
            String title,
            String body,
            Map<String, String> data,
            Instant createdAt
    ) {
        String mongoId = recipientId + ":" + eventKey;
        NotificationDocument existing = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(mongoId)),
                new Update()
                        .setOnInsert("_id", mongoId)
                        .setOnInsert("id", id)
                        .setOnInsert("recipientId", recipientId)
                        .setOnInsert("eventKey", eventKey)
                        .setOnInsert("type", type)
                        .setOnInsert("title", title)
                        .setOnInsert("body", body)
                        .setOnInsert("data", data)
                        .setOnInsert("createdAt", createdAt),
                FindAndModifyOptions.options().upsert(true),
                NotificationDocument.class,
                COLLECTION
        );
        NotificationDocument stored = existing == null
                ? new NotificationDocument(
                        mongoId,
                        id,
                        recipientId,
                        eventKey,
                        type,
                        title,
                        body,
                        data,
                        null,
                        createdAt
                )
                : existing;
        return new SaveResult(
                view(stored, watermark(recipientId)),
                existing == null
        );
    }

    private Instant watermark(String recipientId) {
        NotificationState state = mongo.findById(
                recipientId,
                NotificationState.class,
                STATE_COLLECTION
        );
        return state == null ? null : state.readBefore();
    }

    private static NotificationOperations.NotificationView view(
            NotificationDocument value,
            Instant watermark
    ) {
        Instant readAt = value.readAt();
        if (readAt == null
                && watermark != null
                && !value.createdAt().isAfter(watermark)) {
            readAt = watermark;
        }
        return new NotificationOperations.NotificationView(
                value.id(),
                value.type(),
                value.title(),
                value.body(),
                value.data() == null ? Map.of() : value.data(),
                readAt,
                value.createdAt()
        );
    }

    public record NotificationDocument(
            @Id String mongoId,
            String id,
            String recipientId,
            String eventKey,
            String type,
            String title,
            String body,
            Map<String, String> data,
            Instant readAt,
            Instant createdAt
    ) {
    }

    public record NotificationState(
            @Id String recipientId,
            Instant readBefore
    ) {
    }
}
