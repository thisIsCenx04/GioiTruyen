package com.storyplatform.catalog.infrastructure;

import com.storyplatform.catalog.domain.ChapterAudio;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ChapterAudioRepository extends CrudRepository<ChapterAudio, UUID> {
}
