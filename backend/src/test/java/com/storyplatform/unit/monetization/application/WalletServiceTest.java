package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.WalletService;
import com.storyplatform.monetization.application.port.WalletRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.WalletAccount;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");
    private final WalletRepository repository = mock(WalletRepository.class);
    private final WalletService service = new WalletService(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void returnsAZeroViewWithoutMutatingOnFirstRead() {
        when(repository.find(WalletAccount.OwnerType.USER, "user"))
                .thenReturn(Optional.empty());

        var balance = service.balance("user");

        assertThat(balance.availableXu()).isZero();
        assertThat(balance.reservedXu()).isZero();
        assertThat(balance.asOf()).isEqualTo(NOW);
    }

    @Test
    void opensDeterministicLiabilityWalletAndReplaysExistingOne() {
        when(repository.find(WalletAccount.OwnerType.USER, "user"))
                .thenReturn(Optional.empty());
        when(repository.insert(any())).thenAnswer(invocation -> {
            WalletAccount account = invocation.getArgument(0);
            return new WalletRepository.AccountBalance(
                    account, 0, 0, 0, NOW
            );
        });

        var opened = service.open(WalletAccount.OwnerType.USER, "user");

        assertThat(opened.currency()).isEqualTo("XU");
        var account = org.mockito.ArgumentCaptor.forClass(
                WalletAccount.class
        );
        verify(repository).insert(account.capture());
        assertThat(account.getValue().normalSide())
                .isEqualTo(LedgerEntry.Side.CREDIT);
    }

    @Test
    void mapsPersistedAvailableAndReservedBalances() {
        WalletAccount account = new WalletAccount(
                "10000000-0000-4000-8000-000000000001",
                WalletAccount.OwnerType.USER,
                "user",
                LedgerEntry.Side.CREDIT,
                WalletAccount.Status.ACTIVE,
                "XU",
                NOW
        );
        when(repository.find(WalletAccount.OwnerType.USER, "user"))
                .thenReturn(Optional.of(
                        new WalletRepository.AccountBalance(
                                account, 90_000, 10_000, 4, NOW
                        )
                ));

        assertThat(service.balance("user"))
                .extracting("availableXu", "reservedXu", "version")
                .containsExactly(90_000L, 10_000L, 4L);
    }
}
