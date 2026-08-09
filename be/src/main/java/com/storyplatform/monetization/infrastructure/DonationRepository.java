package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.domain.Donation;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface DonationRepository extends CrudRepository<Donation, UUID> {
}
