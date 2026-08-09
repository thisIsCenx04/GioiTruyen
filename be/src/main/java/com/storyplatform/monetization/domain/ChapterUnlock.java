package com.storyplatform.monetization.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("chapter_unlocks")
public class ChapterUnlock {
    @Id
    private UUID id;
    private UUID userId;
    private UUID chapterId;
    private UUID purchaseOrderId;
    private Long coinPaid;
    private Instant createdAt;

    public ChapterUnlock() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getChapterId() { return chapterId; }
    public void setChapterId(UUID chapterId) { this.chapterId = chapterId; }

    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(UUID purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }

    public Long getCoinPaid() { return coinPaid; }
    public void setCoinPaid(Long coinPaid) { this.coinPaid = coinPaid; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
