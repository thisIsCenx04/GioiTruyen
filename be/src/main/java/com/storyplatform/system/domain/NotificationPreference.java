package com.storyplatform.system.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("notification_preferences")
public class NotificationPreference {
    @Id
    private UUID userId;
    private Boolean storyUpdates;
    private Boolean teamUpdates;
    private Boolean systemUpdates;
    private Boolean paymentUpdates;
    private Instant updatedAt;

    public NotificationPreference() {}

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Boolean getStoryUpdate() { return storyUpdates; }
    public void setStoryUpdate(Boolean storyUpdates) { this.storyUpdates = storyUpdates; }

    public Boolean getTeamUpdate() { return teamUpdates; }
    public void setTeamUpdate(Boolean teamUpdates) { this.teamUpdates = teamUpdates; }

    public Boolean getSystemUpdate() { return systemUpdates; }
    public void setSystemUpdate(Boolean systemUpdates) { this.systemUpdates = systemUpdates; }

    public Boolean getPaymentUpdate() { return paymentUpdates; }
    public void setPaymentUpdate(Boolean paymentUpdates) { this.paymentUpdates = paymentUpdates; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
