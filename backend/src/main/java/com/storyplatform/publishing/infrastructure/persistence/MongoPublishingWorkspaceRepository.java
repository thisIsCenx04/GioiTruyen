package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.application
        .PublishingWorkspaceOperations;
import com.storyplatform.publishing.application.port
        .PublishingWorkspaceRepository;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MongoPublishingWorkspaceRepository
        implements PublishingWorkspaceRepository {

    private static final String STORIES = "stories";
    private static final String CHAPTERS = "chapters";
    private static final String REVISIONS = "chapter_revisions";
    private final MongoTemplate mongo;

    public MongoPublishingWorkspaceRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public List<PublishingWorkspaceOperations.StorySummary> stories(
            String teamId
    ) {
        return mongo.find(
                        Query.query(Criteria.where("teamId").is(teamId)
                                        .and("workflowStatus").ne("ARCHIVED"))
                                .with(Sort.by(
                                        Sort.Order.desc("updatedAt"),
                                        Sort.Order.asc("_id")
                                )),
                        WorkspaceStory.class,
                        STORIES
                ).stream()
                .map(MongoPublishingWorkspaceRepository::story)
                .toList();
    }

    @Override
    public Optional<PublishingWorkspaceOperations.StorySummary> story(
            String teamId,
            String storyId
    ) {
        WorkspaceStory value = mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(storyId),
                        Criteria.where("teamId").is(teamId),
                        Criteria.where("workflowStatus").ne("ARCHIVED")
                )),
                WorkspaceStory.class,
                STORIES
        );
        return Optional.ofNullable(value)
                .map(MongoPublishingWorkspaceRepository::story);
    }

    @Override
    public List<PublishingWorkspaceOperations.ChapterEditor> chapters(
            String teamId,
            String storyId
    ) {
        List<WorkspaceChapter> chapters = mongo.find(
                Query.query(new Criteria().andOperator(
                                Criteria.where("teamId").is(teamId),
                                Criteria.where("storyId").is(storyId),
                                Criteria.where("workflowStatus").ne(
                                        "ARCHIVED"
                                )
                        ))
                        .with(Sort.by(
                                Sort.Order.asc("number"),
                                Sort.Order.asc("_id")
                        )),
                WorkspaceChapter.class,
                CHAPTERS
        );
        List<String> revisionIds = chapters.stream()
                .map(WorkspaceChapter::currentRevision)
                .toList();
        Map<String, WorkspaceRevision> revisions = revisionIds.isEmpty()
                ? Map.of()
                : mongo.find(
                                Query.query(Criteria.where("_id").in(
                                        revisionIds
                                )),
                                WorkspaceRevision.class,
                                REVISIONS
                        ).stream()
                        .collect(Collectors.toMap(
                                WorkspaceRevision::id,
                                Function.identity()
                        ));
        return chapters.stream()
                .map(value -> chapter(
                        value,
                        revisions.get(value.currentRevision())
                ))
                .toList();
    }

    private static PublishingWorkspaceOperations.StorySummary story(
            WorkspaceStory value
    ) {
        return new PublishingWorkspaceOperations.StorySummary(
                value.id(),
                value.teamId(),
                value.slug(),
                value.title(),
                value.synopsis(),
                value.origin(),
                value.language(),
                value.categoryIds(),
                value.coverAssetId(),
                value.completionStatus(),
                value.workflowStatus(),
                value.currentRevision(),
                Math.max(1, value.currentRevisionNo()),
                value.version(),
                value.updatedAt()
        );
    }

    private static PublishingWorkspaceOperations.ChapterEditor chapter(
            WorkspaceChapter value,
            WorkspaceRevision revision
    ) {
        String content = revision == null ? "" : revision.contentHtml();
        String plainText = revision == null ? "" : revision.plainText();
        int words = plainText.isBlank()
                ? 0
                : plainText.trim().split("\\s+").length;
        return new PublishingWorkspaceOperations.ChapterEditor(
                value.id(),
                value.storyId(),
                value.teamId(),
                value.number(),
                value.slug(),
                value.title(),
                value.workflowStatus(),
                value.currentRevision(),
                Math.max(1, value.currentRevisionNo()),
                value.version(),
                content,
                words,
                value.updatedAt()
        );
    }

    public record WorkspaceStory(
            @Id String id,
            String teamId,
            String slug,
            String title,
            String synopsis,
            String origin,
            String language,
            List<String> categoryIds,
            String coverAssetId,
            String completionStatus,
            String workflowStatus,
            String currentRevision,
            long currentRevisionNo,
            long version,
            Instant updatedAt
    ) {
        public WorkspaceStory {
            categoryIds = categoryIds == null
                    ? List.of()
                    : List.copyOf(categoryIds);
        }
    }

    public record WorkspaceChapter(
            @Id String id,
            String storyId,
            String teamId,
            int number,
            String slug,
            String title,
            String workflowStatus,
            String currentRevision,
            long currentRevisionNo,
            long version,
            Instant updatedAt
    ) {
    }

    public record WorkspaceRevision(
            @Id String id,
            String contentHtml,
            String plainText
    ) {
    }
}
