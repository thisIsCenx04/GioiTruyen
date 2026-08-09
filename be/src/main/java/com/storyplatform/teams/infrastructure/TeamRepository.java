package com.storyplatform.teams.infrastructure;

import com.storyplatform.teams.domain.Team;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TeamRepository extends CrudRepository<Team, UUID> {
}
