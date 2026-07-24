package com.storyplatform.moderation.infrastructure.persistence;

import com.storyplatform.moderation.application.CopyrightCaseOperations;
import com.storyplatform.moderation.application.port.CopyrightCaseRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class MongoCopyrightCaseRepository
        implements CopyrightCaseRepository {

    private final MongoTemplate mongo;

    public MongoCopyrightCaseRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo, "mongo");
    }

    @Override
    public boolean storyIsPublic(String storyId) {
        return mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(storyId),
                        Criteria.where("workflowStatus").is("PUBLISHED")
                )),
                "stories"
        );
    }

    @Override
    public boolean evidenceIsPrivateAndOwned(
            String claimantId,
            List<String> evidenceMediaIds
    ) {
        long count = mongo.count(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").in(evidenceMediaIds),
                        Criteria.where("ownerType").is("USER"),
                        Criteria.where("ownerId").is(claimantId),
                        Criteria.where("state").is("READY"),
                        Criteria.where("deliveryType").is("authenticated")
                )),
                "media_assets"
        );
        return count == evidenceMediaIds.size();
    }

    @Override
    public CreateResult createAndHold(
            CopyrightCaseOperations.CopyrightCaseView copyrightCase
    ) {
        try {
            mongo.insert(MongoCopyrightCaseDocument.from(copyrightCase));
        } catch (DuplicateKeyException exception) {
            return new CreateResult(Outcome.DUPLICATE, null);
        }
        var hold = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(copyrightCase.storyId()),
                        Criteria.where("workflowStatus").is("PUBLISHED"),
                        Criteria.where("copyrightCaseId").exists(false)
                )),
                new Update()
                        .set("workflowStatus", "SUSPENDED")
                        .set("copyrightCaseId", copyrightCase.id())
                        .set("copyrightHoldUntil", copyrightCase.holdUntil())
                        .set("updatedAt", copyrightCase.createdAt())
                        .inc("version", 1),
                "stories"
        );
        return hold.getModifiedCount() == 1
                ? new CreateResult(Outcome.SUCCESS, copyrightCase)
                : new CreateResult(Outcome.STORY_CHANGED, null);
    }

    @Override
    public CreateResult appeal(
            String caseId,
            String actorId,
            String statement,
            Instant appealedAt
    ) {
        MongoCopyrightCaseDocument current = mongo.findById(
                caseId,
                MongoCopyrightCaseDocument.class
        );
        if (current == null) {
            return new CreateResult(Outcome.STORY_CHANGED, null);
        }
        TeamProjection story = mongo.findById(
                current.storyId(),
                TeamProjection.class,
                "stories"
        );
        boolean member = story != null && mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("teamId").is(story.teamId()),
                        Criteria.where("userId").is(actorId),
                        Criteria.where("state").is("ACTIVE")
                )),
                "team_memberships"
        );
        if (!member) {
            return new CreateResult(Outcome.STORY_CHANGED, null);
        }
        MongoCopyrightCaseDocument appealed = mongo.findAndModify(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(caseId),
                        Criteria.where("status").is("PENDING"),
                        Criteria.where("holdUntil").gte(appealedAt),
                        Criteria.where("appealStatement").exists(false)
                )),
                new Update()
                        .set("status", "APPEALED")
                        .set("appealStatement", statement)
                        .set("appealActorId", actorId)
                        .set("appealedAt", appealedAt),
                FindAndModifyOptions.options().returnNew(true),
                MongoCopyrightCaseDocument.class
        );
        return appealed == null
                ? new CreateResult(Outcome.DUPLICATE, null)
                : new CreateResult(Outcome.SUCCESS, appealed.toView());
    }

    @Override
    public CreateResult decide(
            String caseId,
            String reviewerId,
            CopyrightCaseOperations.Decision decision,
            String reasonCode,
            String note,
            Instant decidedAt
    ) {
        String state = decision == CopyrightCaseOperations.Decision.TAKEDOWN
                ? "TAKEDOWN"
                : "REINSTATED";
        MongoCopyrightCaseDocument decided = mongo.findAndModify(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(caseId),
                        Criteria.where("status").in("PENDING", "APPEALED"),
                        Criteria.where("claimantId").ne(reviewerId),
                        Criteria.where("appealActorId").ne(reviewerId)
                )),
                new Update()
                        .set("status", state)
                        .set("decision", decision.name())
                        .set("decisionReasonCode", reasonCode)
                        .set("decisionNote", note)
                        .set("reviewerId", reviewerId)
                        .set("decidedAt", decidedAt),
                FindAndModifyOptions.options().returnNew(true),
                MongoCopyrightCaseDocument.class
        );
        if (decided == null) {
            return new CreateResult(Outcome.DUPLICATE, null);
        }
        String storyState = decision
                == CopyrightCaseOperations.Decision.TAKEDOWN
                ? "ARCHIVED"
                : "PUBLISHED";
        var story = mongo.updateFirst(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(decided.storyId()),
                        Criteria.where("workflowStatus").is("SUSPENDED"),
                        Criteria.where("copyrightCaseId").is(caseId)
                )),
                new Update()
                        .set("workflowStatus", storyState)
                        .unset("copyrightHoldUntil")
                        .set("updatedAt", decidedAt)
                        .inc("version", 1),
                "stories"
        );
        return story.getModifiedCount() == 1
                ? new CreateResult(Outcome.SUCCESS, decided.toView())
                : new CreateResult(Outcome.STORY_CHANGED, null);
    }

    public record TeamProjection(String id, String teamId) {
    }
}
