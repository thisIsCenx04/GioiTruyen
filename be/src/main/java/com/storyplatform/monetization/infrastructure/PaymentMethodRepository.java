package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.domain.PaymentMethod;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends CrudRepository<PaymentMethod, UUID> {
}
