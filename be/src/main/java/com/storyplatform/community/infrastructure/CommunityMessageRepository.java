package com.storyplatform.community.infrastructure;

import com.storyplatform.community.domain.CommunityMessage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CommunityMessageRepository extends CrudRepository<CommunityMessage, UUID> {
}
