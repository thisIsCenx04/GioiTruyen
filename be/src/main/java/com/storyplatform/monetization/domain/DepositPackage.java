package com.storyplatform.monetization.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("deposit_packages")
public class DepositPackage {
    @Id
    private UUID id;
    private String name;
    private Long priceVnd;
    private Long coinAmount;
    private Long gemAmount;
    private Long bonusCoin;
    private Long bonusGem;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;

    public DepositPackage() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getPriceVnd() { return priceVnd; }
    public void setPriceVnd(Long priceVnd) { this.priceVnd = priceVnd; }

    public Long getCoinAmount() { return coinAmount; }
    public void setCoinAmount(Long coinAmount) { this.coinAmount = coinAmount; }

    public Long getGemAmount() { return gemAmount; }
    public void setGemAmount(Long gemAmount) { this.gemAmount = gemAmount; }

    public Long getBonusCoin() { return bonusCoin; }
    public void setBonusCoin(Long bonusCoin) { this.bonusCoin = bonusCoin; }

    public Long getBonusGem() { return bonusGem; }
    public void setBonusGem(Long bonusGem) { this.bonusGem = bonusGem; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
