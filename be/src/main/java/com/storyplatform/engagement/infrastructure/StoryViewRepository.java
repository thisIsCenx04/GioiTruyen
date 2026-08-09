package com.storyplatform.engagement.infrastructure;

import com.storyplatform.engagement.domain.StoryView;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface StoryViewRepository extends CrudRepository<StoryView, UUID> {
}
