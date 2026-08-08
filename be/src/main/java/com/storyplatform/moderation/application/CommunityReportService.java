package com.storyplatform.moderation.application;

import com.storyplatform.moderation.application.port
        .CommunityReportRepository;
import com.storyplatform.moderation.domain.CommunityReport;
import org.jsoup.Jsoup;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CommunityReportService
        implements CommunityReportOperations {

    private final CommunityReportRepository reports;
    private final ReportRateLimiter limiter;
    private final Clock clock;

    public CommunityReportService(
            CommunityReportRepository reports,
            ReportRateLimiter limiter,
            Clock clock
    ) {
        this.reports = Objects.requireNonNull(reports);
        this.limiter = Objects.requireNonNull(limiter);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ReportView create(String reporterId, CreateCommand command) {
        requireTarget(command.targetType(), command.targetId());
        List<String> evidence = command.evidenceMediaIds();
        if (evidence.size() > 10
                || new HashSet<>(evidence).size() != evidence.size()) {
            throw invalid("Report evidence must contain at most 10 unique items.");
        }
        if (!reports.evidenceIsOwnedAndReady(reporterId, evidence)) {
            throw new CommunityReportException(
                    "REPORT_EVIDENCE_NOT_FOUND",
                    "Report evidence does not exist.",
                    CommunityReportException.Kind.NOT_FOUND
            );
        }
        String detail = sanitize(command.detail());
        String dedupeKey = String.join(
                ":",
                reporterId,
                command.targetType().name(),
                command.targetId(),
                command.reason().name(),
                LocalDate.now(clock).toString()
        );
        java.util.Optional<CommunityReport> duplicate =
                reports.findByDedupeKey(dedupeKey);
        if (duplicate.isPresent()) {
            return view(duplicate.orElseThrow(), true);
        }
        int trust = Math.clamp(
                reports.reporterTrustScore(reporterId),
                0,
                100
        );
        if (!limiter.allow(reporterId, trust)) {
            throw new CommunityReportException(
                    "REPORT_RATE_LIMITED",
                    "Daily report quota exceeded.",
                    CommunityReportException.Kind.RATE_LIMITED,
                    limiter.retryAfterSeconds()
            );
        }
        CommunityReport report = new CommunityReport(
                UUID.randomUUID().toString(),
                dedupeKey,
                reporterId,
                command.targetType(),
                command.targetId(),
                command.reason(),
                detail,
                evidence,
                trust,
                risk(command.reason(), trust),
                CommunityReport.Status.RECEIVED,
                clock.instant()
        );
        CommunityReportRepository.SaveResult saved =
                reports.saveIfAbsent(report);
        return view(saved.report(), !saved.created());
    }

    private void requireTarget(
            CommunityReport.TargetType type,
            String targetId
    ) {
        if (type == null || !reports.targetIsVisible(type, targetId)) {
            throw new CommunityReportException(
                    "REPORT_TARGET_NOT_FOUND",
                    "Report target does not exist.",
                    CommunityReportException.Kind.NOT_FOUND
            );
        }
    }

    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(raw);
        document.select(
                "script,style,noscript,iframe,object,embed,template"
        ).remove();
        String plain = document.body().text()
                .replaceAll("\\s+", " ")
                .strip();
        if (plain.isBlank() || plain.codePointCount(0, plain.length())
                > 5_000) {
            throw invalid("Report detail must not exceed 5000 characters.");
        }
        return plain;
    }

    private static int risk(
            CommunityReport.Reason reason,
            int trust
    ) {
        int base = switch (reason) {
            case ILLEGAL_CONTENT, SEXUAL_CONTENT -> 90;
            case COPYRIGHT, IMPERSONATION, HARASSMENT -> 70;
            case SPAM -> 45;
            case BROKEN_CONTENT, OTHER -> 25;
        };
        return Math.clamp(base + ((trust - 50) / 5), 0, 100);
    }

    private static ReportView view(
            CommunityReport report,
            boolean duplicate
    ) {
        return new ReportView(
                report.id(),
                report.targetType(),
                report.targetId(),
                report.reason(),
                report.status(),
                report.riskScore(),
                duplicate,
                report.createdAt()
        );
    }

    private static CommunityReportException invalid(String detail) {
        return new CommunityReportException(
                "REPORT_INVALID",
                detail,
                CommunityReportException.Kind.INVALID
        );
    }
}
