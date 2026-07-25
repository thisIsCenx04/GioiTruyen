package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.port
        .MonetizationKillSwitchRepository;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Objects;
import java.util.Optional;

public final class MongoMonetizationKillSwitchRepository
        implements MonetizationKillSwitchRepository {

    private final MongoTemplate mongo;

    public MongoMonetizationKillSwitchRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public Optional<MonetizationKillSwitch> find(
            MonetizationKillSwitch.Operation operation
    ) {
        return Optional.ofNullable(mongo.findById(
                operation.name(),
                MongoMonetizationKillSwitchDocument.class
        )).map(MongoMonetizationKillSwitchDocument::toDomain);
    }

    @Override
    public boolean save(
            MonetizationKillSwitch expected,
            MonetizationKillSwitch replacement,
            String reason
    ) {
        boolean changed;
        if (expected.version() == 0) {
            changed = insertInitial(replacement);
        } else {
            changed = mongo.updateFirst(
                    Query.query(Criteria.where("_id")
                            .is(expected.operation().name())
                            .and("version").is(expected.version())),
                    new Update()
                            .set("engaged", replacement.engaged())
                            .set("version", replacement.version())
                            .set("changedBy", replacement.changedBy())
                            .set("changedAt", replacement.changedAt()),
                    MongoMonetizationKillSwitchDocument.class
            ).getModifiedCount() == 1;
        }
        if (changed) {
            mongo.insert(
                    MongoMonetizationKillSwitchAuditDocument.from(
                            expected,
                            replacement,
                            reason
                    )
            );
        }
        return changed;
    }

    private boolean insertInitial(MonetizationKillSwitch value) {
        try {
            mongo.insert(MongoMonetizationKillSwitchDocument.from(value));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }
}
