package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application
        .MonetizationKillSwitchGuard;
import com.storyplatform.monetization.application.WalletOperations;
import com.storyplatform.monetization.application
        .WithdrawalPayoutClaimOperations;
import com.storyplatform.monetization.application
        .WithdrawalPayoutClaimService;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCompletionOperations;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCompletionService;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCallbackOperations;
import com.storyplatform.monetization.application
        .WithdrawalPayoutCallbackService;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackDecoder;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackRepository;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutCallbackVerifier;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import com.storyplatform.monetization.application.port
        .WithdrawalPayoutRepository;
import com.storyplatform.monetization.application.port.WithdrawalRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalPayoutCallbackRepository;
import com.storyplatform.monetization.infrastructure.persistence
        .MongoWithdrawalPayoutRepository;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition
        .ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.monetization.withdrawals.payout.enabled",
        havingValue = "true"
)
public class WithdrawalPayoutConfiguration {

    @Bean
    WithdrawalPayoutRepository withdrawalPayoutRepository(
            MongoTemplate mongo
    ) {
        return new MongoWithdrawalPayoutRepository(mongo);
    }

    @Bean
    WithdrawalPayoutCallbackRepository withdrawalPayoutCallbackRepository(
            MongoTemplate mongo
    ) {
        return new MongoWithdrawalPayoutCallbackRepository(mongo);
    }

    @Bean
    WithdrawalPayoutClaimOperations withdrawalPayoutClaims(
            WithdrawalRepository withdrawals,
            WithdrawalPayoutRepository payouts,
            MonetizationKillSwitchGuard killSwitch,
            @Value("${app.monetization.withdrawals.payout.provider}")
            String provider,
            @Value("${app.monetization.withdrawals.payout.lease-duration}")
            Duration leaseDuration
    ) {
        return new GuardedWithdrawalPayoutClaimOperations(
                new TransactionalWithdrawalPayoutClaimOperations(
                new WithdrawalPayoutClaimService(
                        withdrawals,
                        payouts,
                        provider,
                        leaseDuration,
                        Clock.systemUTC()
                )),
                killSwitch
        );
    }

    @Bean
    WithdrawalPayoutCompletionOperations withdrawalPayoutCompletions(
            WithdrawalRepository withdrawals,
            WithdrawalPayoutRepository payouts,
            WalletOperations wallets,
            LedgerOperations ledger,
            OutboxAppender outbox,
            @Value("${app.monetization.withdrawals.payout.retry-delay}")
            Duration retryDelay
    ) {
        return new TransactionalWithdrawalPayoutCompletionOperations(
                new WithdrawalPayoutCompletionService(
                        withdrawals,
                        payouts,
                        wallets,
                        ledger,
                        outbox,
                        retryDelay,
                        Clock.systemUTC(),
                        UUID::randomUUID
                )
        );
    }

    @Bean
    WithdrawalPayoutGateway withdrawalPayoutGateway(
            ObjectMapper json,
            @Value("${app.monetization.withdrawals.payout.endpoint}")
            URI endpoint,
            @Value("${app.monetization.withdrawals.payout.token}")
            String token,
            @Value("${app.monetization.withdrawals.payout.timeout}")
            Duration timeout
    ) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new HttpWithdrawalPayoutGateway(
                http,
                json,
                endpoint,
                token,
                timeout
        );
    }

    @Bean
    WithdrawalPayoutCallbackVerifier withdrawalPayoutCallbackVerifier(
            @Value("${app.monetization.withdrawals.payout.provider}")
            String provider,
            @Value("${app.monetization.withdrawals.payout.webhook-secret}")
            String secret,
            @Value("${app.monetization.withdrawals.payout.webhook-max-age}")
            Duration maximumAge
    ) {
        return new HmacWithdrawalPayoutCallbackVerifier(
                provider,
                secret,
                maximumAge,
                Clock.systemUTC()
        );
    }

    @Bean
    WithdrawalPayoutCallbackDecoder withdrawalPayoutCallbackDecoder(
            ObjectMapper json
    ) {
        return new WithdrawalPayoutCallbackJsonDecoder(json);
    }

    @Bean
    WithdrawalPayoutCallbackOperations withdrawalPayoutCallbacks(
            WithdrawalPayoutCallbackVerifier verifier,
            WithdrawalPayoutCallbackDecoder decoder,
            WithdrawalPayoutCallbackRepository callbacks,
            WithdrawalRepository withdrawals,
            WithdrawalPayoutRepository payouts,
            WithdrawalPayoutCompletionOperations completions
    ) {
        return new TransactionalWithdrawalPayoutCallbackOperations(
                new WithdrawalPayoutCallbackService(
                        verifier,
                        decoder,
                        callbacks,
                        withdrawals,
                        payouts,
                        completions,
                        Clock.systemUTC(),
                        UUID::randomUUID
                )
        );
    }

    @Bean
    WithdrawalPayoutWorker withdrawalPayoutWorker(
            WithdrawalPayoutClaimOperations claims,
            WithdrawalPayoutCompletionOperations completions,
            WithdrawalPayoutGateway gateway,
            @Value("${app.monetization.withdrawals.payout.batch-size}")
            int batchSize
    ) {
        return new WithdrawalPayoutWorker(
                claims,
                completions,
                gateway,
                batchSize
        );
    }
}
