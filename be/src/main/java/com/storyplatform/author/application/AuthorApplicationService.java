package com.storyplatform.author.application;

import com.storyplatform.shared.api.ApiException;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applying to publish, and deciding those applications.
 *
 * <p>Approving creates the applicant's team and makes them its owner. A team
 * can hold one person or several, so this is simply its first member rather
 * than a separate kind of account - everything downstream (stories, revenue,
 * donations, bố cáo) keeps working through teams as before.
 */
@Service
public class AuthorApplicationService {

    private static final int MAX_INTRODUCTION = 2000;

    private final JdbcClient jdbc;

    public AuthorApplicationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record ApplicationView(
            String id,
            String userId,
            String userEmail,
            String userName,
            String teamName,
            String penName,
            String introduction,
            String sampleWork,
            String phoneNumber,
            String facebookUrl,
            String status,
            String reviewNote,
            String reviewedAt,
            String createdTeamId,
            String createdAt,
            String updatedAt
    ) {
    }

    public record ApplyRequest(
            String teamName,
            String penName,
            String introduction,
            String sampleWork,
            String phoneNumber,
            String facebookUrl
    ) {
    }

    private static final String SELECT = """
            SELECT a.id, a.user_id, u.email AS user_email, u.display_name AS user_name,
                   a.team_name, a.pen_name, a.introduction, a.sample_work,
                   a.phone_number, a.facebook_url, a.status,
                   a.review_note, a.reviewed_at, a.created_team_id, a.created_at, a.updated_at
            FROM author_applications a
            JOIN users u ON u.id = a.user_id
            """;

    /** The applicant's own request, if they have one. */
    @Transactional(readOnly = true)
    public ApplicationView mine(UUID userId) {
        return jdbc.sql(SELECT + " WHERE a.user_id = ? ORDER BY a.created_at DESC LIMIT 1")
                .param(userId.toString())
                .query((rs, rowNum) -> toView(rs))
                .optional()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> queue(String status) {
        String filter = status == null || status.isBlank() ? null
                : status.trim().toUpperCase(Locale.ROOT);
        return jdbc.sql(SELECT + """
                        WHERE (? IS NULL OR a.status = ?)
                        ORDER BY a.status = 'PENDING' DESC, a.created_at DESC
                        LIMIT 200
                        """)
                .params(filter, filter)
                .query((rs, rowNum) -> toView(rs))
                .list();
    }

    @Transactional
    public ApplicationView apply(UUID userId, ApplyRequest request) {
        String teamName = requireText(request.teamName(), "Tên nhóm đăng truyện");
        String introduction = requireText(request.introduction(), "Phần giới thiệu / Ghi chú");
        if (introduction.length() > MAX_INTRODUCTION) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "author.introduction_too_long",
                    "Introduction too long",
                    "Phần giới thiệu tối đa %d ký tự.".formatted(MAX_INTRODUCTION));
        }

        boolean alreadyPublisher = jdbc.sql("""
                        SELECT COUNT(*) FROM team_members
                        WHERE user_id = ? AND status = 'ACTIVE'
                        """)
                .param(userId.toString())
                .query(Long.class)
                .single() > 0;
        if (alreadyPublisher) {
            throw new ApiException(HttpStatus.CONFLICT, "author.already_publisher",
                    "Already a publisher",
                    "Tài khoản của bạn đã có quyền đăng truyện.");
        }

        boolean pending = jdbc.sql("""
                        SELECT COUNT(*) FROM author_applications
                        WHERE user_id = ? AND status = 'PENDING'
                        """)
                .param(userId.toString())
                .query(Long.class)
                .single() > 0;
        if (pending) {
            throw new ApiException(HttpStatus.CONFLICT, "author.already_pending",
                    "Application already pending",
                    "Bạn đã gửi một yêu cầu và đang chờ duyệt.");
        }

