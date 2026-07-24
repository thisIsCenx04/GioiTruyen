package com.storyplatform.unit.moderation.infrastructure;

import com.storyplatform.moderation.domain.CommunityReport;
import com.storyplatform.moderation.infrastructure.persistence
        .MongoCommunityReportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoCommunityReportRepositoryTest {

    @Test
    void atomicallyDeduplicatesAndChecksOwnedReadyEvidence() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoCommunityReportRepository.ReportDocument.class),
                eq(MongoCommunityReportRepository.COLLECTION)
        )).thenReturn(null);
        when(mongo.count(
                any(Query.class),
                eq("media_assets")
        )).thenReturn(1L);
        MongoCommunityReportRepository repository =
                new MongoCommunityReportRepository(mongo);
        CommunityReport report = report();

        assertThat(repository.evidenceIsOwnedAndReady(
                "reporter",
                report.evidenceMediaIds()
        )).isTrue();
        assertThat(repository.saveIfAbsent(report))
                .extracting(result -> result.created())
                .isEqualTo(true);
        verify(mongo).findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoCommunityReportRepository.ReportDocument.class),
                eq(MongoCommunityReportRepository.COLLECTION)
        );
    }

    @Test
    void validatesEveryVisibleTargetAndMapsExistingSnapshot() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.exists(any(Query.class), eq("stories")))
                .thenReturn(true);
        when(mongo.exists(any(Query.class), eq("chapters")))
                .thenReturn(true);
        when(mongo.exists(any(Query.class), eq("comments")))
                .thenReturn(true);
        when(mongo.exists(any(Query.class), eq("teams")))
                .thenReturn(true);
        when(mongo.exists(any(Query.class), eq("user_profiles")))
                .thenReturn(true);
        CommunityReport report = report();
        MongoCommunityReportRepository.ReportDocument document =
                new MongoCommunityReportRepository.ReportDocument(
                        report.dedupeKey(),
                        report.id(),
                        report.reporterId(),
                        report.targetType().name(),
                        report.targetId(),
                        report.reason().name(),
                        report.detail(),
                        null,
                        report.reporterTrustScore(),
                        report.riskScore(),
                        report.status().name(),
                        report.createdAt()
                );
        when(mongo.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MongoCommunityReportRepository.ReportDocument.class),
                eq(MongoCommunityReportRepository.COLLECTION)
        )).thenReturn(document);
        when(mongo.findById(
                "reporter",
                MongoCommunityReportRepository.TrustProjection.class,
                "user_profiles"
        )).thenReturn(
                null,
                new MongoCommunityReportRepository.TrustProjection(
                        "reporter", 80
                )
        );
        MongoCommunityReportRepository repository =
                new MongoCommunityReportRepository(mongo);

        for (CommunityReport.TargetType type
                : CommunityReport.TargetType.values()) {
            assertThat(repository.targetIsVisible(type, "target")).isTrue();
        }
        assertThat(repository.evidenceIsOwnedAndReady(
                "reporter", List.of()
        )).isTrue();
        assertThat(repository.reporterTrustScore("reporter")).isEqualTo(50);
        assertThat(repository.reporterTrustScore("reporter")).isEqualTo(80);
        assertThat(repository.saveIfAbsent(report).created()).isFalse();
        assertThat(repository.saveIfAbsent(report).report()
                .evidenceMediaIds()).isEmpty();
    }

    private static CommunityReport report() {
        return new CommunityReport(
                "80000000-0000-4000-8000-000000000001",
                "dedupe",
                "reporter",
                CommunityReport.TargetType.COMMENT,
                "30000000-0000-4000-8000-000000000001",
                CommunityReport.Reason.SPAM,
                "detail",
                List.of(
                        "70000000-0000-4000-8000-000000000001"
                ),
                50,
                45,
                CommunityReport.Status.RECEIVED,
                Instant.EPOCH
        );
    }
}
