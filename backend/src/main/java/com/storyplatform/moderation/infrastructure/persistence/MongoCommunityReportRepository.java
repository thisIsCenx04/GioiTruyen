package com.storyplatform.moderation.infrastructure.persistence;

import com.storyplatform.media.infrastructure.persistence
        .MongoMediaAssetDocument;
import com.storyplatform.moderation.application.port
        .CommunityReportRepository;
import com.storyplatform.moderation.domain.CommunityReport;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MongoCommunityReportRepository
        implements CommunityReportRepository {

    public static final String COLLECTION = "reports";
    private final MongoTemplate mongo;

    public MongoCommunityReportRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public boolean targetIsVisible(
            CommunityReport.TargetType type,
            String targetId
    ) {
        return switch (type) {
            case STORY -> published(targetId, "stories");
            case CHAPTER -> published(targetId, "chapters");
            case COMMENT -> mongo.exists(
                    Query.query(new Criteria().andOperator(
                            Criteria.where("_id").is(targetId),
                            Criteria.where("status").is("VISIBLE")
                    )),
                    "comments"
            );
            case TEAM -> mongo.exists(
                    Query.query(new Criteria().andOperator(
                            Criteria.where("_id").is(targetId),
                            Criteria.where("state").is("ACTIVE")
                    )),
                    "teams"
            );
            case USER -> mongo.exists(
                    Query.query(Criteria.where("_id").is(targetId)),
                    "user_profiles"
            );
        };
    }

    @Override
    public boolean evidenceIsOwnedAndReady(
            String reporterId,
            List<String> mediaIds
    ) {
        if (mediaIds.isEmpty()) {
            return true;
        }
        return mongo.count(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").in(mediaIds),
                        Criteria.where("ownerType").is("USER"),
                        Criteria.where("ownerId").is(reporterId),
                        Criteria.where("state").is("READY")
                )),
                MongoMediaAssetDocument.COLLECTION
        ) == mediaIds.size();
    }

    @Override
    public int reporterTrustScore(String reporterId) {
        TrustProjection profile = mongo.findById(
                reporterId,
                TrustProjection.class,
                "user_profiles"
        );
        return profile == null || profile.trustScore() == null
                ? 50 : profile.trustScore();
    }

    @Override
    public Optional<CommunityReport> findByDedupeKey(String dedupeKey) {
        return Optional.ofNullable(mongo.findById(
                dedupeKey,
                ReportDocument.class,
                COLLECTION
        )).map(ReportDocument::toDomain);
    }

    @Override
    public SaveResult saveIfAbsent(CommunityReport report) {
        ReportDocument existing = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(report.dedupeKey())),
                insert(report),
                FindAndModifyOptions.options()
                        .upsert(true)
                        .returnNew(false),
                ReportDocument.class,
                COLLECTION
        );
        return existing == null
                ? new SaveResult(report, true)
                : new SaveResult(existing.toDomain(), false);
    }

    private boolean published(String targetId, String collection) {
        return mongo.exists(
                Query.query(new Criteria().andOperator(
                        Criteria.where("_id").is(targetId),
                        Criteria.where("workflowStatus").is("PUBLISHED")
                )),
                collection
        );
    }

    private static Update insert(CommunityReport report) {
        return new Update()
                .setOnInsert("_id", report.dedupeKey())
                .setOnInsert("id", report.id())
                .setOnInsert("reporterId", report.reporterId())
                .setOnInsert("targetType", report.targetType().name())
                .setOnInsert("targetId", report.targetId())
                .setOnInsert("reason", report.reason().name())
                .setOnInsert("detail", report.detail())
                .setOnInsert(
                        "evidenceMediaIds",
                        report.evidenceMediaIds()
                )
                .setOnInsert(
                        "reporterTrustScore",
                        report.reporterTrustScore()
                )
                .setOnInsert("riskScore", report.riskScore())
                .setOnInsert("status", report.status().name())
                .setOnInsert("createdAt", report.createdAt());
    }

    public record ReportDocument(
            @Id String mongoId,
            String id,
            String reporterId,
            String targetType,
            String targetId,
            String reason,
            String detail,
            List<String> evidenceMediaIds,
            int reporterTrustScore,
            int riskScore,
            String status,
            Instant createdAt
    ) {
        CommunityReport toDomain() {
            return new CommunityReport(
                    id,
                    mongoId,
                    reporterId,
                    CommunityReport.TargetType.valueOf(targetType),
                    targetId,
                    CommunityReport.Reason.valueOf(reason),
                    detail,
                    evidenceMediaIds == null ? List.of() : evidenceMediaIds,
                    reporterTrustScore,
                    riskScore,
                    CommunityReport.Status.valueOf(status),
                    createdAt
            );
        }
    }

    public record TrustProjection(String id, Integer trustScore) {
    }
}
