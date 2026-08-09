package com.storyplatform.catalog.infrastructure;

import com.storyplatform.catalog.domain.Story;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface StoryRepository extends CrudRepository<Story, UUID> {
}
