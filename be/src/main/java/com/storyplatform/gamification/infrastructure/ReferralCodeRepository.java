package com.storyplatform.gamification.infrastructure;

import com.storyplatform.gamification.domain.ReferralCode;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ReferralCodeRepository extends CrudRepository<ReferralCode, UUID> {
}
