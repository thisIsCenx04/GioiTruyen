package com.storyplatform.teams.api;

import com.storyplatform.admin.api.AdminStoryController;
import com.storyplatform.admin.application.dto.AdminDtos.AdminStoryRow;
import com.storyplatform.admin.application.dto.AdminDtos.UpsertStoryRequest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * The publisher-side counterpart to the admin dashboard.
 *
 * <p>Same shape of information an admin sees - headline stats, daily series,
 * a work queue, the story list - but every query is constrained to one team, so
 * a publisher only ever reads their own numbers. The admin surfaces stay
 * separate and keep requiring {@code SCOPE_ADMIN}; nothing here widens them.
 *
 * <p>Authorisation is membership-based rather than role-based. {@code users.role}
 * only distinguishes READER from ADMIN, so "may publish" is decided by an active
 * {@code team_members} row carrying a publishing role.
 */
@RestController
@RequestMapping("/teams/{teamId}")
public class TeamWorkspaceController {

    private static final int SERIES_DAYS = 7;
    private static final int DAILY_MEMBER_INVITE_LIMIT = 2;
    private static final int MAX_ACTIVE_TEAM_ACCOUNTS = 10;

    /** Team roles allowed to open the workspace. MEMBER is read-only elsewhere. */
    private static final Set<String> PUBLISHING_ROLES = Set.of("OWNER", "MANAGER", "EDITOR", "MEMBER");

    private final JdbcClient jdbc;

    /**
     * Story writes reuse the admin implementation rather than restating it.
     *
     * <p>Creating a story is the same job whoever asks for it: the slug rules,
     * genre and tag link tables, chapter numbering, the paid-chapter-needs-a-price
     * check and the guard against silently dropping chapters all have to behave
     * identically, and that logic is ~400 lines. Duplicating it here would mean
     * two versions drifting apart, with the publisher copy being the one nobody
     * notices is wrong.
     *
     * <p>What this controller adds is authorisation: it proves the caller may act
     * for the team, pins {@code teamId} to that team, and refuses to touch a story
     * belonging to anyone else - so a publisher can never reach another team's
     * catalogue through an endpoint that is otherwise the admin's.
     */
    private final AdminStoryController storyWriter;

    /** Shared with the reader-avatar upload, so both get the same checks. */
    private final com.storyplatform.admin.application.StoryMediaStorage mediaStorage;

    public TeamWorkspaceController(
            JdbcClient jdbc,
            AdminStoryController storyWriter,
            com.storyplatform.admin.application.StoryMediaStorage mediaStorage
    ) {
        this.jdbc = jdbc;
        this.storyWriter = storyWriter;
        this.mediaStorage = mediaStorage;
    }

    // ---------------------------------------------------------------- reads

