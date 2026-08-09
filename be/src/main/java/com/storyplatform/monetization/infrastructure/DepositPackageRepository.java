package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.domain.DepositPackage;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface DepositPackageRepository extends CrudRepository<DepositPackage, UUID> {
}
