package com.storyplatform.catalog.infrastructure.persistence;

import com.storyplatform.catalog.application.PublicChapterProjection;
import com.storyplatform.catalog.application.port.ChapterRepository;
import com.storyplatform.catalog.domain.Chapter;
import org.springframework.data.domain.Sort;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class MongoChapterRepository implements ChapterRepository {

    private final MongoTemplate mongo;

    public MongoChapterRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public List<PublicChapterProjection> findPublished(
            ChapterListQuery request
    ) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("storyId").is(request.storyId()));
        filters.add(Criteria.where("workflowStatus").is(
                Chapter.WorkflowStatus.PUBLISHED
        ));
        if (request.afterNumber() != null) {
            filters.add(new Criteria().orOperator(
                    Criteria.where("number").gt(request.afterNumber()),
                    new Criteria().andOperator(
                            Criteria.where("number")
                                    .is(request.afterNumber()),
                            Criteria.where("_id").gt(request.afterId())
                    )
            ));
        }
        Query query = Query.query(new Criteria().andOperator(
                        filters.toArray(Criteria[]::new)
                ))
                .with(Sort.by(
                        Sort.Order.asc("number"),
                        Sort.Order.asc("_id")
                ))
                .limit(request.limit());
        query.fields().include(
                "_id",
                "storyId",
                "number",
                "slug",
                "title",
                "publishedAt",
                "version"
        );
        return mongo.find(
                query,
                PublicChapterProjection.class,
                MongoChapterDocument.COLLECTION
        );
    }

    @Override
    public Optional<ChapterRepository.StoredChapter> findPublishedDetail(
            String chapterId
    ) {
        ChapterDetailDocument chapter = mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(chapterId),
                        Criteria.where("workflowStatus").is(
                                Chapter.WorkflowStatus.PUBLISHED
                        )
                )),
                ChapterDetailDocument.class,
                MongoChapterDocument.COLLECTION
        );
        if (chapter == null) {
            return Optional.empty();
        }
        ChapterRevisionDocument revision = mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(chapter.currentRevision()),
                        Criteria.where("chapterId").is(chapter.id())
                )),
                ChapterRevisionDocument.class,
                "chapter_revisions"
        );
        if (revision == null) {
            return Optional.empty();
        }
        return Optional.of(new ChapterRepository.StoredChapter(
                projection(chapter),
                revision.id(),
                revision.revisionNo(),
                revision.contentHtml(),
                revision.plainText(),
                revision.checksum()
        ));
    }

    @Override
    public Optional<PublicChapterProjection> previous(
            String storyId,
            int number,
            String chapterId
    ) {
        return neighbor(
                storyId,
                new Criteria().orOperator(
                        Criteria.where("number").lt(number),
                        new Criteria().andOperator(
                                Criteria.where("number").is(number),
                                Criteria.where("_id").lt(chapterId)
                        )
                ),
                Sort.Direction.DESC
        );
    }

    @Override
    public Optional<PublicChapterProjection> next(
            String storyId,
            int number,
            String chapterId
    ) {
        return neighbor(
                storyId,
                new Criteria().orOperator(
                        Criteria.where("number").gt(number),
                        new Criteria().andOperator(
                                Criteria.where("number").is(number),
                                Criteria.where("_id").gt(chapterId)
                        )
                ),
                Sort.Direction.ASC
        );
    }

    private Optional<PublicChapterProjection> neighbor(
            String storyId,
            Criteria position,
            Sort.Direction direction
    ) {
        Query query = Query.query(new Criteria().andOperator(
                        Criteria.where("storyId").is(storyId),
                        Criteria.where("workflowStatus").is(
                                Chapter.WorkflowStatus.PUBLISHED
                        ),
                        position
                ))
                .with(Sort.by(
                        new Sort.Order(direction, "number"),
                        new Sort.Order(direction, "_id")
                ))
                .limit(1);
        return Optional.ofNullable(mongo.findOne(
                query,
                PublicChapterProjection.class,
                MongoChapterDocument.COLLECTION
        ));
    }

    private static PublicChapterProjection projection(
            ChapterDetailDocument chapter
    ) {
        return new PublicChapterProjection(
                chapter.id(),
                chapter.storyId(),
                chapter.number(),
                chapter.slug(),
                chapter.title(),
                chapter.publishedAt(),
                chapter.version()
        );
    }

    public record ChapterDetailDocument(
            @Id String id,
            String storyId,
            int number,
            String slug,
            String title,
            String currentRevision,
            java.time.Instant publishedAt,
            long version
    ) {
    }

    public record ChapterRevisionDocument(
            @Id String id,
            String chapterId,
            long revisionNo,
            String contentHtml,
            String plainText,
            String checksum
    ) {
    }
}
