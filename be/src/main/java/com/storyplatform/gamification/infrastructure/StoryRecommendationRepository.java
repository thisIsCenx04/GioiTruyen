package com.storyplatform.gamification.infrastructure;

import com.storyplatform.gamification.domain.StoryRecommendation;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface StoryRecommendationRepository extends CrudRepository<StoryRecommendation, UUID> {
}
