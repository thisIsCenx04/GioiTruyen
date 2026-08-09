package com.storyplatform.system.infrastructure;

import com.storyplatform.system.domain.SiteDocument;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SiteDocumentRepository extends CrudRepository<SiteDocument, UUID> {
}
