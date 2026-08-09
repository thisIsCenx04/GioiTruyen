package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.domain.TeamLedger;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface TeamLedgerRepository extends CrudRepository<TeamLedger, UUID> {
}
