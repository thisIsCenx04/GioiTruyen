package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.domain.PurchaseOrderItem;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PurchaseOrderItemRepository extends CrudRepository<PurchaseOrderItem, UUID> {
}
