package com.storyplatform.engagement.infrastructure;

import com.storyplatform.engagement.domain.StoryDailyStat;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface StoryDailyStatRepository extends CrudRepository<StoryDailyStat, UUID> {
}
