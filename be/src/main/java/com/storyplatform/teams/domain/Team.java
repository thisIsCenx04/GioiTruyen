package com.storyplatform.teams.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("teams")
public class Team {
    @Id
    private UUID id;
    private String name;
    private String slug;
    private String avatarUrl;
    private String coverUrl;
    private String description;
    private TeamStatus status;
    /**
     * Khi ban quản trị xác nhận nhóm, hoặc null khi chưa xác nhận.
     *
     * <p>Tách khỏi {@link #status}: tạm khoá một nhóm rồi mở lại không được phép
     * làm mất dấu xác minh, nên hai thứ này là hai cột.
     */
    private Instant verifiedAt;
    /** Quản trị viên đã bấm xác nhận, để còn truy được khi có khiếu nại. */
    private UUID verifiedBy;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public Team() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TeamStatus getStatus() { return status; }
    public void setStatus(TeamStatus status) { this.status = status; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }

    /** True khi nhóm đã được xác nhận - thứ dấu tích xanh hiển thị. */
    public boolean isVerified() { return verifiedAt != null; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
