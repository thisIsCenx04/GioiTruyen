package com.storyplatform.gamification.infrastructure;

import com.storyplatform.gamification.domain.UserMissionProgress;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface UserMissionProgressRepository extends CrudRepository<UserMissionProgress, UUID> {
}
