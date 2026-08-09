package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.domain.ChapterUnlock;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ChapterUnlockRepository extends CrudRepository<ChapterUnlock, UUID> {
}
