package com.storyplatform.moderation.api;

import com.storyplatform.moderation.domain.ReportTargetType;
import com.storyplatform.shared.api.ApiException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

    /** Matches the `report_type` column, which is VARCHAR(100). */
    private static final int REPORT_TYPE_LIMIT = 100;

    private final NamedParameterJdbcTemplate jdbc;

    public ReportController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
        // reports.reporter_id is NOT NULL with a foreign key onto users, so a
        // report has to belong to a real account. Substituting a random UUID for
        // an anonymous caller failed that key and lost the report.
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Login required", "Bạn cần đăng nhập để gửi báo cáo.");
        }
        UUID reporterId = UUID.fromString(jwt.getSubject());

        UUID targetId;
        try {
            targetId = UUID.fromString(request.targetId());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid.target_id",
                    "Mã đối tượng báo cáo không hợp lệ", "Mã đối tượng không hợp lệ");
        }

        ReportTargetType targetType;
        try {
            targetType = ReportTargetType.valueOf(
                    request.targetType().trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            targetType = ReportTargetType.STORY;
        }

        String reportType = request.reportType() == null || request.reportType().isBlank()
                ? "VIOLATION"
                : request.reportType().trim();
        if (reportType.length() > REPORT_TYPE_LIMIT) {
            reportType = reportType.substring(0, REPORT_TYPE_LIMIT);
        }

        UUID reportId = UUID.randomUUID();
        Instant now = Instant.now();

        // The id is assigned here, so Spring Data JDBC would read the entity as
        // an existing row and save() would emit an UPDATE that matches nothing -
        // which turned every report into a 500. An explicit INSERT is required.
        jdbc.update(
                """
                        INSERT INTO reports (id, reporter_id, target_type, target_id,
                                             report_type, description, status, created_at)
                        VALUES (:id, :reporterId, :targetType, :targetId,
                                :reportType, :description, 'OPEN', :createdAt)
                        """,
                Map.of(
                        "id", reportId.toString(),
                        "reporterId", reporterId.toString(),
                        "targetType", targetType.name(),
                        "targetId", targetId.toString(),
                        "reportType", reportType,
                        "description", request.description() == null ? "" : request.description(),
                        "createdAt", java.sql.Timestamp.from(now)
                )
        );

        return new ReportReceipt(
                reportId.toString(),
                "RECEIVED",
                "Cảm ơn bạn đã gửi báo cáo. Ban quản trị sẽ kiểm tra trong thời gian sớm nhất."
        );
    }
}
