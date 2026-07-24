package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.LedgerService;
import com.storyplatform.monetization.application.port.LedgerRepository;
import com.storyplatform.monetization.application.WalletBalanceProjector;
import com.storyplatform.monetization.application.WalletOperations;
import com.storyplatform.monetization.application.WalletService;
import com.storyplatform.monetization.application.TopupDiscountOperations;
import com.storyplatform.monetization.application.TopupDiscountService;
import com.storyplatform.monetization.application.port.LedgerBalanceProjector;
import com.storyplatform.monetization.application.port.WalletRepository;
import com.storyplatform.monetization.application.port
        .ConfigurationChangeAuthorizer;
import com.storyplatform.monetization.application.port
        .TopupDiscountRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoLedgerRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWalletRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupDiscountRepository;
import com.storyplatform.identity.application.contract
        .ReauthenticationVerifier;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class MonetizationConfiguration {

    @Bean
    TopupDiscountRepository topupDiscountRepository(MongoTemplate mongo) {
        return new MongoTopupDiscountRepository(mongo);
    }

    @Bean
    ConfigurationChangeAuthorizer configurationChangeAuthorizer(
            ReauthenticationVerifier reauthentication
    ) {
        return new ScopedConfigurationChangeAuthorizer(reauthentication);
    }

    @Bean
    TopupDiscountOperations topupDiscountOperations(
            TopupDiscountRepository repository,
            ConfigurationChangeAuthorizer authorizer,
            OutboxAppender outbox
    ) {
        return new TransactionalTopupDiscountOperations(
                new TopupDiscountService(
                        repository,
                        authorizer,
                        outbox,
                        Clock.systemUTC(),
                        UUID::randomUUID
                )
        );
    }

    @Bean
    LedgerRepository ledgerRepository(MongoTemplate mongo) {
        return new MongoLedgerRepository(mongo);
    }

    @Bean
    LedgerOperations ledgerOperations(
            LedgerRepository repository,
            OutboxAppender outbox,
            LedgerBalanceProjector balances
    ) {
        return new TransactionalLedgerOperations(new LedgerService(
                repository,
                outbox,
                balances,
                Clock.systemUTC(),
                UUID::randomUUID
        ));
    }

    @Bean
    WalletRepository walletRepository(MongoTemplate mongo) {
        return new MongoWalletRepository(mongo);
    }

    @Bean
    LedgerBalanceProjector ledgerBalanceProjector(
            WalletRepository repository
    ) {
        return new WalletBalanceProjector(
                repository,
                Clock.systemUTC()
        );
    }

    @Bean
    WalletOperations walletOperations(WalletRepository repository) {
        return new TransactionalWalletOperations(
                new WalletService(repository, Clock.systemUTC())
        );
    }
}
