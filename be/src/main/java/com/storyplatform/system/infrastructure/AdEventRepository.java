package com.storyplatform.system.infrastructure;

import com.storyplatform.system.domain.AdEvent;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AdEventRepository extends CrudRepository<AdEvent, UUID> {

    @Query("SELECT COUNT(*) FROM ad_events WHERE advertisement_id = :advertisementId")
    long countByAdvertisementId(@Param("advertisementId") UUID advertisementId);
}
