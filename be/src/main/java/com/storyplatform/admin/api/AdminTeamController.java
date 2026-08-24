package com.storyplatform.admin.api;

import static com.storyplatform.admin.application.dto.AdminDtos.timestamp;

import com.storyplatform.admin.application.dto.AdminDtos.AdminTeamRow;
import com.storyplatform.admin.application.dto.AdminDtos.UpsertTeamRequest;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.teams.domain.Team;
import com.storyplatform.teams.domain.TeamStatus;
import com.storyplatform.teams.infrastructure.TeamRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/content/teams")
public class AdminTeamController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AdminTeamController.class);

    private static final String LIST_SQL = """
            SELECT t.id, t.slug, t.name, t.description, t.status, t.created_at, t.updated_at, t.created_by,
                   u.display_name AS owner_name,
                   (SELECT COUNT(*) FROM team_members m
                     WHERE m.team_id = t.id AND m.status = 'ACTIVE') AS member_count
            FROM teams t
            LEFT JOIN users u ON u.id = t.created_by
            ORDER BY t.updated_at DESC
            """;

    private final TeamRepository teamRepository;
    private final JdbcClient jdbc;

    public AdminTeamController(TeamRepository teamRepository, JdbcClient jdbc) {
        this.teamRepository = teamRepository;
        this.jdbc = jdbc;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<AdminTeamRow> list() {
        return jdbc.sql(LIST_SQL)
                .query((rs, rowNum) -> new AdminTeamRow(
                        rs.getString("id"),
                        rs.getString("slug"),
                        rs.getString("name"),
                        AdminCategoryController.nullToEmpty(rs.getString("owner_name")),
                        AdminCategoryController.nullToEmpty(rs.getString("created_by")),
                        AdminCategoryController.nullToEmpty(rs.getString("description")),
                        rs.getString("status"),
                        rs.getLong("member_count"),
                        timestamp(rs, "updated_at"),
                        timestamp(rs, "created_at")
                ))
                .list();
    }

    @PostMapping
    @Transactional
    public AdminTeamRow create(@RequestBody UpsertTeamRequest request) {
        AdminCategoryController.requireText(request.name(), "name");
        AdminCategoryController.requireText(request.ownerUserId(), "ownerUserId");

        UUID ownerId = AdminStoryController.parseUuid(request.ownerUserId(), "ownerUserId");
        requireUserExists(ownerId);

        String slug = AdminCategoryController.slugOrDerive(request.slug(), request.name());
        requireUniqueSlug(slug, null);

        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        TeamStatus status = AdminStoryController.parseEnum(TeamStatus.class, request.state(), TeamStatus.ACTIVE);

        // Ids are assigned here, so an explicit INSERT is used; repository.save()
        // would treat the populated id as an existing row and emit an UPDATE.
        jdbc.sql("""
                        INSERT INTO teams (id, name, slug, description, status, created_by, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(id.toString(), request.name().trim(), slug,
                        AdminCategoryController.nullToEmpty(request.description()),
                        status.name(), ownerId.toString(),
                        java.sql.Timestamp.from(now), java.sql.Timestamp.from(now))
                .update();

        ensureOwnerMembership(id, ownerId);
        return toRow(find(id));
    }

    @PutMapping("/{id}")
    @Transactional
    public AdminTeamRow update(@PathVariable UUID id, @RequestBody UpsertTeamRequest request) {
        Team team = find(id);
        AdminCategoryController.requireText(request.name(), "name");

        String slug = AdminCategoryController.slugOrDerive(request.slug(), request.name());
        requireUniqueSlug(slug, id);

        if (request.ownerUserId() != null && !request.ownerUserId().isBlank()) {
            UUID ownerId = AdminStoryController.parseUuid(request.ownerUserId(), "ownerUserId");
            requireUserExists(ownerId);
            team.setCreatedBy(ownerId);
            ensureOwnerMembership(id, ownerId);
        }

        team.setName(request.name().trim());
        team.setSlug(slug);
        team.setDescription(AdminCategoryController.nullToEmpty(request.description()));
        if (request.state() != null && !request.state().isBlank()) {
            team.setStatus(AdminStoryController.parseEnum(TeamStatus.class, request.state(), TeamStatus.ACTIVE));
        }
        team.setUpdatedAt(Instant.now());
        return toRow(teamRepository.save(team));
    }

    /**
     * Teams own stories with ON DELETE RESTRICT, so archiving disables the team
     * instead of deleting the row.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public void archive(@PathVariable UUID id) {
        Team team = find(id);
        team.setStatus(TeamStatus.DISABLED);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);
    }

    /**
     * Removes a team outright. Refused while it still owns stories: those rows
     * are ON DELETE RESTRICT, and deleting around them would orphan a library
     * readers are still using.
     */
    @DeleteMapping("/{id}/permanent")
    @Transactional
    public void deletePermanently(@PathVariable UUID id) {
        Team team = find(id);
        long stories = jdbc.sql("SELECT COUNT(*) FROM stories WHERE team_id = ?")
                .param(id.toString())
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (stories > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "team.has_stories",
                    "Team still owns stories",
                    ("Team \"%s\" đang sở hữu %d truyện nên không thể xóa. "
                            + "Hãy chuyển hoặc xóa các truyện đó trước, hoặc dùng \"Tạm khóa\".")
                            .formatted(team.getName(), stories));
        }
        // team_members cascades from the teams row.
        jdbc.sql("DELETE FROM teams WHERE id = ?").param(id.toString()).update();
    }

    /**
     * Đặt một người làm chủ nhóm, và chỉ một người.
     *
     * <p>Trước đây hàm này chỉ nâng người mới lên OWNER mà không hạ người cũ
     * xuống, nên mỗi lần chuyển quyền sở hữu lại đẻ thêm một chủ nhóm nữa.
     *
     * <p>Đó không chỉ là cái nhãn hiện sai trên sổ thành viên. Doanh thu nhóm chảy
     * về ví của chủ nhóm, mà chỗ chọn chủ nhóm lại lấy {@code ORDER BY joined_at
     * LIMIT 1} — tức là người vào trước. Sau một lần chuyển quyền, tiền vẫn chạy về
     * ví chủ cũ chứ không về chủ mới.
     *
     * <p>Chủ cũ được hạ xuống QUẢN LÝ chứ không bị gỡ khỏi nhóm: họ vẫn đang làm
     * việc ở đó, chuyển quyền sở hữu không phải là đuổi người.
     */
    private void ensureOwnerMembership(UUID teamId, UUID ownerId) {
        int demoted = jdbc.sql("""
                        UPDATE team_members
                           SET member_role = 'MANAGER'
                         WHERE team_id = ? AND user_id <> ? AND member_role = 'OWNER'
                        """)
                .params(teamId.toString(), ownerId.toString())
                .update();
        if (demoted > 0) {
            log.info("Chuyển quyền sở hữu nhóm {}: hạ {} chủ nhóm cũ xuống quản lý", teamId, demoted);
        }

        long existing = jdbc.sql("SELECT COUNT(*) FROM team_members WHERE team_id = ? AND user_id = ?")
                .params(teamId.toString(), ownerId.toString())
                .query(Long.class).optional().orElse(0L);
        if (existing > 0) {
            jdbc.sql("UPDATE team_members SET member_role = 'OWNER', status = 'ACTIVE', removed_at = NULL "
                            + "WHERE team_id = ? AND user_id = ?")
                    .params(teamId.toString(), ownerId.toString())
                    .update();
            return;
        }
        jdbc.sql("INSERT INTO team_members (id, team_id, user_id, member_role, status, added_by, joined_at) "
                        + "VALUES (?, ?, ?, 'OWNER', 'ACTIVE', ?, ?)")
                .params(UUID.randomUUID().toString(), teamId.toString(), ownerId.toString(),
                        ownerId.toString(), java.sql.Timestamp.from(Instant.now()))
                .update();
    }

    private void requireUserExists(UUID userId) {
        long exists = jdbc.sql("SELECT COUNT(*) FROM users WHERE id = ?")
                .param(userId.toString()).query(Long.class).optional().orElse(0L);
        if (exists == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "team.invalid_owner",
                    "Unknown user", "ownerUserId does not match an existing user");
        }
    }

    private void requireUniqueSlug(String slug, UUID excludedId) {
        long taken = excludedId == null
                ? jdbc.sql("SELECT COUNT(*) FROM teams WHERE slug = ?")
                        .param(slug).query(Long.class).optional().orElse(0L)
                : jdbc.sql("SELECT COUNT(*) FROM teams WHERE slug = ? AND id <> ?")
                        .params(slug, excludedId.toString()).query(Long.class).optional().orElse(0L);
        if (taken > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "team.slug_taken",
                    "Slug already used", "Slug already used by another team");
        }
    }

    private Team find(UUID id) {
        return teamRepository.findById(id).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "team.not_found", "Team not found", "Team not found"));
    }

    private AdminTeamRow toRow(Team team) {
        String ownerName = jdbc.sql("SELECT display_name FROM users WHERE id = ?")
                .param(team.getCreatedBy().toString()).query(String.class).optional().orElse("");
        long memberCount = jdbc.sql("SELECT COUNT(*) FROM team_members WHERE team_id = ? AND status = 'ACTIVE'")
                .param(team.getId().toString()).query(Long.class).optional().orElse(0L);

        return new AdminTeamRow(
                team.getId().toString(),
                team.getSlug(),
                team.getName(),
                AdminCategoryController.nullToEmpty(ownerName),
                team.getCreatedBy().toString(),
                AdminCategoryController.nullToEmpty(team.getDescription()),
                team.getStatus().name(),
                memberCount,
                team.getUpdatedAt() == null ? null : team.getUpdatedAt().toString(),
                team.getCreatedAt() == null ? null : team.getCreatedAt().toString()
        );
    }
}
