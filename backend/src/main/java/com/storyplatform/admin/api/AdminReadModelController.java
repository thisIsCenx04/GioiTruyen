package com.storyplatform.admin.api;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminReadModelController {

    private final JdbcClient jdbc;

    public AdminReadModelController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        long revenueXu = scalar("""
                SELECT COALESCE(SUM(CASE WHEN amount_xu > 0 THEN amount_xu ELSE 0 END), 0)
                FROM ledger_entries
                """);
        long visits = scalar("SELECT COUNT(*) FROM reading_sessions");
        long readers = scalar("SELECT COUNT(DISTINCT actor_ref) FROM reading_sessions");
        long teams = scalar("SELECT COUNT(*) FROM teams");
        long stories = scalar("SELECT COUNT(*) FROM stories");

        return new DashboardResponse(
                new DashboardStats(revenueXu, visits, readers, teams, stories),
                revenueSeries(),
                trafficSeries(),
                readerSeries(),
                tasks()
        );
    }

    @GetMapping("/content/stories")
    public List<StoryRow> stories() {
        return jdbc.sql("""
                        SELECT s.id,
                               s.slug,
                               s.title,
                               s.author_name,
                               s.synopsis,
                               s.team_id,
                               s.workflow_status,
                               s.completion_status,
                               s.updated_at,
                               t.name AS team_name,
                               MIN(sc.category_id) AS category_id,
                               MIN(c.name) AS category_name
                        FROM stories s
                        JOIN teams t ON t.id = s.team_id
                        LEFT JOIN story_categories sc ON sc.story_id = s.id
                        LEFT JOIN categories c ON c.id = sc.category_id
                        GROUP BY s.id, s.slug, s.title, s.author_name,
                                 s.synopsis, s.team_id, s.workflow_status,
                                 s.completion_status, s.updated_at, t.name
                        ORDER BY s.updated_at DESC, s.title ASC
                        LIMIT 80
                        """)
                .query((result, rowNumber) -> new StoryRow(
                        result.getString("id"),
                        result.getString("slug"),
                        result.getString("title"),
                        result.getString("author_name"),
                        result.getString("team_name"),
                        result.getString("team_id"),
                        result.getString("category_id"),
                        result.getString("category_name"),
                        result.getString("synopsis"),
                        result.getString("workflow_status"),
                        result.getString("completion_status"),
                        timestamp(result, "updated_at")
                ))
                .list();
    }

    @GetMapping("/content/categories")
    public List<CategoryRow> categories() {
        return jdbc.sql("""
                        SELECT id, slug, name, description, sort_order,
                               active, version
                        FROM categories
                        ORDER BY sort_order ASC, name ASC
                        LIMIT 100
                        """)
                .query((result, rowNumber) -> new CategoryRow(
                        result.getString("id"),
                        result.getString("slug"),
                        result.getString("name"),
                        result.getString("description"),
                        result.getInt("sort_order"),
                        result.getBoolean("active"),
                        result.getLong("version")
                ))
                .list();
    }

    @GetMapping("/content/teams")
    public List<TeamRow> teams() {
        return jdbc.sql("""
                        SELECT t.id,
                               t.slug,
                               t.name,
                               t.state,
                               t.description,
                               t.owner_user_id,
                               t.updated_at,
                               COALESCE(p.display_name, u.email_normalized) AS owner_name,
                               COUNT(m.user_id) AS member_count
                        FROM teams t
                        JOIN users u ON u.id = t.owner_user_id
                        LEFT JOIN user_profiles p ON p.user_id = u.id
                        LEFT JOIN team_memberships m ON m.team_id = t.id
                        GROUP BY t.id, t.slug, t.name, t.state,
                                 t.description, t.owner_user_id, t.updated_at,
                                 p.display_name, u.email_normalized
                        ORDER BY t.updated_at DESC, t.name ASC
                        LIMIT 80
                        """)
                .query((result, rowNumber) -> new TeamRow(
                        result.getString("id"),
                        result.getString("slug"),
                        result.getString("name"),
                        result.getString("owner_name"),
                        result.getString("owner_user_id"),
                        result.getString("description"),
                        result.getString("state"),
                        result.getLong("member_count"),
                        timestamp(result, "updated_at")
                ))
                .list();
    }

    @GetMapping("/content/team-applications")
    public List<TeamApplicationRow> teamApplications() {
        return jdbc.sql("""
                        SELECT a.id,
                               a.slug,
                               a.name,
                               a.description,
                               a.state,
                               a.submitted_at,
                               COALESCE(p.display_name, u.email_normalized) AS requester_name
                        FROM team_applications a
                        JOIN users u ON u.id = a.requester_user_id
                        LEFT JOIN user_profiles p ON p.user_id = u.id
                        ORDER BY a.submitted_at DESC
                        LIMIT 80
                        """)
                .query((result, rowNumber) -> new TeamApplicationRow(
                        result.getString("id"),
                        result.getString("slug"),
                        result.getString("name"),
                        result.getString("description"),
                        result.getString("requester_name"),
                        result.getString("state"),
                        timestamp(result, "submitted_at")
                ))
                .list();
    }

    @GetMapping("/content/users")
    public List<UserRow> users() {
        return jdbc.sql("""
                        SELECT u.id,
                               u.email_normalized,
                               u.state,
                               u.created_at,
                               COALESCE(p.display_name, '') AS display_name,
                               COALESCE(p.bio, '') AS bio,
                               COALESCE(w.available_xu, 0) AS available_xu,
                               COALESCE(GROUP_CONCAT(r.role ORDER BY r.role SEPARATOR ', '), '') AS roles
                        FROM users u
                        LEFT JOIN user_profiles p ON p.user_id = u.id
                        LEFT JOIN user_roles r ON r.user_id = u.id
                        LEFT JOIN wallets w ON w.user_id = u.id
                        GROUP BY u.id, u.email_normalized, u.state,
                                 u.created_at, p.display_name, p.bio,
                                 w.available_xu
                        ORDER BY u.created_at DESC
                        LIMIT 80
                        """)
                .query((result, rowNumber) -> new UserRow(
                        result.getString("id"),
                        result.getString("email_normalized"),
                        result.getString("display_name"),
                        result.getString("bio"),
                        result.getString("state"),
                        result.getString("roles"),
                        result.getLong("available_xu"),
                        timestamp(result, "created_at")
                ))
                .list();
    }

    @GetMapping("/finance/cash-flow")
    public List<CashFlowRow> cashFlow() {
        return jdbc.sql("""
                        SELECT l.id,
                               l.entry_type,
                               l.amount_xu,
                               l.reference_type,
                               l.reference_id,
                               l.description,
                               l.created_at,
                               l.user_id,
                               u.email_normalized
                        FROM ledger_entries l
                        JOIN users u ON u.id = l.user_id
                        ORDER BY l.created_at DESC
                        LIMIT 100
                        """)
                .query((result, rowNumber) -> new CashFlowRow(
                        result.getString("id"),
                        result.getString("entry_type"),
                        result.getLong("amount_xu"),
                        result.getString("reference_type"),
                        result.getString("reference_id"),
                        result.getString("description"),
                        result.getString("user_id"),
                        result.getString("email_normalized"),
                        timestamp(result, "created_at")
                ))
                .list();
    }

    private List<ChartPoint> revenueSeries() {
        List<ChartPoint> points = jdbc.sql("""
                        SELECT DATE_FORMAT(created_at, '%d/%m') AS label,
                               COALESCE(SUM(CASE WHEN amount_xu > 0 THEN amount_xu ELSE 0 END), 0) AS value
                        FROM ledger_entries
                        GROUP BY DATE(created_at), DATE_FORMAT(created_at, '%d/%m')
                        ORDER BY DATE(created_at) DESC
                        LIMIT 12
                        """)
                .query((result, rowNumber) -> new ChartPoint(result.getString("label"), result.getLong("value")))
                .list();
        List<ChartPoint> ordered = new ArrayList<>(points);
        Collections.reverse(ordered);
        return ordered;
    }

    private List<ChartPoint> trafficSeries() {
        List<ChartPoint> points = jdbc.sql("""
                        SELECT DATE_FORMAT(started_at, '%d/%m') AS label,
                               COUNT(*) AS value
                        FROM reading_sessions
                        GROUP BY DATE(started_at), DATE_FORMAT(started_at, '%d/%m')
                        ORDER BY DATE(started_at) DESC
                        LIMIT 7
                        """)
                .query((result, rowNumber) -> new ChartPoint(result.getString("label"), result.getLong("value")))
                .list();
        List<ChartPoint> ordered = new ArrayList<>(points);
        Collections.reverse(ordered);
        return ordered;
    }

    private List<ChartPoint> readerSeries() {
        List<ChartPoint> points = jdbc.sql("""
                        SELECT DATE_FORMAT(started_at, '%d/%m') AS label,
                               COUNT(DISTINCT actor_ref) AS value
                        FROM reading_sessions
                        GROUP BY DATE(started_at),
                                 DATE_FORMAT(started_at, '%d/%m')
                        ORDER BY DATE(started_at) DESC
                        LIMIT 14
                        """)
                .query((result, rowNumber) -> new ChartPoint(
                        result.getString("label"),
                        result.getLong("value")
                ))
                .list();
        List<ChartPoint> ordered = new ArrayList<>(points);
        Collections.reverse(ordered);
        return ordered;
    }

    private List<String> tasks() {
        return List.of(
                "Duyệt các team đang chờ xem xét trước khi cấp quyền đăng truyện.",
                "Kiểm tra cash flow nạp xu, donate và đối soát ví người dùng.",
                "Rà soát truyện mới seed/publish để bảo đảm home và catalog luôn đọc từ DB.",
                "Theo dõi lượt đọc, reader và doanh thu trên dashboard admin."
        );
    }

    private long scalar(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static String timestamp(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

    public record DashboardResponse(
            DashboardStats stats,
            List<ChartPoint> revenueSeries,
            List<ChartPoint> trafficSeries,
            List<ChartPoint> readerSeries,
            List<String> tasks
    ) {
    }

    public record DashboardStats(long revenueXu, long visits, long readers, long teams, long stories) {
    }

    public record ChartPoint(String label, long value) {
    }

    public record StoryRow(
            String id,
            String slug,
            String title,
            String authorName,
            String teamName,
            String teamId,
            String categoryId,
            String categoryName,
            String synopsis,
            String workflowStatus,
            String completionStatus,
            String updatedAt
    ) {
    }

    public record CategoryRow(
            String id,
            String slug,
            String name,
            String description,
            int sortOrder,
            boolean active,
            long version
    ) {
    }

    public record TeamRow(
            String id,
            String slug,
            String name,
            String ownerName,
            String ownerUserId,
            String description,
            String state,
            long memberCount,
            String updatedAt
    ) {
    }

    public record TeamApplicationRow(
            String id,
            String slug,
            String name,
            String description,
            String requesterName,
            String state,
            String submittedAt
    ) {
    }

    public record UserRow(
            String id,
            String email,
            String displayName,
            String bio,
            String state,
            String roles,
            long availableXu,
            String createdAt
    ) {
    }

    public record CashFlowRow(
            String id,
            String entryType,
            long amountXu,
            String referenceType,
            String referenceId,
            String description,
            String userId,
            String userEmail,
            String createdAt
    ) {
    }
}
