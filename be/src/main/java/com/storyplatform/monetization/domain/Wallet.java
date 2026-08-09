package com.storyplatform.monetization.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("wallets")
public class Wallet {
    @Id
    private UUID id;
    private UUID userId;
    private Long coinBalance;
    private Long gemBalance;
    private Instant updatedAt;

    public Wallet() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Long getCoinBalance() { return coinBalance; }
    public void setCoinBalance(Long coinBalance) { this.coinBalance = coinBalance; }

    public Long getGemBalance() { return gemBalance; }
    public void setGemBalance(Long gemBalance) { this.gemBalance = gemBalance; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
