package com.storyplatform.system.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("advertisements")
public class Advertisement {
    @Id
    private UUID id;
    private String name;
    private AdvertisementType type;
    private String imageUrl;
    private String targetUrl;
    private AdvertisementPlacement placement;
    private Integer cooldownSeconds;
    private Integer maxClicksPerDay;
    private Integer priority;
    private Integer triggerEveryNViews;
    private Instant startAt;
    private Instant endAt;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public Advertisement() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AdvertisementType getType() { return type; }
    public void setType(AdvertisementType type) { this.type = type; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public AdvertisementPlacement getPlacement() { return placement; }
    public void setPlacement(AdvertisementPlacement placement) { this.placement = placement; }

    public Integer getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(Integer cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }

    public Integer getMaxClicksPerDay() { return maxClicksPerDay; }
    public void setMaxClicksPerDay(Integer maxClicksPerDay) { this.maxClicksPerDay = maxClicksPerDay; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }

    public Integer getTriggerEveryNView() { return triggerEveryNViews; }
    public void setTriggerEveryNView(Integer triggerEveryNViews) { this.triggerEveryNViews = triggerEveryNViews; }

    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }

    public Instant getEndAt() { return endAt; }
    public void setEndAt(Instant endAt) { this.endAt = endAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
