package com.storyplatform.moderation.infrastructure;

import com.storyplatform.moderation.domain.Report;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ReportRepository extends CrudRepository<Report, UUID> {
}
