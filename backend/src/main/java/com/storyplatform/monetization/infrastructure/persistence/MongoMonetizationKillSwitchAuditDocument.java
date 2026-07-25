package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.MonetizationKillSwitch;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection =
        MongoMonetizationKillSwitchAuditDocument.COLLECTION)
public record MongoMonetizationKillSwitchAuditDocument(
        @Id String id,
        String operation,
        boolean previousEngaged,
        boolean engaged,
        long version,
        String changedBy,
        String reason,
        Instant changedAt
) {
    public static final String COLLECTION =
            "monetization_kill_switch_audits";

    static MongoMonetizationKillSwitchAuditDocument from(
            MonetizationKillSwitch previous,
            MonetizationKillSwitch replacement,
            String reason
    ) {
        return new MongoMonetizationKillSwitchAuditDocument(
                UUID.randomUUID().toString(),
                replacement.operation().name(),
                previous.engaged(),
                replacement.engaged(),
                replacement.version(),
                replacement.changedBy(),
                reason,
                replacement.changedAt()
        );
    }
}