    @GetMapping("/access")
    @Transactional(readOnly = true)
    public TeamAccess access(@PathVariable("teamId") String teamRef, @AuthenticationPrincipal Jwt jwt) {
        String teamId = resolveTeamId(teamRef);
        String role = requireMembershipOrAdmin(teamId, jwt);
        return new TeamAccess(teamId, teamName(teamId), role, canSeeOwnerSurfaces(role));
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public TeamOverview dashboard(@PathVariable("teamId") String teamRef, @AuthenticationPrincipal Jwt jwt) {
        String teamId = resolveTeamId(teamRef);
        String role = requireOwnerOrAdmin(teamId, jwt);

        TeamStats stats = new TeamStats(
                scalar("SELECT COALESCE(SUM(net_coin), 0) FROM team_ledger WHERE team_id = ?", teamId),
                scalar("""
                        SELECT COUNT(*) FROM story_views v
                        JOIN stories s ON s.id = v.story_id
                        WHERE s.team_id = ?
                        """, teamId),
                scalar("SELECT COUNT(*) FROM stories WHERE team_id = ?", teamId),
                scalar("SELECT COUNT(*) FROM stories WHERE team_id = ? AND status = 'PUBLISHED'", teamId),
                scalar("""
                        SELECT COUNT(*) FROM chapters c
                        JOIN stories s ON s.id = c.story_id
                        WHERE s.team_id = ?
                        """, teamId),
                scalar("SELECT COUNT(*) FROM team_follows WHERE team_id = ?", teamId)
        );

        return new TeamOverview(
                teamId,
                teamName(teamId),
                role,
                stats,
                dailySeries("""
                        SELECT DATE(created_at) AS d, COALESCE(SUM(net_coin), 0) AS v
                        FROM team_ledger
                        WHERE team_id = ? AND created_at >= ?
                        GROUP BY DATE(created_at)
                        """, teamId),
                dailySeries("""
                        SELECT DATE(v.viewed_at) AS d, COUNT(*) AS v
                        FROM story_views v
                        JOIN stories s ON s.id = v.story_id
                        WHERE s.team_id = ? AND v.viewed_at >= ?
                        GROUP BY DATE(v.viewed_at)
                        """, teamId),
                pendingTasks(teamId)
        );
    }

    /** The team's own catalogue, including drafts an admin list would also show. */
    @GetMapping("/stories")
    @Transactional(readOnly = true)
    public List<TeamStoryRow> stories(@PathVariable("teamId") String teamRef, @AuthenticationPrincipal Jwt jwt) {
        String teamId = resolveTeamId(teamRef);
        String role = requireMembershipOrAdmin(teamId, jwt);
        boolean showFinancials = canSeeOwnerSurfaces(role);
        return jdbc.sql("""
                SELECT s.id, s.slug, s.title, s.cover_url, s.status, s.progress_status,
                       s.story_format, s.story_type, s.view_count_cache, s.follow_count_cache,
                       s.favorite_count_cache, s.combo_price_xu, s.original_author,
                       -- The full synopsis as well as the 500-character teaser.
                       -- The edit form used to load the teaser and save it back
                       -- as the synopsis, so every edit clipped the
                       -- introduction to 500 characters and the loss was
                       -- permanent - one round trip through the form and the
                       -- rest of the text was gone.
                       s.short_description, s.description,
                       s.published_at, s.last_chapter_at, s.updated_at,
                       -- Genres and tags the story already carries. The edit
                       -- form has to load these back: without them it opened
                       -- with nothing ticked and no tags, and saving wrote that
                       -- emptiness over what the story had.
                       (SELECT GROUP_CONCAT(sg.genre_id) FROM story_genres sg
                         WHERE sg.story_id = s.id) AS category_ids,
                       (SELECT GROUP_CONCAT(st.label ORDER BY st.label SEPARATOR ',')
                          FROM story_tags st WHERE st.story_id = s.id) AS tag_labels,
                       (SELECT COUNT(*) FROM chapters c WHERE c.story_id = s.id) AS chapter_count,
                       (SELECT COUNT(*) FROM chapters c
                         WHERE c.story_id = s.id AND c.status = 'PUBLISHED') AS published_chapter_count,
                       -- Net xu this story earned the team. team_ledger holds
                       -- every credit, so unlocks, combos and donations are all
                       -- counted here without summing three tables separately.
                       COALESCE((SELECT SUM(tl.net_coin) FROM team_ledger tl
                                  WHERE tl.reference_type = 'CHAPTER_UNLOCK'
                                    AND tl.reference_id IN (
                                        SELECT cu.id FROM chapter_unlocks cu
                                          JOIN chapters ch ON ch.id = cu.chapter_id
                                         WHERE ch.story_id = s.id)), 0)
                     + COALESCE((SELECT SUM(d.team_net_coin) FROM donations d
                                  WHERE d.story_id = s.id), 0)
                     + COALESCE((SELECT SUM(scp.price_xu) FROM story_combo_purchases scp
                                  WHERE scp.story_id = s.id), 0) AS revenue_xu
                FROM stories s
                WHERE s.team_id = ?
                ORDER BY s.updated_at DESC
                """)
                .param(teamId)
                .query((rs, rowNum) -> new TeamStoryRow(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("title"),
                        rs.getString("cover_url"),
                        rs.getString("status"),
                        rs.getString("progress_status"),
                        rs.getString("story_format"),
                        rs.getString("story_type"),
                        rs.getLong("view_count_cache"),
                        rs.getLong("follow_count_cache"),
                        rs.getLong("favorite_count_cache"),
                        // getLong returns 0 for SQL NULL, so the null is read
                        // explicitly - 0 and "not configured" mean the same thing
                        // to the pricing rules but not to the edit form.
                        rs.getObject("combo_price_xu") == null ? null : rs.getLong("combo_price_xu"),
                        rs.getString("original_author"),
                        rs.getString("short_description"),

                        rs.getString("description"),
                        splitCsv(rs.getString("category_ids")),
                        splitCsv(rs.getString("tag_labels")),
                        rs.getInt("chapter_count"),
                        rs.getInt("published_chapter_count"),
                        instant(rs, "published_at"),
                        instant(rs, "last_chapter_at"),
                        instant(rs, "updated_at"),
                        showFinancials ? rs.getLong("revenue_xu") : 0L
                ))
                .list();
    }

    // --------------------------------------------------------------- writes

    /** Metadata-only create, mirroring the admin JSON endpoint. */
    @PostMapping(path = "/stories", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminStoryRow createStory(
            @PathVariable("teamId") String teamRef,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpsertStoryRequest request
    ) {
        String teamId = resolveTeamId(teamRef);
        requireMembership(teamId, jwt);
        return storyWriter.create(pinTeam(request, teamId));
    }

    /**
     * Create with a cover image and/or chapter files attached, the same multipart
     * form the admin workspace posts.
     */
    @PostMapping(path = "/stories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminStoryRow createStoryMultipart(
            @PathVariable("teamId") String teamRef,
            @AuthenticationPrincipal Jwt jwt,
            MultipartHttpServletRequest request
    ) {
        String teamId = resolveTeamId(teamRef);
        requireMembership(teamId, jwt);
        return storyWriter.createMultipart(requireFormTeamMatches(request, teamId));
    }

    @PutMapping(path = "/stories/{storyId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminStoryRow updateStory(
            @PathVariable("teamId") String teamRef,
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpsertStoryRequest request
    ) {
        String teamId = resolveTeamId(teamRef);
        requireMembership(teamId, jwt);
        requireStoryOwnedByTeam(teamId, storyId);
        return storyWriter.update(UUID.fromString(storyId), pinTeam(request, teamId));
    }

    @PutMapping(path = "/stories/{storyId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminStoryRow updateStoryMultipart(
            @PathVariable("teamId") String teamRef,
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt,
            MultipartHttpServletRequest request
    ) {
        String teamId = resolveTeamId(teamRef);
        requireMembership(teamId, jwt);
        requireStoryOwnedByTeam(teamId, storyId);
        return storyWriter.updateMultipart(
                UUID.fromString(storyId), requireFormTeamMatches(request, teamId));
    }

    /**
     * Hides the story rather than deleting it, so purchases and reading history
     * that point at it stay intact. Permanent deletion stays admin-only.
     */
    @DeleteMapping("/stories/{storyId}")
    public void hideStory(
            @PathVariable("teamId") String teamRef,
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String teamId = resolveTeamId(teamRef);
        String role = requireMembership(teamId, jwt);
        // Taking a published story off the site is not an editor's call.
        if (!Set.of("OWNER", "MANAGER").contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ chủ nhóm hoặc quản lý mới được ẩn truyện.");
        }
        requireStoryOwnedByTeam(teamId, storyId);
        storyWriter.archive(UUID.fromString(storyId));
    }

    /**
     * Removes a story and its chapters for good.
     *
     * <p>Hiding is the safe operation and stays the default; this exists because
     * a team that uploaded the wrong file, or a duplicate, otherwise carries it
     * in their list forever.
     *
     * <p>Refused once anyone has paid for any part of the story. A chapter
     * someone bought cannot be deleted without taking away what they paid for,
     * and the unlock rows reference it. Those stories can still be hidden, which
     * achieves the same thing for readers browsing the site.
     */
    @DeleteMapping("/stories/{storyId}/permanent")
    @Transactional
    public void deleteStory(
            @PathVariable("teamId") String teamRef,
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String teamId = resolveTeamId(teamRef);
        String role = requireMembership(teamId, jwt);
        if (!"OWNER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ chủ nhóm mới được xoá vĩnh viễn truyện.");
        }
        requireStoryOwnedByTeam(teamId, storyId);

        long unlocks = scalar("""
                SELECT COUNT(*) FROM chapter_unlocks cu
                JOIN chapters c ON c.id = cu.chapter_id
                WHERE c.story_id = ?
                """, storyId);
        long combos = scalar("SELECT COUNT(*) FROM story_combo_purchases WHERE story_id = ?", storyId);
        if (unlocks + combos > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Truyện đã có %d lượt mua nên không xoá được. Hãy dùng \"Ẩn truyện\" để gỡ khỏi trang."
                            .formatted(unlocks + combos));
        }

        // Chapters go first: the foreign key from chapters to stories is not
        // ON DELETE CASCADE, so the story row would refuse to go otherwise.
        jdbc.sql("DELETE FROM chapters WHERE story_id = ?").param(storyId).update();
        jdbc.sql("DELETE FROM story_genres WHERE story_id = ?").param(storyId).update();
        jdbc.sql("DELETE FROM story_tags WHERE story_id = ?").param(storyId).update();
        jdbc.sql("DELETE FROM stories WHERE id = ? AND team_id = ?")
                .params(storyId, teamId)
                .update();
    }

