package com.storyplatform.admin.api;

import com.storyplatform.admin.application.dto.AdminDtos.AdOverview;
import com.storyplatform.admin.application.dto.AdminDtos.AdPlacementRow;
import com.storyplatform.admin.application.dto.AdminDtos.AdminOverview;
import com.storyplatform.admin.application.dto.AdminDtos.AdminStats;
import com.storyplatform.admin.application.dto.AdminDtos.ChartPoint;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    /**
     * The dashboard offers 7-day, 30-day and 3-month views, but only seven
     * points were ever sent, so picking "3 tháng" quietly showed the same week
     * under a different label. Ninety covers the widest range on offer.
     */
    private static final int SERIES_DAYS = 90;

    private final JdbcClient jdbc;

    public AdminDashboardController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public AdminOverview overview() {
        AdminStats stats = new AdminStats(
                count("SELECT COALESCE(SUM(net_coin), 0) FROM team_ledger"),
                count("SELECT COUNT(*) FROM story_views"),
                count("SELECT COUNT(*) FROM users WHERE role = 'READER'"),
                count("SELECT COUNT(*) FROM teams WHERE status = 'ACTIVE'"),
                count("SELECT COUNT(*) FROM stories")
        );

        return new AdminOverview(
                stats,
                dailySeries("SELECT DATE(created_at) AS d, COALESCE(SUM(net_coin), 0) AS v FROM team_ledger "
                        + "WHERE created_at >= ? GROUP BY DATE(created_at)"),
                dailySeries("SELECT DATE(viewed_at) AS d, COUNT(*) AS v FROM story_views "
                        + "WHERE viewed_at >= ? GROUP BY DATE(viewed_at)"),
                dailySeries("SELECT DATE(created_at) AS d, COUNT(*) AS v FROM users "
                        + "WHERE created_at >= ? GROUP BY DATE(created_at)"),
                pendingTasks(),
                ads()
        );
    }

    /**
     * Advertising performance from ad_events.
     *
     * <p>These are the platform's own banners and affiliate redirects - the rows
     * this database actually holds. AdSense impressions and earnings are not
     * here: they live in the AdSense account and can only be read through the
     * AdSense Management API, which needs its own OAuth credentials. The panel
     * says so rather than showing zeroes that look like a collapse in traffic.
     *
     * <p>A REDIRECT counts as a click: both mean the reader acted on the ad, and
     * an affiliate link records the leaving hop rather than a press.
     */
    private AdOverview ads() {
        long impressions = count("SELECT COUNT(*) FROM ad_events WHERE event_type = 'IMPRESSION'");
        long clicks = count("SELECT COUNT(*) FROM ad_events WHERE event_type IN ('CLICK', 'REDIRECT')");
        long activeUnits = count("SELECT COUNT(*) FROM advertisements WHERE is_active = TRUE");

        List<AdPlacementRow> placements = jdbc.sql("""
                        SELECT a.placement,
                               SUM(e.event_type = 'IMPRESSION')                AS impressions,
                               SUM(e.event_type IN ('CLICK', 'REDIRECT'))      AS clicks,
                               COUNT(DISTINCT IF(a.is_active, a.id, NULL))     AS active_units
                        FROM advertisements a
                        LEFT JOIN ad_events e ON e.advertisement_id = a.id
                        GROUP BY a.placement
                        ORDER BY impressions DESC, a.placement ASC
                        """)
                .query((rs, rowNum) -> {
                    long shown = rs.getLong("impressions");
                    long pressed = rs.getLong("clicks");
                    return new AdPlacementRow(
                            rs.getString("placement"),
                            shown,
                            pressed,
                            ctr(pressed, shown),
                            rs.getLong("active_units")
                    );
                })
                .list();

        return new AdOverview(
                impressions,
                clicks,
                ctr(clicks, impressions),
                activeUnits,
                dailySeries("SELECT DATE(created_at) AS d, COUNT(*) AS v FROM ad_events "
                        + "WHERE event_type = 'IMPRESSION' AND created_at >= ? GROUP BY DATE(created_at)"),
                dailySeries("SELECT DATE(created_at) AS d, COUNT(*) AS v FROM ad_events "
                        + "WHERE event_type IN ('CLICK', 'REDIRECT') AND created_at >= ? "
                        + "GROUP BY DATE(created_at)"),
                placements
        );
    }

    /** Click-through rate as a percentage; a placement never shown has none. */
    private static double ctr(long clicks, long impressions) {
        return impressions == 0 ? 0d : Math.round(clicks * 10000d / impressions) / 100d;
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).optional().orElse(0L);
    }

    /**
     * Returns one point per day for the last {@value #SERIES_DAYS} days, filling
     * missing days with zero so the chart always renders a continuous line.
     */
    private List<ChartPoint> dailySeries(String sql) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.minusDays(SERIES_DAYS - 1L);

        List<DayValue> rows = jdbc.sql(sql)
                .param(from.atStartOfDay(ZoneOffset.UTC).toInstant())
                .query((rs, rowNum) -> new DayValue(rs.getDate("d").toLocalDate(), rs.getLong("v")))
                .list();

        List<ChartPoint> series = new ArrayList<>(SERIES_DAYS);
        for (int offset = 0; offset < SERIES_DAYS; offset++) {
            LocalDate day = from.plusDays(offset);
            long value = rows.stream()
                    .filter(row -> row.day().equals(day))
                    .mapToLong(DayValue::value)
                    .findFirst()
                    .orElse(0L);
            series.add(new ChartPoint(day.toString(), value));
        }
        return series;
    }

    private List<String> pendingTasks() {
        List<String> tasks = new ArrayList<>();
        addTask(tasks, "SELECT COUNT(*) FROM stories WHERE status = 'PENDING_REVIEW'",
                "truyện đang chờ duyệt");
        addTask(tasks, "SELECT COUNT(*) FROM reports WHERE status = 'OPEN'",
                "báo cáo chưa xử lý");
        addTask(tasks, "SELECT COUNT(*) FROM payments WHERE status = 'PENDING'",
                "giao dịch nạp đang chờ");
        return tasks;
    }

    private void addTask(List<String> tasks, String sql, String label) {
        long pending = count(sql);
        if (pending > 0) {
            tasks.add(pending + " " + label);
        }
    }

    private record DayValue(LocalDate day, long value) {}
}
