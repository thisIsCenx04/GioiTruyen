package com.storyplatform.system.infrastructure;

import com.storyplatform.system.domain.AdEvent;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AdEventRepository extends CrudRepository<AdEvent, UUID> {
}
