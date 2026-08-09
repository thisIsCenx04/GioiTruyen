package com.storyplatform.catalog.infrastructure;

import com.storyplatform.catalog.domain.StoryReview;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface StoryReviewRepository extends CrudRepository<StoryReview, UUID> {
}
