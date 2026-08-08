package com.storyplatform.teams.api;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamDashboardController {

    private final JdbcClient jdbc;

    public TeamDashboardController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/teams/{teamId}/dashboard")
    public TeamDashboardResponse dashboard(@PathVariable String teamId) {
        TeamDashboardResponse response = jdbc.sql("""
                        SELECT t.id AS team_id,
                               t.state AS team_state,
                               COUNT(DISTINCT s.id) AS story_count,
                               COUNT(DISTINCT CASE
                                   WHEN s.workflow_status = 'PUBLISHED' THEN s.id
                                   ELSE NULL
                               END) AS published_story_count,
                               COUNT(DISTINCT CASE
                                   WHEN rp.exclusive = TRUE THEN s.id
                                   ELSE NULL
                               END) AS exclusive_story_count,
                               COALESCE(SUM(m.donation_xu), 0) AS revenue_xu,
                               COALESCE(SUM(m.view_count), 0) AS view_count,
                               COALESCE(SUM(ROUND(m.donation_xu * 0.06)), 0) AS sale_xu,
                               COALESCE(SUM(ROUND(m.donation_xu * 0.04)), 0) AS combo_sale_xu,
                               COALESCE(SUM(ROUND(m.donation_xu * 0.88)), 0) AS donation_xu,
                               COALESCE(SUM(ROUND(m.donation_xu * 0.02)), 0) AS event_xu,
                               COALESCE(MAX(rp.author_share_bps), 7000) AS author_share_bps,
                               COALESCE(MIN(rp.admin_share_bps), 3000) AS admin_share_bps
                        FROM teams t
                        LEFT JOIN stories s ON s.team_id = t.id
                        LEFT JOIN story_revenue_policies rp ON rp.story_id = s.id
                        LEFT JOIN story_engagement_metrics m ON m.story_id = s.id
                        WHERE t.id = :teamId
                        GROUP BY t.id, t.state
                        """)
                .param("teamId", teamId)
                .query((result, rowNumber) -> response(result, teamId))
                .optional()
                .orElseGet(() -> empty(teamId));
        String latestChapter = latestChapterTitle(teamId);
        return new TeamDashboardResponse(
                response.teamId(),
                response.publishStatus(),
                response.completionStatus(),
                response.exclusiveStatus(),
                response.copyrightStatus(),
                response.revenueXu(),
                latestChapter,
                response.supporters(),
                response.storyUrl(),
                response.viewCount(),
                response.saleXu(),
                response.comboSaleXu(),
                response.donationXu(),
                response.eventXu(),
                response.authorShareBps(),
                response.adminShareBps()
        );
    }

    private TeamDashboardResponse response(ResultSet result, String teamId)
            throws SQLException {
        long storyCount = result.getLong("story_count");
        long publishedStoryCount = result.getLong("published_story_count");
        long exclusiveStoryCount = result.getLong("exclusive_story_count");
        long revenueXu = result.getLong("revenue_xu");
        long viewCount = result.getLong("view_count");
        return new TeamDashboardResponse(
                teamId,
                publishedStoryCount > 0 ? "Đã xuất bản" : "Chưa xuất bản",
                storyCount > 0 ? "Đang hoạt động" : "Chưa có truyện",
                exclusiveStoryCount > 0 ? "Đã ký" : "Chưa ký",
                storyCount > 0 ? "Đang xác minh" : "Chưa xác minh",
                revenueXu,
                "Chưa xác định",
                publishedStoryCount > 0 ? "Danh sách" : "Chưa có",
                publishedStoryCount > 0 ? "/stories" : "Chưa có",
                viewCount,
                result.getLong("sale_xu"),
                result.getLong("combo_sale_xu"),
                result.getLong("donation_xu"),
                result.getLong("event_xu"),
                result.getInt("author_share_bps"),
                result.getInt("admin_share_bps")
        );
    }

    private String latestChapterTitle(String teamId) {
        return jdbc.sql("""
                        SELECT CONCAT('Chương ', c.chapter_number, ' - ', c.title)
                        FROM chapters c
                        JOIN stories s ON s.id = c.story_id
                        WHERE s.team_id = :teamId
                          AND c.workflow_status = 'PUBLISHED'
                        ORDER BY COALESCE(c.published_at, c.updated_at) DESC
                        LIMIT 1
                        """)
                .param("teamId", teamId)
                .query(String.class)
                .optional()
                .orElse("Chưa xác định");
    }

    private TeamDashboardResponse empty(String teamId) {
        return new TeamDashboardResponse(
                teamId,
                "Chưa xuất bản",
                "Chưa có truyện",
                "Chưa ký",
                "Chưa xác minh",
                0,
                "Chưa xác định",
                "Chưa có",
                "Chưa có",
                0,
                0,
                0,
                0,
                0,
                7000,
                3000
        );
    }

    public record TeamDashboardResponse(
            String teamId,
            String publishStatus,
            String completionStatus,
            String exclusiveStatus,
            String copyrightStatus,
            long revenueXu,
            String latestChapter,
            String supporters,
            String storyUrl,
            long viewCount,
            long saleXu,
            long comboSaleXu,
            long donationXu,
            long eventXu,
            int authorShareBps,
            int adminShareBps
    ) {
    }
}
