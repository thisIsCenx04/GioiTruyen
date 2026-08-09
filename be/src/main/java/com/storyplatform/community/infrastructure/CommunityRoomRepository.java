package com.storyplatform.community.infrastructure;

import com.storyplatform.community.domain.CommunityRoom;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CommunityRoomRepository extends CrudRepository<CommunityRoom, UUID> {
}
