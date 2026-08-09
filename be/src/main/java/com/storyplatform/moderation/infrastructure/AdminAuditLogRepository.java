package com.storyplatform.moderation.infrastructure;

import com.storyplatform.moderation.domain.AdminAuditLog;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AdminAuditLogRepository extends CrudRepository<AdminAuditLog, UUID> {
}
