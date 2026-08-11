package com.storyplatform.catalog.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("stories")
public class Story {
    @Id
    private UUID id;
    private UUID teamId;
    private UUID createdBy;
    private String title;
    private String slug;
    private String originalTitle;
    private String originalAuthor;
    private String shortDescription;
    private String description;
    private String coverUrl;
    private String bannerUrl;
    private StoryContentType contentType;
    private StoryFormat storyFormat;
    private StoryType storyType;
    private StoryStatus status;
    private StoryProgressStatus progressStatus;
    private String ageRating;
    private Instant publishedAt;
    private Instant lastChapterAt;
    private Long viewCountCache;
    private Long followCountCache;
    private Long favoriteCountCache;
    private Long recommendationGemCache;
    private Instant createdAt;
    private Instant updatedAt;

    public Story() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getOriginalTitle() { return originalTitle; }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }

    public String getOriginalAuthor() { return originalAuthor; }
    public void setOriginalAuthor(String originalAuthor) { this.originalAuthor = originalAuthor; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }

    public StoryContentType getContentType() { return contentType; }
    public void setContentType(StoryContentType contentType) { this.contentType = contentType; }

    public StoryFormat getStoryFormat() { return storyFormat; }
    public void setStoryFormat(StoryFormat storyFormat) { this.storyFormat = storyFormat; }

    public StoryType getStoryType() { return storyType; }
    public void setStoryType(StoryType storyType) { this.storyType = storyType; }

    public StoryStatus getStatus() { return status; }
    public void setStatus(StoryStatus status) { this.status = status; }

    public StoryProgressStatus getProgressStatus() { return progressStatus; }
    public void setProgressStatus(StoryProgressStatus progressStatus) { this.progressStatus = progressStatus; }

    public String getAgeRating() { return ageRating; }
    public void setAgeRating(String ageRating) { this.ageRating = ageRating; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public Instant getLastChapterAt() { return lastChapterAt; }
    public void setLastChapterAt(Instant lastChapterAt) { this.lastChapterAt = lastChapterAt; }

    public Long getViewCountCache() { return viewCountCache; }
    public void setViewCountCache(Long viewCountCache) { this.viewCountCache = viewCountCache; }

    public Long getFollowCountCache() { return followCountCache; }
    public void setFollowCountCache(Long followCountCache) { this.followCountCache = followCountCache; }

    public Long getFavoriteCountCache() { return favoriteCountCache; }
    public void setFavoriteCountCache(Long favoriteCountCache) { this.favoriteCountCache = favoriteCountCache; }

    public Long getRecommendationGemCache() { return recommendationGemCache; }
    public void setRecommendationGemCache(Long recommendationGemCache) { this.recommendationGemCache = recommendationGemCache; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
