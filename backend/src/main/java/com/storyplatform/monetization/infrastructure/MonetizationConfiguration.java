package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.LedgerService;
import com.storyplatform.monetization.application.port.LedgerRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoLedgerRepository;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class MonetizationConfiguration {

    @Bean
    LedgerRepository ledgerRepository(MongoTemplate mongo) {
        return new MongoLedgerRepository(mongo);
    }

    @Bean
    LedgerOperations ledgerOperations(
            LedgerRepository repository,
            OutboxAppender outbox
    ) {
        return new TransactionalLedgerOperations(new LedgerService(
                repository,
                outbox,
                Clock.systemUTC(),
                UUID::randomUUID
        ));
    }
}
