package com.storyplatform.moderation.application;

import com.storyplatform.moderation.domain.CommunityReport;

import java.time.Instant;
import java.util.List;

public interface CommunityReportOperations {

    ReportView create(String reporterId, CreateCommand command);

    record CreateCommand(
            CommunityReport.TargetType targetType,
            String targetId,
            CommunityReport.Reason reason,
            String detail,
            List<String> evidenceMediaIds
    ) {
        public CreateCommand {
            evidenceMediaIds = evidenceMediaIds == null
                    ? List.of() : List.copyOf(evidenceMediaIds);
        }
    }

    record ReportView(
            String id,
            CommunityReport.TargetType targetType,
            String targetId,
            CommunityReport.Reason reason,
            CommunityReport.Status status,
            int riskScore,
            boolean duplicate,
            Instant createdAt
    ) {
    }
}
