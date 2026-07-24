package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.LedgerService;
import com.storyplatform.monetization.application.ManualTopupOperations;
import com.storyplatform.monetization.application.ManualTopupService;
import com.storyplatform.monetization.application.port.LedgerRepository;
import com.storyplatform.monetization.application.port.ManualTopupAuthorizer;
import com.storyplatform.monetization.application.port.ManualTopupRepository;
import com.storyplatform.monetization.application.WalletBalanceProjector;
import com.storyplatform.monetization.application.WalletOperations;
import com.storyplatform.monetization.application.WalletService;
import com.storyplatform.monetization.application.TopupDiscountOperations;
import com.storyplatform.monetization.application.TopupDiscountService;
import com.storyplatform.monetization.application.TopupRequestOperations;
import com.storyplatform.monetization.application.TopupRequestService;
import com.storyplatform.monetization.application.TopupRejectionOperations;
import com.storyplatform.monetization.application.TopupRejectionService;
import com.storyplatform.monetization.application.TopupSettlementOperations;
import com.storyplatform.monetization.application.TopupSettlementService;
import com.storyplatform.monetization.application.port.LedgerBalanceProjector;
import com.storyplatform.monetization.application.port.WalletRepository;
import com.storyplatform.monetization.application.port
        .ConfigurationChangeAuthorizer;
import com.storyplatform.monetization.application.port
        .TopupDiscountRepository;
import com.storyplatform.monetization.application.port
        .TopupRequestRepository;
import com.storyplatform.monetization.application.port
        .TopupRejectionRepository;
import com.storyplatform.monetization.application.port
        .TopupSettlementRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoLedgerRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoManualTopupRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWalletRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupDiscountRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupRequestRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupRejectionRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoTopupSettlementRepository;
import com.storyplatform.identity.application.contract
        .ReauthenticationVerifier;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class MonetizationConfiguration {

    @Bean
    TopupRejectionRepository topupRejectionRepository(
            MongoTemplate mongo
    ) {
        return new MongoTopupRejectionRepository(mongo);
    }

    @Bean
    TopupRejectionOperations topupRejectionOperations(
            TopupRejectionRepository repository,
            OutboxAppender outbox
    ) {
        return new TransactionalTopupRejectionOperations(
                new TopupRejectionService(
                        repository,
                        outbox,
                        Clock.systemUTC(),
                        UUID::randomUUID
                )
        );
    }

    @Bean
    ManualTopupRepository manualTopupRepository(MongoTemplate mongo) {
        return new MongoManualTopupRepository(mongo);
    }

    @Bean
    ManualTopupAuthorizer manualTopupAuthorizer(
            ReauthenticationVerifier reauthentication
    ) {
        return new ScopedManualTopupAuthorizer(reauthentication);
    }

    @Bean
    ManualTopupOperations manualTopupOperations(
            ManualTopupRepository repository,
            ManualTopupAuthorizer authorizer,
            WalletOperations wallets,
            LedgerOperations ledger,
            OutboxAppender outbox
    ) {
        return new TransactionalManualTopupOperations(
                new ManualTopupService(
                        repository,
                        authorizer,
                        wallets,
                        ledger,
                        outbox,
                        Clock.systemUTC(),
                        UUID::randomUUID
                )
        );
    }

    @Bean
    TopupSettlementRepository topupSettlementRepository(
            MongoTemplate mongo
    ) {
        return new MongoTopupSettlementRepository(mongo);
    }

    @Bean
    TopupSettlementOperations topupSettlementOperations(
            TopupSettlementRepository repository,
            WalletOperations wallets,
            LedgerOperations ledger
    ) {
        return new TransactionalTopupSettlementOperations(
                new TopupSettlementService(
                        repository,
                        wallets,
                        ledger,
                        Clock.systemUTC()
                )
        );
    }

    @Bean
    TopupRequestRepository topupRequestRepository(MongoTemplate mongo) {
        return new MongoTopupRequestRepository(mongo);
    }

    @Bean
    TopupRequestOperations topupRequestOperations(
            TopupRequestRepository repository,
            TopupDiscountOperations discounts,
            OutboxAppender outbox,
            @Value("${app.monetization.topup.request-ttl}") Duration ttl,
            @Value("${app.monetization.topup.bank-bin}") String bankBin,
            @Value("${app.monetization.topup.account-number}")
            String accountNumber,
            @Value("${app.monetization.topup.account-name}")
            String accountName
    ) {
        return new TransactionalTopupRequestOperations(
                new TopupRequestService(
                        repository,
                        discounts,
                        new VietQrPayloadFactory(
                                bankBin,
                                accountNumber,
                                accountName
                        ),
                        outbox,
                        Clock.systemUTC(),
                        ttl,
                        UUID::randomUUID,
                        new SecureTopupReferenceSupplier(
                                new SecureRandom()
                        )
                )
        );
    }

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
