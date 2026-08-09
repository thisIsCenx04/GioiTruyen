package com.storyplatform.catalog.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;
import java.time.Instant;
import com.storyplatform.community.domain.GenericContentStatus;

@Table("chapter_audios")
public class ChapterAudio {
    @Id
    private UUID id;
    private UUID chapterId;
    private String audioUrl;
    private Integer durationSeconds;
    private Long fileSize;
    private String narrator;
    private GenericContentStatus status;
    private Instant createdAt;

    public ChapterAudio() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getChapterId() { return chapterId; }
    public void setChapterId(UUID chapterId) { this.chapterId = chapterId; }

    public String getAudioUrl() { return audioUrl; }
    public void setAudioUrl(String audioUrl) { this.audioUrl = audioUrl; }

    public Integer getDurationSecond() { return durationSeconds; }
    public void setDurationSecond(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getNarrator() { return narrator; }
    public void setNarrator(String narrator) { this.narrator = narrator; }

    public GenericContentStatus getStatus() { return status; }
    public void setStatus(GenericContentStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

}
