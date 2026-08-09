package com.storyplatform.catalog.infrastructure;

import com.storyplatform.catalog.domain.StoryGenre;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface StoryGenreRepository extends CrudRepository<StoryGenre, UUID> {
}
