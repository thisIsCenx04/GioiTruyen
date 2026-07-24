package com.storyplatform.moderation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record CommunityReport(
        String id,
        String dedupeKey,
        String reporterId,
        TargetType targetType,
        String targetId,
        Reason reason,
        String detail,
        List<String> evidenceMediaIds,
        int reporterTrustScore,
        int riskScore,
        Status status,
        Instant createdAt
) {
    public CommunityReport {
        Objects.requireNonNull(id);
        Objects.requireNonNull(dedupeKey);
        Objects.requireNonNull(reporterId);
        Objects.requireNonNull(targetType);
        Objects.requireNonNull(targetId);
        Objects.requireNonNull(reason);
        evidenceMediaIds = List.copyOf(evidenceMediaIds);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        if (reporterTrustScore < 0 || reporterTrustScore > 100
                || riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("invalid report score");
        }
    }

    public enum TargetType {
        STORY,
        CHAPTER,
        COMMENT,
        TEAM,
        USER
    }

    public enum Reason {
        COPYRIGHT,
        IMPERSONATION,
        HARASSMENT,
        SEXUAL_CONTENT,
        ILLEGAL_CONTENT,
        SPAM,
        BROKEN_CONTENT,
        OTHER
    }

    public enum Status {
        RECEIVED,
        TRIAGED,
        INVESTIGATING,
        RESOLVED,
        REJECTED,
        APPEALED
    }
}
