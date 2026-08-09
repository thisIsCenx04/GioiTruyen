package com.storyplatform.catalog.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table("story_genres")
public class StoryGenre {
    private UUID storyId;
    private UUID genreId;

    public StoryGenre() {}

    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID storyId) { this.storyId = storyId; }

    public UUID getGenreId() { return genreId; }
    public void setGenreId(UUID genreId) { this.genreId = genreId; }

}
