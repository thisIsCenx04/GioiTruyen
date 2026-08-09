package com.storyplatform.system.infrastructure;

import com.storyplatform.system.domain.NotificationPreference;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends CrudRepository<NotificationPreference, UUID> {
}
