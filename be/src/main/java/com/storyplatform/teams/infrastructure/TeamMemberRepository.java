package com.storyplatform.teams.infrastructure;

import com.storyplatform.teams.domain.TeamMember;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TeamMemberRepository extends CrudRepository<TeamMember, UUID> {
}
