package com.storyplatform.monetization.infrastructure.persistence;

import com.storyplatform.monetization.application.port.PaymentEventRepository;
import com.storyplatform.monetization.domain.PaymentEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Objects;

public final class MongoPaymentEventRepository
        implements PaymentEventRepository {

    private final MongoTemplate mongo;

    public MongoPaymentEventRepository(MongoTemplate mongo) {
        this.mongo = Objects.requireNonNull(mongo);
    }

    @Override
    public boolean insertIfAbsent(PaymentEvent event) {
        try {
            mongo.insert(MongoPaymentEventDocument.from(event));
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }
}
