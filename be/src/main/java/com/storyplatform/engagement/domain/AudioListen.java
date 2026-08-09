package com.storyplatform.engagement.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;

@Table("audio_listens")
public class AudioListen {
    @Id
    private UUID id;
    private UUID chapterAudioId;
    private UUID userId;
    private String sessionId;
    private Integer listenedSeconds;
    private Instant createdAt;

    public AudioListen() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getChapterAudioId() { return chapterAudioId; }
    public void setChapterAudioId(UUID chapterAudioId) { this.chapterAudioId = chapterAudioId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Integer getListenedSecond() { return listenedSeconds; }
    public void setListenedSecond(Integer listenedSeconds) { this.listenedSeconds = listenedSeconds; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