    /**
     * Forces the story onto the caller's team, whatever the body claimed. Without
     * this a publisher could post another team's id and create a story there.
     */
    private static UpsertStoryRequest pinTeam(UpsertStoryRequest request, String teamId) {
        return new UpsertStoryRequest(
                request.title(),
                request.slug(),
                request.authorName(),
                teamId,
                request.categoryId(),
                request.categoryIds(),
                request.synopsis(),
                request.summary(),
                request.contentType(),
                request.storyFormat(),
                request.storyType(),
                request.workflowStatus(),
                request.completionStatus(),
                request.tags(),
                request.comboPriceXu()
        );
    }

    /**
     * Checks the form names this team, and hands back a request whose
     * {@code teamId} parameter is the canonical id.
     *
     * <p>The workspace lives at {@code /teams/{slug}/stories}, and the form field
     * is filled from that same path parameter - so the form carried the slug
     * while the path had already been resolved to a UUID. Comparing the two as
     * strings rejected every publisher who reached their workspace by slug with
     * "Truyện phải thuộc nhóm bạn đang quản lý", which is precisely the team
     * they were managing. Only a reference resolving to a *different* team is a
     * real mismatch.
     *
     * <p>Rewriting the parameter matters as much as accepting it: the admin code
     * downstream reads {@code teamId} straight off the form and parses it as a
     * UUID, so a slug that got past this check would fail there instead.
     */
    private MultipartHttpServletRequest requireFormTeamMatches(
            MultipartHttpServletRequest request, String teamId) {
        String submitted = request.getParameter("teamId");
        if (submitted == null || submitted.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Thiếu teamId trong biểu mẫu.");
        }
        if (!resolveTeamId(submitted).equals(teamId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Truyện phải thuộc nhóm bạn đang quản lý.");
        }
        return submitted.equals(teamId) ? request : new TeamIdOverride(request, teamId);
    }

    /**
     * A multipart request whose {@code teamId} parameter reads as the canonical
     * team id, whatever the form actually submitted. Everything else - the
     * files above all - is delegated untouched.
     */
    private static final class TeamIdOverride
            extends jakarta.servlet.http.HttpServletRequestWrapper
            implements MultipartHttpServletRequest {

        private final MultipartHttpServletRequest delegate;
        private final String teamId;

        private TeamIdOverride(MultipartHttpServletRequest delegate, String teamId) {
            super(delegate);
            this.delegate = delegate;
            this.teamId = teamId;
        }

        @Override
        public String getParameter(String name) {
            return "teamId".equals(name) ? teamId : super.getParameter(name);
        }

        @Override
        public String[] getParameterValues(String name) {
            return "teamId".equals(name) ? new String[] { teamId } : super.getParameterValues(name);
        }

        @Override
        public java.util.Map<String, String[]> getParameterMap() {
            java.util.Map<String, String[]> merged =
                    new java.util.LinkedHashMap<>(super.getParameterMap());
            merged.put("teamId", new String[] { teamId });
            return java.util.Collections.unmodifiableMap(merged);
        }

        @Override
        public java.util.Iterator<String> getFileNames() {
            return delegate.getFileNames();
        }

        @Override
        public org.springframework.web.multipart.MultipartFile getFile(String name) {
            return delegate.getFile(name);
        }

        @Override
        public List<org.springframework.web.multipart.MultipartFile> getFiles(String name) {
            return delegate.getFiles(name);
        }

        @Override
        public java.util.Map<String, org.springframework.web.multipart.MultipartFile> getFileMap() {
            return delegate.getFileMap();
        }

        @Override
        public org.springframework.util.MultiValueMap<String,
                org.springframework.web.multipart.MultipartFile> getMultiFileMap() {
            return delegate.getMultiFileMap();
        }

        @Override
        public String getMultipartContentType(String paramOrFileName) {
            return delegate.getMultipartContentType(paramOrFileName);
        }

        @Override
        public org.springframework.http.HttpMethod getRequestMethod() {
            return delegate.getRequestMethod();
        }

        @Override
        public org.springframework.http.HttpHeaders getRequestHeaders() {
            return delegate.getRequestHeaders();
        }

        @Override
        public org.springframework.http.HttpHeaders getMultipartHeaders(String paramOrFileName) {
            return delegate.getMultipartHeaders(paramOrFileName);
        }
    }

    @GetMapping("/stories/{storyId}/chapters")
    @Transactional(readOnly = true)
    public List<TeamChapterRow> chapters(
            @PathVariable("teamId") String teamRef,
            @PathVariable String storyId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String teamId = resolveTeamId(teamRef);
        requireMembership(teamId, jwt);
        requireStoryOwnedByTeam(teamId, storyId);
        return jdbc.sql("""
                SELECT id, chapter_number, title, slug, access_type, coin_price, status,
                       published_at, updated_at, content, CHAR_LENGTH(COALESCE(content, '')) AS content_length
                FROM chapters
                WHERE story_id = ?
                ORDER BY chapter_number
                """)
                .param(storyId)
                .query((rs, rowNum) -> new TeamChapterRow(
                        rs.getString("id"),
                        rs.getBigDecimal("chapter_number"),
                        rs.getString("title"),
                        rs.getString("slug"),
                        rs.getString("access_type"),
                        rs.getLong("coin_price"),
                        rs.getString("status"),
                        rs.getInt("content_length"),
                        rs.getString("content"),
                        instant(rs, "published_at"),
                        instant(rs, "updated_at")
                ))
                .list();
    }

    /** Per-story revenue and views, the breakdown the payout figure comes from. */
    @GetMapping("/earnings")
    @Transactional(readOnly = true)
    public TeamEarnings earnings(@PathVariable("teamId") String teamRef, @AuthenticationPrincipal Jwt jwt) {
        String teamId = resolveTeamId(teamRef);
        requireOwnerOrAdmin(teamId, jwt);

        List<EarningRow> byType = jdbc.sql("""
                SELECT type, COALESCE(SUM(net_coin), 0) AS net, COUNT(*) AS entries
                FROM team_ledger
                WHERE team_id = ?
                GROUP BY type
                ORDER BY net DESC
                """)
                .param(teamId)
                .query((rs, rowNum) -> new EarningRow(
                        rs.getString("type"),
                        rs.getLong("net"),
                        rs.getLong("entries")
                ))
                .list();

        return new TeamEarnings(
                scalar("SELECT COALESCE(SUM(net_coin), 0) FROM team_ledger WHERE team_id = ?", teamId),
                scalar("""
                        SELECT COALESCE(SUM(net_coin), 0) FROM team_ledger
                        WHERE team_id = ? AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)
                        """, teamId),
                byType
        );
    }

    @GetMapping("/analytics")
    @Transactional(readOnly = true)
    public TeamAnalyticsReport analytics(
            @PathVariable("teamId") String teamRef,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "30D") String period
    ) {
        String teamId = resolveTeamId(teamRef);
        requireOwnerOrAdmin(teamId, jwt);

        int days = switch (period == null ? "" : period.toUpperCase(Locale.ROOT)) {
            case "7D" -> 7;
            case "90D" -> 90;
            default -> 30;
        };
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.minusDays(days - 1L);
        List<DayValue> rows = jdbc.sql("""
                        SELECT DATE(v.viewed_at) AS d, COUNT(*) AS v
                        FROM story_views v
                        JOIN stories s ON s.id = v.story_id
                        WHERE s.team_id = ? AND v.viewed_at >= ?
                        GROUP BY DATE(v.viewed_at)
                        """)
                .params(List.of(teamId, from.atStartOfDay(ZoneOffset.UTC).toInstant()))
                .query((rs, rowNum) -> new DayValue(rs.getDate("d").toLocalDate(), rs.getLong("v")))
                .list();

        List<AnalyticsBucket> series = new ArrayList<>(days);
        long total = 0L;
        for (int offset = 0; offset < days; offset++) {
            LocalDate day = from.plusDays(offset);
            long value = rows.stream()
                    .filter(row -> row.day().equals(day))
                    .mapToLong(DayValue::value)
                    .findFirst()
                    .orElse(0L);
            total += value;
            series.add(new AnalyticsBucket(
                    day.atStartOfDay(ZoneOffset.UTC).toInstant().toString(),
                    value,
                    0,
                    value,
                    value,
                    1.0
            ));
        }

        return new TeamAnalyticsReport(
                teamId,
                days == 7 ? "7D" : days == 90 ? "90D" : "30D",
                from.atStartOfDay(ZoneOffset.UTC).toInstant().toString(),
                today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString(),
                new AnalyticsTotals(total, total, 0, total == 0 ? 1.0 : 1.0),
                series,
                List.of()
        );
    }

