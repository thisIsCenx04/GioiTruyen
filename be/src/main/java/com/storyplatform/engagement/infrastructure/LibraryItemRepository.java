package com.storyplatform.engagement.infrastructure;

import com.storyplatform.engagement.domain.LibraryItem;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface LibraryItemRepository extends CrudRepository<LibraryItem, UUID> {
}
