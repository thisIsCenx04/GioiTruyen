package com.storyplatform.moderation.infrastructure.persistence;

import com.storyplatform.moderation.application.CopyrightCaseOperations;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = MongoCopyrightCaseDocument.COLLECTION)
public record MongoCopyrightCaseDocument(
        @Id String id,
        String storyId,
        String claimantId,
        String claimantName,
        String statement,
        List<String> evidenceMediaIds,
        String status,
        Instant createdAt,
        Instant responseDueAt,
        Instant holdUntil,
        String appealStatement,
        String appealActorId,
        Instant appealedAt,
        String decision,
        String decisionReasonCode,
        String decisionNote,
        String reviewerId,
        Instant decidedAt
) {
    public static final String COLLECTION = "copyright_cases";

    public MongoCopyrightCaseDocument {
        evidenceMediaIds = List.copyOf(evidenceMediaIds);
    }

    public CopyrightCaseOperations.CopyrightCaseView toView() {
        return new CopyrightCaseOperations.CopyrightCaseView(
                id,
                storyId,
                claimantId,
                claimantName,
                statement,
                evidenceMediaIds,
                status,
                createdAt,
                responseDueAt,
                holdUntil,
                appealStatement,
                appealActorId,
                appealedAt,
                decision == null
                        ? null
                        : CopyrightCaseOperations.Decision.valueOf(decision),
                decisionReasonCode,
                decisionNote,
                reviewerId,
                decidedAt
        );
    }

    public static MongoCopyrightCaseDocument from(
            CopyrightCaseOperations.CopyrightCaseView value
    ) {
        return new MongoCopyrightCaseDocument(
                value.id(),
                value.storyId(),
                value.claimantId(),
                value.claimantName(),
                value.statement(),
                value.evidenceMediaIds(),
                value.status(),
                value.createdAt(),
                value.responseDueAt(),
                value.holdUntil(),
                value.appealStatement(),
                value.appealActorId(),
                value.appealedAt(),
                value.decision() == null ? null : value.decision().name(),
                value.decisionReasonCode(),
                value.decisionNote(),
                value.reviewerId(),
                value.decidedAt()
        );
    }
}
