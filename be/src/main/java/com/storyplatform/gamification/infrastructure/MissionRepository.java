package com.storyplatform.gamification.infrastructure;

import com.storyplatform.gamification.domain.Mission;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface MissionRepository extends CrudRepository<Mission, UUID> {
}
