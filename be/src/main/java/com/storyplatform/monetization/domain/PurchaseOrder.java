package com.storyplatform.monetization.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("purchase_orders")
public class PurchaseOrder {
    @Id
    private UUID id;
    private UUID userId;
    private UUID storyId;
    private PurchaseType purchaseType;
    private Long totalCoin;
    private Instant createdAt;

    public PurchaseOrder() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public PurchaseType getPurchaseType() { return purchaseType; }
    public void setPurchaseType(PurchaseType purchaseType) { this.purchaseType = purchaseType; }

    public Long getTotalCoin() { return totalCoin; }
    public void setTotalCoin(Long totalCoin) { this.totalCoin = totalCoin; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
