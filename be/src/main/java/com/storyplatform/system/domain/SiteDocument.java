package com.storyplatform.system.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("site_documents")
public class SiteDocument {
    @Id
    private UUID id;
    private SiteDocumentType type;
    private String title;
    private String content;
    private Integer version;
    private Boolean isPublished;
    private UUID createdBy;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public SiteDocument() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public SiteDocumentType getType() { return type; }
    public void setType(SiteDocumentType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Boolean getIsPublished() { return isPublished; }
    public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
