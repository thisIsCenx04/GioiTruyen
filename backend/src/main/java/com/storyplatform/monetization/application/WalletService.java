package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port.WalletRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.WalletAccount;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class WalletService implements WalletOperations {

    private final WalletRepository repository;
    private final Clock clock;

    public WalletService(WalletRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public WalletBalance balance(String userId) {
        return repository.find(WalletAccount.OwnerType.USER, userId)
                .map(WalletService::view)
                .orElseGet(() -> new WalletBalance(
                        "XU", 0, 0, 0, clock.instant()
                ));
    }

    @Override
    public WalletBalance open(
            WalletAccount.OwnerType ownerType,
            String ownerId
    ) {
        return repository.find(ownerType, ownerId)
                .map(WalletService::view)
                .orElseGet(() -> view(repository.insert(
                        new WalletAccount(
                                deterministicId(ownerType, ownerId),
                                ownerType,
                                ownerId,
                                ownerType == WalletAccount.OwnerType.PLATFORM
                                        ? LedgerEntry.Side.DEBIT
                                        : LedgerEntry.Side.CREDIT,
                                WalletAccount.Status.ACTIVE,
                                "XU",
                                clock.instant()
                        )
                )));
    }

    private static WalletBalance view(
            WalletRepository.AccountBalance value
    ) {
        return new WalletBalance(
                value.account().currency(),
                value.availableXu(),
                value.reservedXu(),
                value.version(),
                value.updatedAt()
        );
    }

    private static String deterministicId(
            WalletAccount.OwnerType ownerType,
            String ownerId
    ) {
        return UUID.nameUUIDFromBytes(
                ("gioitruyen:XU:" + ownerType + ":" + ownerId)
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();
    }
}
