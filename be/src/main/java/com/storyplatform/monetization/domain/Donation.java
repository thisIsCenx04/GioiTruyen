package com.storyplatform.monetization.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("donations")
public class Donation {
    @Id
    private UUID id;
    private UUID userId;
    private UUID teamId;
    private UUID storyId;
    private Long grossCoin;
    private java.math.BigDecimal commissionRate;
    private Long commissionCoin;
    private Long teamNetCoin;
    private String message;
    private Instant createdAt;

    public Donation() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public Long getGrossCoin() { return grossCoin; }
    public void setGrossCoin(Long grossCoin) { this.grossCoin = grossCoin; }

    public java.math.BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(java.math.BigDecimal commissionRate) { this.commissionRate = commissionRate; }

    public Long getCommissionCoin() { return commissionCoin; }
    public void setCommissionCoin(Long commissionCoin) { this.commissionCoin = commissionCoin; }

    public Long getTeamNetCoin() { return teamNetCoin; }
    public void setTeamNetCoin(Long teamNetCoin) { this.teamNetCoin = teamNetCoin; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
