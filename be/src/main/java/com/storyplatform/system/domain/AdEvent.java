package com.storyplatform.system.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("ad_events")
public class AdEvent {
    @Id
    private UUID id;
    private UUID advertisementId;
    private UUID userId;
    private String sessionId;
    private UUID storyId;
    private String pageUrl;
    private String ipHash;
    private AdEventType eventType;
    private Instant createdAt;

    public AdEvent() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAdvertisementId() { return advertisementId; }
    public void setAdvertisementId(UUID advertisementId) { this.advertisementId = advertisementId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String pageUrl) { this.pageUrl = pageUrl; }

    public String getIpHash() { return ipHash; }
    public void setIpHash(String ipHash) { this.ipHash = ipHash; }

    public AdEventType getEventType() { return eventType; }
    public void setEventType(AdEventType eventType) { this.eventType = eventType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
