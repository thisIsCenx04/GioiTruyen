package com.storyplatform.gamification.infrastructure;

import com.storyplatform.gamification.domain.Referral;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ReferralRepository extends CrudRepository<Referral, UUID> {
}
