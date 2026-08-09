package com.storyplatform.catalog.infrastructure;

import com.storyplatform.catalog.domain.Chapter;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ChapterRepository extends CrudRepository<Chapter, UUID> {
}
