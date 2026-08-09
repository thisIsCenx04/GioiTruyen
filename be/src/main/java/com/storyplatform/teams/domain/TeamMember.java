package com.storyplatform.teams.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("team_members")
public class TeamMember {
    @Id
    private UUID id;
    private UUID teamId;
    private UUID userId;
    private TeamMemberRole memberRole;
    private TeamMemberStatus status;
    private UUID addedBy;
    private Instant joinedAt;
    private Instant removedAt;

    public TeamMember() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public TeamMemberRole getMemberRole() { return memberRole; }
    public void setMemberRole(TeamMemberRole memberRole) { this.memberRole = memberRole; }

    public TeamMemberStatus getStatus() { return status; }
    public void setStatus(TeamMemberStatus status) { this.status = status; }

    public UUID getAddedBy() { return addedBy; }
    public void setAddedBy(UUID addedBy) { this.addedBy = addedBy; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }

    public Instant getRemovedAt() { return removedAt; }
    public void setRemovedAt(Instant removedAt) { this.removedAt = removedAt; }

}
