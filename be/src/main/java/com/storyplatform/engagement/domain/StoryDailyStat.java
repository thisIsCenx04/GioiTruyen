package com.storyplatform.engagement.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.LocalDate;

@Table("story_daily_stats")
public class StoryDailyStat {
    private UUID storyId;
    private LocalDate statDate;
    private Long views;
    private Long uniqueViews;
    private Long audioListens;
    private Long favorites;
    private Long follows;
    private Long recommendations;
    private Long coinRevenue;

    public StoryDailyStat() {}

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }

    public Long getView() { return views; }
    public void setView(Long views) { this.views = views; }

    public Long getUniqueView() { return uniqueViews; }
    public void setUniqueView(Long uniqueViews) { this.uniqueViews = uniqueViews; }

    public Long getAudioListen() { return audioListens; }
    public void setAudioListen(Long audioListens) { this.audioListens = audioListens; }

    public Long getFavorite() { return favorites; }
    public void setFavorite(Long favorites) { this.favorites = favorites; }

    public Long getFollow() { return follows; }
    public void setFollow(Long follows) { this.follows = follows; }

    public Long getRecommendation() { return recommendations; }
    public void setRecommendation(Long recommendations) { this.recommendations = recommendations; }

    public Long getCoinRevenue() { return coinRevenue; }
    public void setCoinRevenue(Long coinRevenue) { this.coinRevenue = coinRevenue; }

}
