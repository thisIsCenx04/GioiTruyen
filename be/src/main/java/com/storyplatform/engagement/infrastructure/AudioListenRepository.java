package com.storyplatform.engagement.infrastructure;

import com.storyplatform.engagement.domain.AudioListen;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AudioListenRepository extends CrudRepository<AudioListen, UUID> {
}