    @GetMapping("/supporters")
    @Transactional(readOnly = true)
    public TeamSupporters supporters(@PathVariable("teamId") String teamRef, @AuthenticationPrincipal Jwt jwt) {
        String teamId = resolveTeamId(teamRef);
        requireOwnerOrAdmin(teamId, jwt);

        List<DonationSupporterRow> donations = jdbc.sql("""
                SELECT d.id, d.gross_coin, d.team_net_coin, d.message, d.created_at,
                       u.email, u.display_name, s.title AS story_title
                FROM donations d
                JOIN users u ON u.id = d.user_id
                LEFT JOIN stories s ON s.id = d.story_id
                WHERE d.team_id = ?
                ORDER BY d.created_at DESC
                LIMIT 100
                """)
                .param(teamId)
                .query((rs, rowNum) -> new DonationSupporterRow(
                        rs.getString("id"),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("story_title"),
                        rs.getLong("gross_coin"),
                        rs.getLong("team_net_coin"),
                        rs.getString("message"),
                        instant(rs, "created_at")
                ))
                .list();

        List<RecommendationSupporterRow> recommendations = jdbc.sql("""
                SELECT r.id, r.gem_amount, r.created_at,
                       u.email, u.display_name, s.title AS story_title
                FROM story_recommendations r
                JOIN stories s ON s.id = r.story_id
                JOIN users u ON u.id = r.user_id
                WHERE s.team_id = ?
                ORDER BY r.created_at DESC
                LIMIT 100
                """)
                .param(teamId)
                .query((rs, rowNum) -> new RecommendationSupporterRow(
                        rs.getString("id"),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("story_title"),
                        rs.getLong("gem_amount"),
                        instant(rs, "created_at")
                ))
                .list();

        return new TeamSupporters(donations, recommendations);
    }

