package com.storyplatform.gamification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("story_recommendations")
public class StoryRecommendation {
    @Id
    private UUID id;
    private UUID userId;
    private UUID storyId;
    private Long gemAmount;
    private Instant createdAt;

    public StoryRecommendation() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public Long getGemAmount() { return gemAmount; }
    public void setGemAmount(Long gemAmount) { this.gemAmount = gemAmount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
