package com.storyplatform.unit.moderation.application;

import com.storyplatform.moderation.application
        .CommunityReportException;
import com.storyplatform.moderation.application
        .CommunityReportOperations;
import com.storyplatform.moderation.application
        .CommunityReportService;
import com.storyplatform.moderation.application.ReportRateLimiter;
import com.storyplatform.moderation.application.port
        .CommunityReportRepository;
import com.storyplatform.moderation.domain.CommunityReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityReportServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T08:00:00Z");
    private static final String TARGET =
            "30000000-0000-4000-8000-000000000001";
    private final CommunityReportRepository repository =
            mock(CommunityReportRepository.class);
    private final ReportRateLimiter limiter =
            mock(ReportRateLimiter.class);
    private CommunityReportService service;

    @BeforeEach
    void setUp() {
        service = new CommunityReportService(
                repository,
                limiter,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(repository.targetIsVisible(any(), eq(TARGET)))
                .thenReturn(true);
        when(repository.evidenceIsOwnedAndReady(any(), any()))
                .thenReturn(true);
        when(repository.reporterTrustScore("reporter")).thenReturn(75);
        when(repository.findByDedupeKey(any()))
                .thenReturn(Optional.empty());
        when(limiter.allow("reporter", 75)).thenReturn(true);
        when(repository.saveIfAbsent(any())).thenAnswer(invocation ->
                new CommunityReportRepository.SaveResult(
                        invocation.getArgument(0),
                        true
                ));
    }

    @Test
    void sanitizesAndSnapshotsTrustRiskAndOwnedEvidence() {
        CommunityReportOperations.ReportView view = service.create(
                "reporter",
                command(
                        CommunityReport.Reason.ILLEGAL_CONTENT,
                        "<b>Chi tiết</b><script>alert(1)</script>",
                        List.of(
                                "70000000-0000-4000-8000-000000000001"
                        )
                )
        );

        ArgumentCaptor<CommunityReport> saved =
                ArgumentCaptor.forClass(CommunityReport.class);
        verify(repository).saveIfAbsent(saved.capture());
        assertThat(saved.getValue().detail()).isEqualTo("Chi tiết");
        assertThat(saved.getValue().reporterTrustScore()).isEqualTo(75);
        assertThat(saved.getValue().riskScore()).isEqualTo(95);
        assertThat(view.duplicate()).isFalse();
    }

    @Test
    void returnsDailyDuplicateWithoutConsumingAnotherQuota() {
        CommunityReport existing = report();
        when(repository.findByDedupeKey(any()))
                .thenReturn(Optional.of(existing));

        CommunityReportOperations.ReportView view = service.create(
                "reporter",
                command(CommunityReport.Reason.SPAM, null, List.of())
        );

        assertThat(view.id()).isEqualTo(existing.id());
        assertThat(view.duplicate()).isTrue();
        verify(limiter, never()).allow(any(), any(Integer.class));
        verify(repository, never()).saveIfAbsent(any());
    }

    @Test
    void rejectsMissingEvidenceAbuseAndFailClosedQuota() {
        when(repository.evidenceIsOwnedAndReady(any(), any()))
                .thenReturn(false);
        assertThatThrownBy(() -> service.create(
                "reporter",
                command(
                        CommunityReport.Reason.OTHER,
                        "detail",
                        List.of(
                                "70000000-0000-4000-8000-000000000001"
                        )
                )
        )).isInstanceOfSatisfying(
                CommunityReportException.class,
                error -> assertThat(error.code())
                        .isEqualTo("REPORT_EVIDENCE_NOT_FOUND")
        );

        when(repository.evidenceIsOwnedAndReady(any(), any()))
                .thenReturn(true);
        when(limiter.allow("reporter", 75)).thenReturn(false);
        when(limiter.retryAfterSeconds()).thenReturn(86_400L);
        assertThatThrownBy(() -> service.create(
                "reporter",
                command(CommunityReport.Reason.SPAM, null, List.of())
        )).isInstanceOfSatisfying(
                CommunityReportException.class,
                error -> {
                    assertThat(error.kind()).isEqualTo(
                            CommunityReportException.Kind.RATE_LIMITED
                    );
                    assertThat(error.retryAfterSeconds())
                            .isEqualTo(86_400);
                }
        );
    }

    @Test
    void validatesTargetAndUniqueBoundedEvidence() {
        when(repository.targetIsVisible(
                CommunityReport.TargetType.COMMENT,
                TARGET
        )).thenReturn(false);
        assertThatThrownBy(() -> service.create(
                "reporter",
                command(CommunityReport.Reason.SPAM, null, List.of())
        )).isInstanceOf(CommunityReportException.class);

        when(repository.targetIsVisible(any(), eq(TARGET)))
                .thenReturn(true);
        String evidence =
                "70000000-0000-4000-8000-000000000001";
        assertThatThrownBy(() -> service.create(
                "reporter",
                command(
                        CommunityReport.Reason.SPAM,
                        null,
                        List.of(evidence, evidence)
                )
        )).isInstanceOf(CommunityReportException.class);
    }

    @Test
    void riskPolicyCoversMediumAndLowSeverityReasons() {
        CommunityReportOperations.ReportView medium = service.create(
                "reporter",
                command(
                        CommunityReport.Reason.COPYRIGHT,
                        " ",
                        List.of()
                )
        );
        CommunityReportOperations.ReportView low = service.create(
                "reporter",
                command(
                        CommunityReport.Reason.BROKEN_CONTENT,
                        null,
                        List.of()
                )
        );

        assertThat(medium.riskScore()).isEqualTo(75);
        assertThat(low.riskScore()).isEqualTo(30);
    }

    private static CommunityReportOperations.CreateCommand command(
            CommunityReport.Reason reason,
            String detail,
            List<String> evidence
    ) {
        return new CommunityReportOperations.CreateCommand(
                CommunityReport.TargetType.COMMENT,
                TARGET,
                reason,
                detail,
                evidence
        );
    }

    private static CommunityReport report() {
        return new CommunityReport(
                "80000000-0000-4000-8000-000000000001",
                "dedupe",
                "reporter",
                CommunityReport.TargetType.COMMENT,
                TARGET,
                CommunityReport.Reason.SPAM,
                null,
                List.of(),
                75,
                50,
                CommunityReport.Status.RECEIVED,
                NOW
        );
    }
}
