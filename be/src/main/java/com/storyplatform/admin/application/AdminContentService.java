package com.storyplatform.admin.application;

import com.storyplatform.identity.application.port.PasswordHasher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AdminContentService {

    private final JdbcClient jdbc;
    private final PasswordHasher passwords;
    private final Clock clock;

    public AdminContentService(
            JdbcClient jdbc,
            PasswordHasher passwords
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public String createStory(StoryCommand command) {
        String id = UUID.randomUUID().toString();
        String revisionId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        int changed = jdbc.sql("""
                INSERT INTO stories (
                    id, team_id, slug, title, synopsis, author_name,
                    origin, language, completion_status, workflow_status,
                    current_revision, published_at, created_at, updated_at,
                    version
                ) VALUES (
                    :id, :teamId, :slug, :title, :synopsis, :authorName,
                    'ORIGINAL', 'vi', :completionStatus, :workflowStatus,
                    :revisionId,
                    CASE WHEN :workflowStatus = 'PUBLISHED' THEN :now ELSE NULL END,
                    :now, :now, 0
                )
                """)
                .param("id", id)
                .param("teamId", command.teamId())
                .param("slug", command.slug())
                .param("title", command.title())
                .param("synopsis", command.synopsis())
                .param("authorName", command.authorName())
                .param("completionStatus", command.completionStatus())
                .param("workflowStatus", command.workflowStatus())
                .param("revisionId", revisionId)
                .param("now", now)
                .update();
        requireChanged(changed, "STORY_CREATE_FAILED");
        replaceStoryCategory(id, command.categoryId());
        return id;
    }

    @Transactional
    public void updateStory(String id, StoryCommand command) {
        int changed = jdbc.sql("""
                UPDATE stories
                SET team_id = :teamId,
                    slug = :slug,
                    title = :title,
                    synopsis = :synopsis,
                    author_name = :authorName,
                    completion_status = :completionStatus,
                    workflow_status = :workflowStatus,
                    published_at = CASE
                        WHEN :workflowStatus = 'PUBLISHED'
                            THEN COALESCE(published_at, :now)
                        ELSE published_at
                    END,
                    updated_at = :now,
                    version = version + 1
                WHERE id = :id
                """)
                .param("id", id)
                .param("teamId", command.teamId())
                .param("slug", command.slug())
                .param("title", command.title())
                .param("synopsis", command.synopsis())
                .param("authorName", command.authorName())
                .param("completionStatus", command.completionStatus())
                .param("workflowStatus", command.workflowStatus())
                .param("now", clock.instant())
                .update();
        requireChanged(changed, "STORY_NOT_FOUND");
        replaceStoryCategory(id, command.categoryId());
    }

    @Transactional
    public void archiveStory(String id) {
        int changed = jdbc.sql("""
                UPDATE stories
                SET workflow_status = 'ARCHIVED',
                    updated_at = :now,
                    version = version + 1
                WHERE id = :id
                """)
                .param("id", id)
                .param("now", clock.instant())
                .update();
        requireChanged(changed, "STORY_NOT_FOUND");
    }

    public String createCategory(CategoryCommand command) {
        String id = UUID.randomUUID().toString();
        int changed = jdbc.sql("""
                INSERT INTO categories (
                    id, slug, name, description, group_key, group_order,
                    sort_order, active, version
                ) VALUES (
                    :id, :slug, :name, :description, 'GENRE', 10,
                    :sortOrder, :active, 1
                )
                """)
                .param("id", id)
                .param("slug", command.slug())
                .param("name", command.name())
                .param("description", command.description())
                .param("sortOrder", command.sortOrder())
                .param("active", command.active())
                .update();
        requireChanged(changed, "CATEGORY_CREATE_FAILED");
        return id;
    }

    public void updateCategory(String id, CategoryCommand command) {
        int changed = jdbc.sql("""
                UPDATE categories
                SET slug = :slug,
                    name = :name,
                    description = :description,
                    sort_order = :sortOrder,
                    active = :active,
                    version = version + 1
                WHERE id = :id
                """)
                .param("id", id)
                .param("slug", command.slug())
                .param("name", command.name())
                .param("description", command.description())
                .param("sortOrder", command.sortOrder())
                .param("active", command.active())
                .update();
        requireChanged(changed, "CATEGORY_NOT_FOUND");
    }

    public void archiveCategory(String id) {
        int changed = jdbc.sql("""
                UPDATE categories
                SET active = FALSE, version = version + 1
                WHERE id = :id
                """)
                .param("id", id)
                .update();
        requireChanged(changed, "CATEGORY_NOT_FOUND");
    }

    @Transactional
    public String createTeam(TeamCommand command) {
        String id = UUID.randomUUID().toString();
        Instant now = clock.instant();
        int changed = jdbc.sql("""
                INSERT INTO teams (
                    id, slug, name, description, owner_user_id, state,
                    created_at, updated_at, version
                ) VALUES (
                    :id, :slug, :name, :description, :ownerUserId, :state,
                    :now, :now, 0
                )
                """)
                .param("id", id)
                .param("slug", command.slug())
                .param("name", command.name())
                .param("description", command.description())
                .param("ownerUserId", command.ownerUserId())
                .param("state", command.state())
                .param("now", now)
                .update();
        requireChanged(changed, "TEAM_CREATE_FAILED");
        jdbc.sql("""
                INSERT INTO team_memberships (
                    team_id, user_id, role, permissions, state,
                    joined_at, updated_at, version
                ) VALUES (
                    :teamId, :ownerUserId, 'OWNER',
                    JSON_ARRAY('STORY_CREATE', 'STORY_EDIT', 'MEMBER_MANAGE'),
                    'ACTIVE', :now, :now, 0
                )
                """)
                .param("teamId", id)
                .param("ownerUserId", command.ownerUserId())
                .param("now", now)
                .update();
        return id;
    }

    public void updateTeam(String id, TeamCommand command) {
        int changed = jdbc.sql("""
                UPDATE teams
                SET slug = :slug,
                    name = :name,
                    description = :description,
                    owner_user_id = :ownerUserId,
                    state = :state,
                    updated_at = :now,
                    version = version + 1
                WHERE id = :id
                """)
                .param("id", id)
                .param("slug", command.slug())
                .param("name", command.name())
                .param("description", command.description())
                .param("ownerUserId", command.ownerUserId())
                .param("state", command.state())
                .param("now", clock.instant())
                .update();
        requireChanged(changed, "TEAM_NOT_FOUND");
    }

    public void archiveTeam(String id) {
        int changed = jdbc.sql("""
                UPDATE teams
                SET state = 'SUSPENDED', updated_at = :now,
                    version = version + 1
                WHERE id = :id
                """)
                .param("id", id)
                .param("now", clock.instant())
                .update();
        requireChanged(changed, "TEAM_NOT_FOUND");
    }

    @Transactional
    public String createUser(UserCommand command) {
        String id = UUID.randomUUID().toString();
        Instant now = clock.instant();
        jdbc.sql("""
                INSERT INTO users (
                    id, email_normalized, password_hash, state,
                    security_version, accepted_consent_version,
                    consent_accepted_at, created_at, updated_at, version
                ) VALUES (
                    :id, :email, :passwordHash, :state, 1,
                    '2026-07-24', :now, :now, :now, 0
                )
                """)
                .param("id", id)
                .param("email", command.email().strip().toLowerCase())
                .param("passwordHash", passwords.hash(command.password()))
                .param("state", command.state())
                .param("now", now)
                .update();
        jdbc.sql("""
                INSERT INTO user_profiles (
                    user_id, display_name, bio, created_at, updated_at, version
                ) VALUES (:id, :displayName, :bio, :now, :now, 0)
                """)
                .param("id", id)
                .param("displayName", command.displayName())
                .param("bio", command.bio())
                .param("now", now)
                .update();
        replaceRoles(id, command.roles());
        return id;
    }

    @Transactional
    public void updateUser(String id, UserCommand command) {
        int changed = jdbc.sql("""
                UPDATE users
                SET email_normalized = :email,
                    state = :state,
                    updated_at = :now,
                    version = version + 1
                WHERE id = :id
                """)
                .param("id", id)
                .param("email", command.email().strip().toLowerCase())
                .param("state", command.state())
                .param("now", clock.instant())
                .update();
        requireChanged(changed, "USER_NOT_FOUND");
        jdbc.sql("""
                UPDATE user_profiles
                SET display_name = :displayName,
                    bio = :bio,
                    updated_at = :now,
                    version = version + 1
                WHERE user_id = :id
                """)
                .param("id", id)
                .param("displayName", command.displayName())
                .param("bio", command.bio())
                .param("now", clock.instant())
                .update();
        replaceRoles(id, command.roles());
    }

    public void archiveUser(String id) {
        int changed = jdbc.sql("""
                UPDATE users
                SET state = 'SUSPENDED',
                    security_version = security_version + 1,
                    updated_at = :now,
                    version = version + 1
                WHERE id = :id
                """)
                .param("id", id)
                .param("now", clock.instant())
                .update();
        requireChanged(changed, "USER_NOT_FOUND");
    }

    public String createLedgerEntry(LedgerCommand command) {
        String id = UUID.randomUUID().toString();
        int changed = jdbc.sql("""
                INSERT INTO ledger_entries (
                    id, user_id, entry_type, amount_xu, reference_type,
                    reference_id, description, created_at
                ) VALUES (
                    :id, :userId, :entryType, :amountXu, :referenceType,
                    :referenceId, :description, :now
                )
                """)
                .param("id", id)
                .param("userId", command.userId())
                .param("entryType", command.entryType())
                .param("amountXu", command.amountXu())
                .param("referenceType", command.referenceType())
                .param("referenceId", command.referenceId())
                .param("description", command.description())
                .param("now", clock.instant())
                .update();
        requireChanged(changed, "LEDGER_ENTRY_CREATE_FAILED");
        return id;
    }

    @Transactional
    public String reverseLedgerEntry(String id, String reason) {
        LedgerValue original = jdbc.sql("""
                SELECT user_id, amount_xu, reference_type, reference_id
                FROM ledger_entries
                WHERE id = :id
                """)
                .param("id", id)
                .query((result, rowNumber) -> new LedgerValue(
                        result.getString("user_id"),
                        result.getLong("amount_xu"),
                        result.getString("reference_type"),
                        result.getString("reference_id")
                ))
                .optional()
                .orElseThrow(() -> new AdminContentException(
                        "LEDGER_ENTRY_NOT_FOUND"
                ));
        return createLedgerEntry(new LedgerCommand(
                original.userId(),
                "REVERSAL",
                -original.amountXu(),
                original.referenceType(),
                original.referenceId(),
                "Hoàn ngược bút toán " + id + ": " + reason
        ));
    }

    private void replaceStoryCategory(String storyId, String categoryId) {
        jdbc.sql("DELETE FROM story_categories WHERE story_id = :storyId")
                .param("storyId", storyId)
                .update();
        jdbc.sql("""
                INSERT INTO story_categories (story_id, category_id)
                VALUES (:storyId, :categoryId)
                """)
                .param("storyId", storyId)
                .param("categoryId", categoryId)
                .update();
    }

    private void replaceRoles(String userId, List<String> roles) {
        jdbc.sql("DELETE FROM user_roles WHERE user_id = :userId")
                .param("userId", userId)
                .update();
        roles.stream().distinct().forEach(role -> jdbc.sql("""
                INSERT INTO user_roles (user_id, role)
                VALUES (:userId, :role)
                """)
                .param("userId", userId)
                .param("role", role)
                .update());
    }

    private static void requireChanged(int changed, String code) {
        if (changed != 1) {
            throw new AdminContentException(code);
        }
    }

    public record StoryCommand(
            String teamId,
            String categoryId,
            String slug,
            String title,
            String synopsis,
            String authorName,
            String workflowStatus,
            String completionStatus
    ) {
    }

    public record CategoryCommand(
            String slug,
            String name,
            String description,
            int sortOrder,
            boolean active
    ) {
    }

    public record TeamCommand(
            String slug,
            String name,
            String description,
            String ownerUserId,
            String state
    ) {
    }

    public record UserCommand(
            String email,
            String password,
            String displayName,
            String bio,
            String state,
            List<String> roles
    ) {
        public UserCommand {
            roles = List.copyOf(roles);
        }
    }

    public record LedgerCommand(
            String userId,
            String entryType,
            long amountXu,
            String referenceType,
            String referenceId,
            String description
    ) {
    }

    private record LedgerValue(
            String userId,
            long amountXu,
            String referenceType,
            String referenceId
    ) {
    }
}
