package com.storyplatform.gamification.infrastructure;

import com.storyplatform.gamification.domain.RankingSnapshot;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface RankingSnapshotRepository extends CrudRepository<RankingSnapshot, UUID> {
}
