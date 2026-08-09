package com.storyplatform.engagement.infrastructure;

import com.storyplatform.engagement.domain.StoryFollow;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface StoryFollowRepository extends CrudRepository<StoryFollow, UUID> {
}
