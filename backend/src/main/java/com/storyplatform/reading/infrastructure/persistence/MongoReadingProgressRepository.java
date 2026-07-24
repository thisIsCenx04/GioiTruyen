package com.storyplatform.reading.infrastructure.persistence;

import com.storyplatform.reading.application.ReadingProgressOperations;
import com.storyplatform.reading.application.port.ReadingHistoryRepository;
import com.storyplatform.reading.application.port.ReadingProgressRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

public final class MongoReadingProgressRepository
        implements ReadingProgressRepository, ReadingHistoryRepository {

    public static final String COLLECTION = "reading_progress";
    private final MongoTemplate mongo;

    public MongoReadingProgressRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<StoredProgress> find(String userId, String storyId) {
        return Optional.ofNullable(mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("userId").is(userId),
                        Criteria.where("storyId").is(storyId)
                )),
                ProgressDocument.class,
                COLLECTION
        )).map(MongoReadingProgressRepository::stored);
    }

    @Override
    public boolean chapterIsPublished(
            String storyId,
            String chapterId
    ) {
        return mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(chapterId),
                        Criteria.where("storyId").is(storyId),
                        Criteria.where("workflowStatus").is("PUBLISHED")
                )),
                "chapters"
        ) && mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(storyId),
                        Criteria.where("workflowStatus").is("PUBLISHED")
                )),
                "stories"
        );
    }

    @Override
    public boolean create(
            String userId,
            ReadingProgressOperations.ProgressView progress
    ) {
        try {
            mongo.insert(document(userId, progress), COLLECTION);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public boolean update(
            String userId,
            ReadingProgressOperations.ProgressView progress,
            long expectedVersion,
            Instant previousDeviceUpdatedAt
    ) {
        var result = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("userId").is(userId),
                        Criteria.where("storyId").is(progress.storyId()),
                        Criteria.where("version").is(expectedVersion),
                        Criteria.where("deviceUpdatedAt").is(
                                previousDeviceUpdatedAt
                        )
                )),
                new Update()
                        .set("chapterId", progress.chapterId())
                        .set("position", progress.position())
                        .set(
                                "deviceUpdatedAt",
                                progress.deviceUpdatedAt()
                        )
                        .set("updatedAt", progress.updatedAt())
                        .set("version", progress.version()),
                COLLECTION
        );
        return result.getModifiedCount() == 1;
    }

    @Override
    public List<ReadingProgressOperations.ProgressView> list(
            String userId,
            Instant beforeUpdatedAt,
            String beforeStoryId,
            int limit
    ) {
        Criteria criteria = Criteria.where("userId").is(userId);
        if (beforeUpdatedAt != null) {
            criteria = new Criteria().andOperator(
                    criteria,
                    new Criteria().orOperator(
                            Criteria.where("updatedAt").lt(
                                    beforeUpdatedAt
                            ),
                            new Criteria().andOperator(
                                    Criteria.where("updatedAt").is(
                                            beforeUpdatedAt
                                    ),
                                    Criteria.where("storyId").lt(
                                            beforeStoryId
                                    )
                            )
                    )
            );
        }
        return mongo.find(
                        Query.query(criteria)
                                .with(Sort.by(
                                        Sort.Order.desc("updatedAt"),
                                        Sort.Order.desc("storyId")
                                ))
                                .limit(limit),
                        ProgressDocument.class,
                        COLLECTION
                ).stream()
                .map(MongoReadingProgressRepository::stored)
                .map(StoredProgress::progress)
                .toList();
    }

    @Override
    public void delete(String userId, String storyId) {
        mongo.remove(
                Query.query(new Criteria().andOperator(
                        Criteria.where("userId").is(userId),
                        Criteria.where("storyId").is(storyId)
                )),
                COLLECTION
        );
    }

    private static ProgressDocument document(
            String userId,
            ReadingProgressOperations.ProgressView progress
    ) {
        return new ProgressDocument(
                null,
                userId,
                progress.storyId(),
                progress.chapterId(),
                progress.position(),
                progress.deviceUpdatedAt(),
                progress.updatedAt(),
                progress.version()
        );
    }

    private static StoredProgress stored(ProgressDocument value) {
        return new StoredProgress(
                value.userId(),
                new ReadingProgressOperations.ProgressView(
                        value.storyId(),
                        value.chapterId(),
                        value.position(),
                        value.deviceUpdatedAt(),
                        value.updatedAt(),
                        value.version()
                )
        );
    }

    public record ProgressDocument(
            @Id String id,
            String userId,
            String storyId,
            String chapterId,
            double position,
            Instant deviceUpdatedAt,
            Instant updatedAt,
            long version
    ) {
    }
}
