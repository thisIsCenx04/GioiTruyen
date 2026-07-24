package com.storyplatform.moderation.api;

import com.storyplatform.moderation.application
        .CommunityReportException;
import com.storyplatform.moderation.application
        .CommunityReportOperations;
import com.storyplatform.moderation.domain.CommunityReport;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@RestController
public final class CommunityReportController {

    private final CommunityReportOperations reports;

    public CommunityReportController(
            CommunityReportOperations reports
    ) {
        this.reports = Objects.requireNonNull(reports);
    }

    @PostMapping("/reports")
    public ResponseEntity<CommunityReportOperations.ReportView> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateReportRequest request
    ) {
        try {
            CommunityReportOperations.ReportView report = reports.create(
                    jwt.getSubject(),
                    new CommunityReportOperations.CreateCommand(
                            type(request.targetType()),
                            uuid(request.targetId()),
                            reason(request.reasonCode()),
                            request.detail(),
                            uuids(request.evidenceMediaIds())
                    )
            );
            return ResponseEntity.created(
                            URI.create("/api/v1/reports/" + report.id())
                    )
                    .cacheControl(CacheControl.noStore())
                    .body(report);
        } catch (CommunityReportException exception) {
            throw problem(exception);
        }
    }

    private static CommunityReport.TargetType type(String value) {
        try {
            return CommunityReport.TargetType.valueOf(
                    value.toUpperCase(Locale.ROOT)
            );
        } catch (RuntimeException exception) {
            throw invalid("Report target type is invalid.");
        }
    }

    private static CommunityReport.Reason reason(String value) {
        try {
            return CommunityReport.Reason.valueOf(
                    value.toUpperCase(Locale.ROOT)
            );
        } catch (RuntimeException exception) {
            throw invalid("Report reason is invalid.");
        }
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw invalid("Report identifier is invalid.");
        }
    }

    private static List<String> uuids(List<String> values) {
        return values == null
                ? List.of()
                : values.stream()
                        .map(CommunityReportController::uuid)
                        .toList();
    }

    private static ApiException problem(
            CommunityReportException exception
    ) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return new ApiException(
                status,
                exception.code(),
                "Report request rejected",
                exception.getMessage(),
                exception.retryAfterSeconds() > 0
                        ? Duration.ofSeconds(exception.retryAfterSeconds())
                        : null
        );
    }

    private static ApiException invalid(String detail) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "REPORT_INVALID",
                "Report request rejected",
                detail
        );
    }

    public record CreateReportRequest(
            @NotBlank String targetType,
            @NotBlank String targetId,
            @NotBlank String reasonCode,
            @Size(max = 5_000) String detail,
            @Size(max = 10) List<@NotBlank String> evidenceMediaIds
    ) {
    }
}
