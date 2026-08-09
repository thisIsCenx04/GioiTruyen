package com.storyplatform.auth.infrastructure;

import com.storyplatform.auth.domain.UserSetting;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface UserSettingRepository extends CrudRepository<UserSetting, UUID> {
}
