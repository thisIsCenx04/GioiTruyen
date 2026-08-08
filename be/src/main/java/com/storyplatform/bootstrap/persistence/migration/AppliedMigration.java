package com.storyplatform.bootstrap.persistence.migration;

import java.time.Instant;

public record AppliedMigration(
        long version,
        String name,
        String checksum,
        Instant appliedAt
) {
}
