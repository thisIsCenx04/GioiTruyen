package com.storyplatform.monetization.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;
import com.storyplatform.teams.domain.TeamLedgerType;

@Table("team_ledger")
public class TeamLedger {
    @Id
    private UUID id;
    private UUID teamId;
    private TeamLedgerType type;
    private Long grossCoin;
    private Long platformFeeCoin;
    private Long netCoin;
    private String referenceType;
    private UUID referenceId;
    private Instant createdAt;

    public TeamLedger() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public TeamLedgerType getType() { return type; }
    public void setType(TeamLedgerType type) { this.type = type; }

    public Long getGrossCoin() { return grossCoin; }
    public void setGrossCoin(Long grossCoin) { this.grossCoin = grossCoin; }

    public Long getPlatformFeeCoin() { return platformFeeCoin; }
    public void setPlatformFeeCoin(Long platformFeeCoin) { this.platformFeeCoin = platformFeeCoin; }

    public Long getNetCoin() { return netCoin; }
    public void setNetCoin(Long netCoin) { this.netCoin = netCoin; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
