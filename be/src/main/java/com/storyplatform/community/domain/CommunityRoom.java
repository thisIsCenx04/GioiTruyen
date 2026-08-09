package com.storyplatform.community.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("community_rooms")
public class CommunityRoom {
    @Id
    private UUID id;
    private String name;
    private GenericContentStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public CommunityRoom() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public GenericContentStatus getStatus() { return status; }
    public void setStatus(GenericContentStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

}
