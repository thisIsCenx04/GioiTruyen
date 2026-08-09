package com.storyplatform.engagement.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("story_follows")
public class StoryFollow {
    private UUID userId;
    private UUID storyId;
    private Boolean notifyNewChapter;
    private Instant createdAt;

    public StoryFollow() {}

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public Boolean getNotifyNewChapter() { return notifyNewChapter; }
    public void setNotifyNewChapter(Boolean notifyNewChapter) { this.notifyNewChapter = notifyNewChapter; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
