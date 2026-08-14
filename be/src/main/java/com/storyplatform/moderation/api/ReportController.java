package com.storyplatform.moderation.api;

import com.storyplatform.moderation.domain.Report;
import com.storyplatform.moderation.domain.ReportStatus;
import com.storyplatform.moderation.domain.ReportTargetType;
import com.storyplatform.moderation.infrastructure.ReportRepository;
import com.storyplatform.shared.api.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles user reports for inappropriate content, spam, copyright issues, or violations.
 */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportRepository reportRepository;

    public ReportController(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public record CreateReportRequest(
            String targetType,
            String targetId,
            String reportType,
            String description
    ) {}

    public record ReportReceipt(String reportId, String status, String message) {}

    @PostMapping
    public ReportReceipt submitReport(
            @RequestBody CreateReportRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID reporterId = null;
        if (jwt != null && jwt.getSubject() != null) {
            try {
                reporterId = UUID.fromString(jwt.getSubject());
            } catch (Exception ignored) {}
        }

        UUID targetUUID;
        try {
            targetUUID = UUID.fromString(request.targetId());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid.target_id", "Mã đối tượng báo cáo không hợp lệ", "Mã đối tượng không hợp lệ");
        }

        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporterId(reporterId != null ? reporterId : UUID.randomUUID());
        
        try {
            report.setTargetType(ReportTargetType.valueOf(request.targetType().toUpperCase()));
        } catch (Exception ex) {
            report.setTargetType(ReportTargetType.STORY);
        }

        report.setTargetId(targetUUID);
        report.setReportType(request.reportType() != null ? request.reportType() : "VIOLATION");
        report.setDescription(request.description() != null ? request.description() : "");
        report.setStatus(ReportStatus.OPEN);
        report.setCreatedAt(Instant.now());

        reportRepository.save(report);

        return new ReportReceipt(
                report.getId().toString(),
                "RECEIVED",
                "Cảm ơn bạn đã gửi báo cáo. Ban quản trị sẽ kiểm tra trong thời gian sớm nhất."
        );
    }
}
