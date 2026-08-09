package com.storyplatform.gamification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("missions")
public class Mission {
    @Id
    private UUID id;
    private String code;
    private String name;
    private String description;
    private MissionType missionType;
    private Integer targetCount;
    private Long rewardCoin;
    private Long rewardGem;
    private Integer dailyLimit;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public Mission() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public MissionType getMissionType() { return missionType; }
    public void setMissionType(MissionType missionType) { this.missionType = missionType; }

    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }

    public Long getRewardCoin() { return rewardCoin; }
    public void setRewardCoin(Long rewardCoin) { this.rewardCoin = rewardCoin; }

    public Long getRewardGem() { return rewardGem; }
    public void setRewardGem(Long rewardGem) { this.rewardGem = rewardGem; }

    public Integer getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(Integer dailyLimit) { this.dailyLimit = dailyLimit; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
