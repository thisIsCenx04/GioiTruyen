package com.storyplatform.catalog.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("chapters")
public class Chapter {
    @Id
    private UUID id;
    private UUID storyId;
    private java.math.BigDecimal chapterNumber;
    private String title;
    private String slug;
    private String content;
    private String shortDescription;
    private ChapterAccessType accessType;
    private Long coinPrice;
    private ChapterStatus status;
    private Instant publishedAt;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public Chapter() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public java.math.BigDecimal getChapterNumber() { return chapterNumber; }
    public void setChapterNumber(java.math.BigDecimal chapterNumber) { this.chapterNumber = chapterNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public ChapterAccessType getAccessType() { return accessType; }
    public void setAccessType(ChapterAccessType accessType) { this.accessType = accessType; }

    public Long getCoinPrice() { return coinPrice; }
    public void setCoinPrice(Long coinPrice) { this.coinPrice = coinPrice; }

    public ChapterStatus getStatus() { return status; }
    public void setStatus(ChapterStatus status) { this.status = status; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
