package com.storyplatform.monetization.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("payment_methods")
public class PaymentMethod {
    @Id
    private UUID id;
    private String name;
    private PaymentMethodType type;
    private String config;
    private String instructions;
    private Boolean isActive;
    private Integer sortOrder;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public PaymentMethod() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public PaymentMethodType getType() { return type; }
    public void setType(PaymentMethodType type) { this.type = type; }

    public String getConfig() { return config; }
    public void setConfig(String config) { this.config = config; }

    public String getInstruction() { return instructions; }
    public void setInstruction(String instructions) { this.instructions = instructions; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
