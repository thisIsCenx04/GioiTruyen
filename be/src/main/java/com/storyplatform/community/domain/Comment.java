package com.storyplatform.community.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("comments")
public class Comment {
    @Id
    private UUID id;
    private UUID userId;
    private UUID storyId;
    private UUID chapterId;
    private UUID parentId;
    private String content;
    private GenericContentStatus status;
    private Long likeCountCache;
    private Instant createdAt;
    private Instant updatedAt;

    public Comment() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public UUID getChapterId() { return chapterId; }
    public void setChapterId(UUID chapterId) { this.chapterId = chapterId; }

    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public GenericContentStatus getStatus() { return status; }
    public void setStatus(GenericContentStatus status) { this.status = status; }

    public Long getLikeCountCache() { return likeCountCache; }
    public void setLikeCountCache(Long likeCountCache) { this.likeCountCache = likeCountCache; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
