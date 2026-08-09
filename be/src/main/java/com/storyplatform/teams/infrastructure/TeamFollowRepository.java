package com.storyplatform.teams.infrastructure;

import com.storyplatform.teams.domain.TeamFollow;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TeamFollowRepository extends CrudRepository<TeamFollow, UUID> {
}
