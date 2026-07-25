package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.domain.MonetizationKillSwitch;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = MongoMonetizationKillSwitchDocument.COLLECTION)
public record MongoMonetizationKillSwitchDocument(
        @Id String operation,
        boolean engaged,
        long version,
        String changedBy,
        Instant changedAt
) {
    public static final String COLLECTION = "monetization_kill_switches";

    static MongoMonetizationKillSwitchDocument from(
            MonetizationKillSwitch value
    ) {
        return new MongoMonetizationKillSwitchDocument(
                value.operation().name(),
                value.engaged(),
                value.version(),
                value.changedBy(),
                value.changedAt()
        );
    }

    MonetizationKillSwitch toDomain() {
        return new MonetizationKillSwitch(
                MonetizationKillSwitch.Operation.valueOf(operation),
                engaged,
                version,
                changedBy,
                changedAt
        );
    }
}
