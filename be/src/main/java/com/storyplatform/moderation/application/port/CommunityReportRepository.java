package com.storyplatform.moderation.application.port;

import com.storyplatform.moderation.domain.CommunityReport;

import java.util.List;
import java.util.Optional;

public interface CommunityReportRepository {

    boolean targetIsVisible(
            CommunityReport.TargetType type,
            String targetId
    );

    boolean evidenceIsOwnedAndReady(
            String reporterId,
            List<String> mediaIds
    );

    int reporterTrustScore(String reporterId);

    Optional<CommunityReport> findByDedupeKey(String dedupeKey);

    SaveResult saveIfAbsent(CommunityReport report);

    record SaveResult(CommunityReport report, boolean created) {
    }
}
