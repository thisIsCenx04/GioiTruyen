package com.storyplatform.system.infrastructure;

import com.storyplatform.system.domain.SiteSetting;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteSettingRepository extends CrudRepository<SiteSetting, String> {
}
