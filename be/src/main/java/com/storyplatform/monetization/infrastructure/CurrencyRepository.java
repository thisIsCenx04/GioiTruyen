package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.domain.Currency;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CurrencyRepository extends CrudRepository<Currency, UUID> {
}
