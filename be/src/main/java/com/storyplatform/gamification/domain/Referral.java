package com.storyplatform.gamification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("referrals")
public class Referral {
    @Id
    private UUID id;
    private UUID referrerId;
    private UUID referredUserId;
    private String status;
    private Instant qualifiedAt;
    private Long rewardCoin;
    private Long rewardGem;
    private Instant rewardedAt;
    private Instant createdAt;

    public Referral() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getReferrerId() { return referrerId; }
    public void setReferrerId(UUID referrerId) { this.referrerId = referrerId; }

    public UUID getReferredUserId() { return referredUserId; }
    public void setReferredUserId(UUID referredUserId) { this.referredUserId = referredUserId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getQualifiedAt() { return qualifiedAt; }
    public void setQualifiedAt(Instant qualifiedAt) { this.qualifiedAt = qualifiedAt; }

    public Long getRewardCoin() { return rewardCoin; }
    public void setRewardCoin(Long rewardCoin) { this.rewardCoin = rewardCoin; }

    public Long getRewardGem() { return rewardGem; }
    public void setRewardGem(Long rewardGem) { this.rewardGem = rewardGem; }

    public Instant getRewardedAt() { return rewardedAt; }
    public void setRewardedAt(Instant rewardedAt) { this.rewardedAt = rewardedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
