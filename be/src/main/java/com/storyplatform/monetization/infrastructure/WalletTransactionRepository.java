package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.domain.WalletTransaction;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends CrudRepository<WalletTransaction, UUID> {
}