    /* ------------------------------------------------------ team management */

    /** Roles allowed to change the team itself, as opposed to its catalogue. */
    private static final Set<String> TEAM_ADMIN_ROLES = Set.of("OWNER");

    public record TeamProfileRequest(String name, String avatarUrl, String description) {}
    public record InviteMemberRequest(String email, String memberRole) {}

    /**
     * Requires the caller to own the team, not merely publish for it.
     *
     * <p>Renaming a team or adding members changes who may act as the team,
     * which is a different power from editing its stories - an EDITOR should
     * not be able to grant themselves company.
     */
    private void requireOwner(String teamId, Jwt jwt) {
        String role = requireMembership(teamId, jwt);
        if (!TEAM_ADMIN_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Chỉ chủ nhóm mới sửa được thông tin nhóm và thêm thành viên.");
        }
    }

    /** Name, avatar and description. The slug is deliberately not editable:
     *  it is in every published story URL this team owns. */
    @PutMapping("/profile")
    @Transactional
    public TeamProfileRequest updateProfile(
            @PathVariable("teamId") String teamRef,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody TeamProfileRequest request
    ) {
        String teamId = resolveTeamId(teamRef);
        requireOwner(teamId, jwt);

        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên nhóm không được để trống.");
        }
        if (name.length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên nhóm tối đa 180 ký tự.");
        }

