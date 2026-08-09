package com.storyplatform.system.infrastructure;

import com.storyplatform.system.domain.Advertisement;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AdvertisementRepository extends CrudRepository<Advertisement, UUID> {
}
