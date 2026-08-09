package com.storyplatform.auth.infrastructure;

import com.storyplatform.auth.domain.UserProfile;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends CrudRepository<UserProfile, UUID> {
}