        jdbc.sql("""
                UPDATE teams
                SET name = ?, avatar_url = ?, description = ?, updated_at = NOW(3)
                WHERE id = ?
                """)
                .params(name,
                        request.avatarUrl() == null || request.avatarUrl().isBlank()
                                ? null : request.avatarUrl().trim(),
                        request.description() == null ? null : request.description().trim(),
                        teamId)
                .update();
        return new TeamProfileRequest(name, request.avatarUrl(), request.description());
    }

    /**
     * Uploads the team's avatar and stores the resulting path.
     *
     * <p>A file, not a URL. Asking an owner to paste a link meant finding a
     * host first, and any link they did paste could rot, point at something
     * else later, or leak the visitor's referrer to a third party. The image is
     * put through the same storage the reader avatar uses, so it inherits its
     * type and size checks rather than getting a second, weaker set.
     */
    @PostMapping(path = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public TeamProfileRequest uploadAvatar(
            @PathVariable("teamId") String teamRef,
            @AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.RequestParam("file")
            org.springframework.web.multipart.MultipartFile file
    ) {
        String teamId = resolveTeamId(teamRef);
        requireOwner(teamId, jwt);
        String url = mediaStorage.storeAvatar(file);
        jdbc.sql("UPDATE teams SET avatar_url = ?, updated_at = NOW(3) WHERE id = ?")
                .params(url, teamId)
                .update();
        return new TeamProfileRequest(null, url, null);
    }

    /**
     * Adds someone to the team by the email they registered with.
     *
     * <p>Email rather than a user id because that is what an owner actually
     * knows about the person they are inviting. An address with no account is
     * reported as such instead of failing silently - the usual cause is a typo
     * or the person not having signed up yet.
     *
     * <p>Re-inviting somebody who left reactivates their old row rather than
     * inserting a second one; (team_id, user_id) is the key.
     */
    @PostMapping("/members")
    @Transactional
    public TeamMemberRow inviteMember(
            @PathVariable("teamId") String teamRef,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody InviteMemberRequest request
    ) {
        String teamId = resolveTeamId(teamRef);
        requireOwner(teamId, jwt);
        String ownerId = jwt.getSubject();

        String email = request.email() == null ? "" : request.email().trim();
        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chưa nhập email thành viên.");
        }
        String userId = jdbc.sql("SELECT id FROM users WHERE email = ?")
                .param(email)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không có tài khoản nào dùng email \"%s\". Hãy nhờ họ đăng ký trước."
                                .formatted(email)));

        // OWNER is not grantable here: a team has one owner, and handing that
        // over is a transfer, not an invitation.
        String role = switch (request.memberRole() == null ? "" : request.memberRole().toUpperCase(Locale.ROOT)) {
            case "MANAGER" -> "MANAGER";
            case "EDITOR" -> "EDITOR";
            default -> "MEMBER";
        };

        String existingStatus = jdbc.sql("""
                SELECT status FROM team_members
                WHERE team_id = ? AND user_id = ?
                LIMIT 1
                """)
                .params(teamId, userId)
                .query(String.class)
                .optional()
                .orElse(null);

        if ("ACTIVE".equals(existingStatus)) {
            jdbc.sql("UPDATE team_members SET member_role = ? WHERE team_id = ? AND user_id = ?")
                    .params(role, teamId, userId)
                    .update();
        } else {
            long activeAccounts = scalar("""
                    SELECT COUNT(*) FROM team_members
                    WHERE team_id = ?
                      AND status = 'ACTIVE'
                    """, teamId);
            if (activeAccounts >= MAX_ACTIVE_TEAM_ACCOUNTS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Mỗi team chỉ được tối đa 10 tài khoản. Hãy xoá bớt thành viên trước khi thêm người mới.");
            }

            long addedToday = scalar("""
                    SELECT COUNT(*) FROM team_members
                    WHERE team_id = ?
                      AND member_role <> 'OWNER'
                      AND joined_at >= UTC_TIMESTAMP(3) - INTERVAL 1 DAY
                    """, teamId);
            if (addedToday >= DAILY_MEMBER_INVITE_LIMIT) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Mỗi team chỉ thêm tối đa 2 thành viên trong 24 giờ. Hãy thử lại sau.");
            }

            jdbc.sql("""
                    INSERT INTO team_members (team_id, user_id, member_role, status, added_by, joined_at, removed_at)
                    VALUES (?, ?, ?, 'ACTIVE', ?, NOW(3), NULL)
                    ON DUPLICATE KEY UPDATE
                        member_role = VALUES(member_role),
                        status = 'ACTIVE',
                        added_by = VALUES(added_by),
                        joined_at = VALUES(joined_at),
                        removed_at = NULL
                    """)
                    .params(teamId, userId, role, ownerId)
                    .update();
        }

        return jdbc.sql("""
                SELECT m.user_id, m.member_role, m.status, m.joined_at,
                       u.username, u.display_name, u.avatar_url
                FROM team_members m JOIN users u ON u.id = m.user_id
                WHERE m.team_id = ? AND m.user_id = ?
                """)
                .params(teamId, userId)
                .query((rs, rowNum) -> new TeamMemberRow(
                        rs.getString("user_id"), rs.getString("username"),
                        rs.getString("display_name"), rs.getString("avatar_url"),
                        rs.getString("member_role"), rs.getString("status"),
                        instant(rs, "joined_at")))
                .single();
    }

    /** Removes a member. The owner cannot remove themselves. */
    @DeleteMapping("/members/{userId}")
    @Transactional
    public void removeMember(
            @PathVariable("teamId") String teamRef,
            @PathVariable String userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String teamId = resolveTeamId(teamRef);
        requireOwner(teamId, jwt);
        if (userId.equals(jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chủ nhóm không thể tự gỡ mình khỏi nhóm.");
        }
        jdbc.sql("DELETE FROM team_members WHERE team_id = ? AND user_id = ?")
                .params(teamId, userId)
                .update();
    }

    @GetMapping("/members")
    @Transactional(readOnly = true)
    public List<TeamMemberRow> members(@PathVariable("teamId") String teamRef, @AuthenticationPrincipal Jwt jwt) {
        String teamId = resolveTeamId(teamRef);
        requireOwnerOrAdmin(teamId, jwt);
        return jdbc.sql("""
                SELECT m.user_id, m.member_role, m.status, m.joined_at,
                       u.username, u.display_name, u.avatar_url
                FROM team_members m
                JOIN users u ON u.id = m.user_id
                WHERE m.team_id = ? AND m.status = 'ACTIVE'
                ORDER BY FIELD(m.member_role, 'OWNER', 'MANAGER', 'EDITOR', 'MEMBER'), m.joined_at
                """)
                .param(teamId)
                .query((rs, rowNum) -> new TeamMemberRow(
                        rs.getString("user_id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("avatar_url"),
                        rs.getString("member_role"),
                        rs.getString("status"),
                        instant(rs, "joined_at")
                ))
                .list();
    }

    // -------------------------------------------------------- authorisation

    /**
     * Confirms the caller may act for this team and returns their team role.
     *
     * <p>404 rather than 403 for a non-member: whether a given team exists is not
     * something an outsider needs confirmed by probing this endpoint.
     */
    private String requireMembership(String teamId, Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập.");
        }
        String role = jdbc.sql("""
                SELECT member_role FROM team_members
                WHERE team_id = ? AND user_id = ? AND status = 'ACTIVE'
                """)
                .params(List.of(teamId, jwt.getSubject()))
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy nhóm."));

        if (!PUBLISHING_ROLES.contains(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Vai trò của bạn trong nhóm không được quản lý truyện.");
        }
        return role;
    }

    private String requireMembershipOrAdmin(String teamId, Jwt jwt) {
        if (isAdmin(jwt)) {
            return "ADMIN";
        }
        return requireMembership(teamId, jwt);
    }

    private String requireOwnerOrAdmin(String teamId, Jwt jwt) {
        String role = requireMembershipOrAdmin(teamId, jwt);
        if (!canSeeOwnerSurfaces(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác này.");
        }
        return role;
    }

    private static boolean canSeeOwnerSurfaces(String role) {
        return "OWNER".equals(role) || "ADMIN".equals(role);
    }

    private static boolean isAdmin(Jwt jwt) {
        if (jwt == null) {
            return false;
        }
        String role = jwt.getClaimAsString("role");
        String scope = jwt.getClaimAsString("scope");
        return "ADMIN".equalsIgnoreCase(role)
                || (scope != null && java.util.Arrays.stream(scope.split("\\s+"))
                .anyMatch("ADMIN"::equalsIgnoreCase));
    }

    /** Stops a member of team A reading team B's chapters by passing its story id. */
    private void requireStoryOwnedByTeam(String teamId, String storyId) {
        long owned = scalar("SELECT COUNT(*) FROM stories WHERE id = ? AND team_id = ?", storyId, teamId);
        if (owned == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy truyện trong nhóm.");
        }
    }

    // --------------------------------------------------------------- helpers

    /**
     * Accepts either a team id or its slug, and returns the id.
     *
     * <p>Lets the workspace live at {@code /teams/nha-dich-anh-trang/stories}
     * instead of exposing a raw UUID in the address bar, without changing any of
     * the queries below - they keep working off the canonical id. Existing links
     * that still carry a UUID keep resolving, so nothing bookmarked breaks.
     *
     * <p>A reference that matches nothing is reported as 404 by the membership
     * check that follows, not here: whether a team exists is not something an
     * outsider needs confirmed.
     */
    private String resolveTeamId(String teamRef) {
        if (teamRef == null || teamRef.isBlank()) {
            return "";
        }
        // A slug is never a UUID, so the shape decides which column to look in.
        if (teamRef.length() == 36 && teamRef.charAt(8) == '-') {
            return teamRef;
        }
        return jdbc.sql("SELECT id FROM teams WHERE slug = ?")
                .param(teamRef)
                .query(String.class)
                .optional()
                .orElse(teamRef);
    }

    /**
     * Splits a GROUP_CONCAT result into a list, empty when the story has none.
     *
     * <p>GROUP_CONCAT returns SQL NULL rather than an empty string when nothing
     * matched, and an empty list is what the edit form wants in that case.
     */
    private static List<String> splitCsv(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(joined.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String teamName(String teamId) {
        return jdbc.sql("SELECT name FROM teams WHERE id = ?")
                .param(teamId)
                .query(String.class)
                .optional()
                .orElse("");
    }

    private long scalar(String sql, Object... params) {
        return jdbc.sql(sql).params(List.of(params)).query(Long.class).optional().orElse(0L);
    }

    private static String instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().toString();
    }

    /**
     * One point per day for the last {@value #SERIES_DAYS} days, zero-filled so
     * the chart draws a continuous line instead of collapsing sparse days.
     */
    private List<ChartPoint> dailySeries(String sql, String teamId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.minusDays(SERIES_DAYS - 1L);

        List<DayValue> rows = jdbc.sql(sql)
                .params(List.of(teamId, from.atStartOfDay(ZoneOffset.UTC).toInstant()))
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

    /** The publisher's own work queue - only states they can act on. */
    private List<String> pendingTasks(String teamId) {
        List<String> tasks = new ArrayList<>();
        addTask(tasks, "SELECT COUNT(*) FROM stories WHERE team_id = ? AND status = 'DRAFT'",
                teamId, "truyện còn ở bản nháp");
        addTask(tasks, "SELECT COUNT(*) FROM stories WHERE team_id = ? AND status = 'PENDING_REVIEW'",
                teamId, "truyện đang chờ duyệt");
        addTask(tasks, "SELECT COUNT(*) FROM stories WHERE team_id = ? AND status = 'REJECTED'",
                teamId, "truyện bị từ chối cần sửa");
        addTask(tasks, """
                SELECT COUNT(*) FROM chapters c
                JOIN stories s ON s.id = c.story_id
                WHERE s.team_id = ? AND c.status = 'DRAFT'
                """, teamId, "chương chưa đăng");
        return tasks;
    }

    private void addTask(List<String> tasks, String sql, String teamId, String label) {
        long pending = scalar(sql, teamId);
        if (pending > 0) {
            tasks.add(pending + " " + label);
        }
    }

    // ------------------------------------------------------------------ dtos

    public record ChartPoint(String label, long value) {}

    public record TeamStats(
            long revenueXu,
            long views,
            long stories,
            long publishedStories,
            long chapters,
            long followers
    ) {}

    public record TeamOverview(
            String teamId,
            String teamName,
            /** The caller's role, so the UI can hide actions they cannot take. */
            String memberRole,
            TeamStats stats,
            List<ChartPoint> revenueSeries,
            List<ChartPoint> viewSeries,
            List<String> tasks
    ) {}

    public record TeamAccess(String teamId, String teamName, String memberRole, boolean ownerAccess) {}

    public record TeamStoryRow(
            String id,
            String slug,
            String title,
            String coverUrl,
            String status,
            String progressStatus,
            String storyFormat,
            String storyType,
            long viewCount,
            long followCount,
            long favoriteCount,
            /** Configured combo price, null when the story has no bundle deal. */
            Long comboPriceXu,
            String originalAuthor,
            String shortDescription,

            /** The full synopsis, not the 500-character teaser above. */

            String description,
            /** Genres already linked, so the edit form can tick them again. */
            List<String> categoryIds,
            /** Tag labels already linked, for the same reason. */
            List<String> tags,
            int chapterCount,
            int publishedChapterCount,
            String publishedAt,
            String lastChapterAt,
            String updatedAt,
            /** Net xu this story has earned the team: unlocks, combos, donations. */
            long revenueXu
    ) {}

    public record TeamChapterRow(
            String id,
            java.math.BigDecimal chapterNumber,
            String title,
            String slug,
            String accessType,
            long coinPrice,
            String status,
            int contentLength,
            /** The chapter text, so the publisher form can edit it in place
             *  rather than listing it read-only. */
            String content,
            String publishedAt,
            String updatedAt
    ) {}

    public record EarningRow(String type, long netCoin, long entries) {}

    public record TeamEarnings(long totalNetCoin, long last30DaysNetCoin, List<EarningRow> byType) {}

    public record AnalyticsTotals(
            long rawEvents,
            long validViews,
            long invalidViews,
            double qualityRate
    ) {}

    public record AnalyticsBucket(
            String start,
            long validViews,
            long invalidViews,
            long completedViews,
            long rawEvents,
            double qualityRate
    ) {}

    public record AnalyticsReason(String code, long count) {}

    public record TeamAnalyticsReport(
            String teamId,
            String period,
            String from,
            String to,
            AnalyticsTotals totals,
            List<AnalyticsBucket> series,
            List<AnalyticsReason> reasons
    ) {}

    public record DonationSupporterRow(
            String id,
            String userEmail,
            String displayName,
            String storyTitle,
            long grossCoin,
            long teamNetCoin,
            String message,
            String createdAt
    ) {}

    public record RecommendationSupporterRow(
            String id,
            String userEmail,
            String displayName,
            String storyTitle,
            long gemAmount,
            String createdAt
    ) {}

    public record TeamSupporters(
            List<DonationSupporterRow> donations,
            List<RecommendationSupporterRow> recommendations
    ) {}

    public record TeamMemberRow(
            String userId,
            String username,
            String displayName,
            String avatarUrl,
            String memberRole,
            String status,
            String joinedAt
    ) {}

    private record DayValue(LocalDate day, long value) {}
}
