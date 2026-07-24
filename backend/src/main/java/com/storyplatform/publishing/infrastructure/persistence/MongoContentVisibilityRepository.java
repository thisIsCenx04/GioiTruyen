package com.storyplatform.publishing.infrastructure.persistence;

import com.storyplatform.publishing.application.port
        .ContentVisibilityRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class MongoContentVisibilityRepository
        implements ContentVisibilityRepository {

    private static final String STORIES = "stories";
    private static final String CHAPTERS = "chapters";
    private static final String AUDIT = "audit_logs";
    private final MongoTemplate mongo;
    private final Supplier<String> identifiers;

    public MongoContentVisibilityRepository(
            MongoTemplate mongo,
            Supplier<String> identifiers
    ) {
        this.mongo = Objects.requireNonNull(mongo);
        this.identifiers = Objects.requireNonNull(identifiers);
    }

    @Override
    public Optional<Candidate> find(String storyId) {
        Document story = mongo.findOne(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(storyId),
                        Criteria.where("workflowStatus").in(
                                "PUBLISHED",
                                "HIDDEN",
                                "SUSPENDED"
                        )
                )),
                Document.class,
                STORIES
        );
        if (story == null) {
            return Optional.empty();
        }
        Number version = story.get("version", Number.class);
        return Optional.of(new Candidate(
                storyId,
                story.getString("teamId"),
                story.getString("workflowStatus"),
                story.getString("previousWorkflowStatus"),
                version.longValue()
        ));
    }

    @Override
    public boolean change(
            Candidate candidate,
            String targetState,
            String actorId,
            String action,
            String reasonCode,
            String note,
            Instant changedAt
    ) {
        Update storyUpdate = visibility(targetState, changedAt);
        if ("SUSPEND".equals(action)) {
            storyUpdate.set(
                    "previousWorkflowStatus",
                    candidate.state()
            );
        } else if ("REINSTATE".equals(action)) {
            storyUpdate.unset("previousWorkflowStatus");
        }
        var story = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(candidate.storyId()),
                        Criteria.where("teamId").is(candidate.teamId()),
                        Criteria.where("workflowStatus").is(
                                candidate.state()
                        ),
                        Criteria.where("version").is(candidate.version())
                )),
                storyUpdate,
                STORIES
        );
        if (story.getModifiedCount() != 1) {
            return false;
        }
        mongo.updateMulti(
                Query.query(new Criteria().andOperator(
                        Criteria.where("storyId").is(candidate.storyId()),
                        Criteria.where("teamId").is(candidate.teamId()),
                        Criteria.where("workflowStatus").is(
                                candidate.state()
                        )
                )),
                visibility(targetState, changedAt),
                CHAPTERS
        );
        mongo.insert(new Document()
                .append("_id", identifiers.get())
                .append("eventType", "publishing.visibility.changed")
                .append("actorId", actorId)
                .append("targetType", "STORY")
                .append("targetId", candidate.storyId())
                .append("teamId", candidate.teamId())
                .append("action", action)
                .append("reasonCode", reasonCode)
                .append("note", note)
                .append("fromState", candidate.state())
                .append("toState", targetState)
                .append("evidenceRefs", List.of())
                .append("createdAt", changedAt), AUDIT);
        return true;
    }

    private static Update visibility(
            String state,
            Instant changedAt
    ) {
        return new Update()
                .set("workflowStatus", state)
                .set("visibilityChangedAt", changedAt)
                .set("updatedAt", changedAt)
                .inc("version", 1);
    }
}
