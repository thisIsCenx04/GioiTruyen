package com.storyplatform.gamification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.LocalDate;

@Table("ranking_snapshots")
public class RankingSnapshot {
    @Id
    private UUID id;
    private RankingType rankingType;
    private UUID storyId;
    private Long score;
    private Integer rank;
    private RankingPeriod period;
    private LocalDate snapshotDate;

    public RankingSnapshot() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public RankingType getRankingType() { return rankingType; }
    public void setRankingType(RankingType rankingType) { this.rankingType = rankingType; }

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public Long getScore() { return score; }
    public void setScore(Long score) { this.score = score; }

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }

    public RankingPeriod getPeriod() { return period; }
    public void setPeriod(RankingPeriod period) { this.period = period; }

    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }

}
