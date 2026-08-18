package com.storyplatform.teams.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Answers one question for the signed-in account: may it publish, and where
 * should the "Đăng truyện" entry point lead?
 *
 * <p>The profile menu needs this before it can link anywhere. An approved
 * publisher goes to their team workspace; everyone else goes to the application
 * form, and a pending or rejected application is reported so the UI can say so
 * instead of sending them to re-apply blindly.
 */
@RestController
public class PublishingAccessController {

    /** Team roles that may open the publisher workspace. */
    private static final List<String> PUBLISHING_ROLES = List.of("OWNER", "MANAGER", "EDITOR");

    private final JdbcClient jdbc;

    public PublishingAccessController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/me/publishing")
    @Transactional(readOnly = true)
    public PublishingAccess access(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập.");
        }
        String userId = jwt.getSubject();

        // Membership is the source of truth: an application can be approved while
        // the team is still being set up, and an admin can add someone to a team
        // without any application at all.
        Membership membership = jdbc.sql("""
                SELECT m.team_id, m.member_role, t.name AS team_name, t.slug AS team_slug
                FROM team_members m
                JOIN teams t ON t.id = m.team_id
                WHERE m.user_id = ? AND m.status = 'ACTIVE' AND t.status = 'ACTIVE'
                ORDER BY FIELD(m.member_role, 'OWNER', 'MANAGER', 'EDITOR', 'MEMBER'), m.joined_at
                LIMIT 1
                """)
                .param(userId)
                .query((rs, rowNum) -> new Membership(
                        rs.getString("team_id"),
                        rs.getString("team_slug"),
                        rs.getString("team_name"),
                        rs.getString("member_role")
                ))
                .optional()
                .orElse(null);

        String applicationStatus = jdbc.sql("""
                SELECT status FROM author_applications
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """)
                .param(userId)
                .query(String.class)
                .optional()
                .orElse(null);

        boolean canPublish = membership != null && PUBLISHING_ROLES.contains(membership.memberRole());

        return new PublishingAccess(
                canPublish,
                membership == null ? null : membership.teamId(),
                membership == null ? null : membership.teamSlug(),
                membership == null ? null : membership.teamName(),
                membership == null ? null : membership.memberRole(),
                applicationStatus,
                // Slug when the team has one, so the address bar reads
                // /teams/nha-dich-anh-trang/dashboard rather than a raw UUID.
                // TeamWorkspaceController accepts either form.
                canPublish
                        ? "/teams/" + firstNonBlank(membership.teamSlug(), membership.teamId()) + "/dashboard"
                        : "/dang-ky-dang-truyen"
        );
    }

    public record PublishingAccess(
            /** True when the account may open the publisher workspace. */
            boolean canPublish,
            String teamId,
            String teamSlug,
            String teamName,
            String memberRole,
            /** PENDING, APPROVED, REJECTED, or null when never applied. */
            String applicationStatus,
            /** Where the profile menu should send this account. */
            String entryPath
    ) {}

    /** Falls back to the id when a team has no slug yet. */
    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private record Membership(String teamId, String teamSlug, String teamName, String memberRole) {}
}
