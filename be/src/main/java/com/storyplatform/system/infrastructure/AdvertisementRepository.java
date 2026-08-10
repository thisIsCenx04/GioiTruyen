package com.storyplatform.system.infrastructure;

import com.storyplatform.system.domain.Advertisement;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.time.Instant;
import java.util.Optional;

@Repository
public interface AdvertisementRepository extends CrudRepository<Advertisement, UUID> {
    @Query("""
            SELECT *
            FROM advertisements
            WHERE is_active = TRUE
              AND placement = :placement
              AND (start_at IS NULL OR start_at <= :now)
              AND (end_at IS NULL OR end_at >= :now)
            ORDER BY priority DESC, updated_at DESC
            LIMIT 1
            """)
    Optional<Advertisement> findActive(String placement, Instant now);
}
