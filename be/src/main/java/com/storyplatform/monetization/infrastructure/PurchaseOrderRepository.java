package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.domain.PurchaseOrder;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends CrudRepository<PurchaseOrder, UUID> {
}