        String normalizedPhone = normalizePhone(request.phoneNumber());
        if (normalizedPhone != null) {
            if (normalizedPhone.length() < 9 || normalizedPhone.length() > 12) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "author.invalid_phone",
                        "Invalid phone number",
                        "Số điện thoại không hợp lệ (yêu cầu từ 9 đến 11 chữ số).");
            }
            boolean phoneInUse = jdbc.sql("""
                            SELECT COUNT(*) FROM author_applications
                            WHERE phone_number = ? AND status IN ('PENDING', 'APPROVED')
                            """)
                    .param(normalizedPhone)
                    .query(Long.class)
                    .single() > 0;
            if (phoneInUse) {
                throw new ApiException(HttpStatus.CONFLICT, "author.phone_number_in_use",
                        "Phone number already in use",
                        "Số điện thoại %s đã được sử dụng để gửi yêu cầu và đang chờ duyệt hoặc đã được cấp quyền."
                                .formatted(normalizedPhone));
            }
        }

        String normalizedFb = normalizeFacebook(request.facebookUrl());
        if (normalizedFb != null) {
            boolean fbInUse = jdbc.sql("""
                            SELECT COUNT(*) FROM author_applications
                            WHERE facebook_url = ? AND status IN ('PENDING', 'APPROVED')
                            """)
                    .param(normalizedFb)
                    .query(Long.class)
                    .single() > 0;
            if (fbInUse) {
                throw new ApiException(HttpStatus.CONFLICT, "author.facebook_url_in_use",
                        "Facebook URL already in use",
                        "Link Facebook/Fanpage này đã được sử dụng để gửi yêu cầu và đang chờ duyệt hoặc đã được cấp quyền.");
            }
        }

        String id = UUID.randomUUID().toString();
        jdbc.sql("""
                        INSERT INTO author_applications
                            (id, user_id, team_name, pen_name, introduction, sample_work, phone_number, facebook_url, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                        """)
                .params(id, userId.toString(), teamName, trimToNull(request.penName()),
                        introduction, trimToNull(request.sampleWork()),
                        normalizedPhone, normalizedFb)
                .update();

        return findById(id);
    }

    /** Approves an application and creates the team the applicant asked for. */
    @Transactional
    public ApplicationView approve(UUID applicationId, String reviewerId, String note) {
        ApplicationView application = requirePending(applicationId);

        String teamId = UUID.randomUUID().toString();
        String slug = uniqueTeamSlug(application.teamName());

        jdbc.sql("""
                        INSERT INTO teams (id, name, slug, description, status, created_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'ACTIVE', ?, NOW(3), NOW(3))
                        """)
                .params(teamId, application.teamName(), slug, application.introduction(),
                        application.userId())
                .update();

        // The applicant is the team's first member and its owner; others can be
        // added later without changing anything here.
        //
        // Column is `member_role`, not `role`, and `added_by` is NOT NULL with no
        // default - getting either wrong makes the whole approval fail with a 500
        // and leaves an orphaned team row behind.
        jdbc.sql("""
                        INSERT INTO team_members
                            (id, team_id, user_id, member_role, status, added_by, joined_at)
                        VALUES (?, ?, ?, 'OWNER', 'ACTIVE', ?, NOW())
                        """)
                .params(UUID.randomUUID().toString(), teamId, application.userId(),
                        // The reviewing admin is who granted membership; fall back to
                        // the applicant so the NOT NULL column is always satisfied.
                        reviewerId == null ? application.userId() : reviewerId)
                .update();

        jdbc.sql("""
                        UPDATE author_applications
                        SET status = 'APPROVED', review_note = ?, reviewed_by = ?,
                            reviewed_at = NOW(3), created_team_id = ?
                        WHERE id = ?
                        """)
                .params(trimToNull(note), reviewerId, teamId, applicationId.toString())
                .update();

        notify(application.userId(), "Yêu cầu đăng truyện đã được duyệt",
                "Nhóm \"%s\" đã được tạo và bạn là chủ nhóm. Bạn có thể bắt đầu đăng truyện.%s"
                        .formatted(application.teamName(),
                                note == null || note.isBlank() ? "" : " Ghi chú: " + note));

        return findById(applicationId.toString());
    }

    /** Rejects an application; the reason is required and sent to the applicant. */
    @Transactional
    public ApplicationView reject(UUID applicationId, String reviewerId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "author.reason_required",
                    "Rejection reason required",
                    "Hãy nhập lý do từ chối; người gửi yêu cầu sẽ nhận được nội dung này.");
        }
        ApplicationView application = requirePending(applicationId);

        jdbc.sql("""
                        UPDATE author_applications
                        SET status = 'REJECTED', review_note = ?, reviewed_by = ?, reviewed_at = NOW(3)
                        WHERE id = ?
                        """)
                .params(reason.trim(), reviewerId, applicationId.toString())
                .update();

        notify(application.userId(), "Yêu cầu đăng truyện bị từ chối",
                "Yêu cầu của bạn chưa được chấp nhận. Lý do: " + reason.trim());

        return findById(applicationId.toString());
    }

    private ApplicationView requirePending(UUID applicationId) {
        ApplicationView application = jdbc.sql(SELECT + " WHERE a.id = ?")
                .param(applicationId.toString())
                .query((rs, rowNum) -> toView(rs))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "author.not_found",
                        "Application not found", "Không tìm thấy yêu cầu này."));
        if (!"PENDING".equals(application.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "author.not_pending",
                    "Application already decided",
                    "Yêu cầu này đã được xử lý (%s).".formatted(application.status()));
        }
        return application;
    }

    public static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9+]", "").trim();
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        return digits.isBlank() ? null : digits;
    }

    public static String normalizeFacebook(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String url = raw.trim();
        if (url.startsWith("http://")) {
            url = "https://" + url.substring(7);
        } else if (!url.startsWith("https://")) {
            url = "https://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        int queryIdx = url.indexOf("?");
        if (queryIdx > 0 && (url.contains("facebook.com") || url.contains("fb.com"))) {
            if (url.contains("profile.php") && url.contains("id=")) {
                int idIdx = url.indexOf("id=");
                int nextAmp = url.indexOf("&", idIdx);
                if (nextAmp > 0) {
                    url = url.substring(0, nextAmp);
                }
            } else {
                url = url.substring(0, queryIdx);
            }
        }
        return url.toLowerCase(Locale.ROOT);
    }

    /**
     * "Nhà Dịch Ánh Trăng" -> "nha-dich-anh-trang".
     *
     * <p>Local rather than shared with the admin module: reaching into
     * {@code admin.api} from here would couple two modules that otherwise know
     * nothing about each other.
     */
    public static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return withoutMarks.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    /** Team slugs are unique, and two applicants may pick the same name. */
    private String uniqueTeamSlug(String teamName) {
        String base = slugify(teamName);
        if (base.isBlank()) {
            base = "team";
        }
        String candidate = base;
        for (int suffix = 2; taken(candidate); suffix++) {
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private boolean taken(String slug) {
        return jdbc.sql("SELECT COUNT(*) FROM teams WHERE slug = ?")
                .param(slug)
                .query(Long.class)
                .single() > 0;
    }

    private void notify(String userId, String title, String message) {
        jdbc.sql("""
                        INSERT INTO notifications
                            (id, user_id, type, title, message, target_type, target_url, created_at)
                        VALUES (?, ?, 'ACCOUNT', ?, ?, 'AUTHOR_APPLICATION', '/account', NOW())
                        """)
                .params(UUID.randomUUID().toString(), userId, title, message)
                .update();
    }

    private ApplicationView findById(String id) {
        return jdbc.sql(SELECT + " WHERE a.id = ?")
                .param(id)
                .query((rs, rowNum) -> toView(rs))
                .single();
    }

    private static ApplicationView toView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ApplicationView(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("user_email"),
                rs.getString("user_name"),
                rs.getString("team_name"),
                rs.getString("pen_name"),
                rs.getString("introduction"),
                rs.getString("sample_work"),
                rs.getString("phone_number"),
                rs.getString("facebook_url"),
                rs.getString("status"),
                rs.getString("review_note"),
                instant(rs, "reviewed_at"),
                rs.getString("created_team_id"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private static String instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "author.missing_field",
                    "Missing field", "%s không được để trống.".formatted(label));
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
