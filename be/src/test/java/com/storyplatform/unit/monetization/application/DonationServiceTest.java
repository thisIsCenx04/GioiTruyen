package com.storyplatform.unit.monetization.application;

import com.storyplatform.monetization.application.DonationException;
import com.storyplatform.monetization.application.DonationService;
import com.storyplatform.monetization.application.InsufficientWalletBalanceException;
import com.storyplatform.monetization.application.LedgerConflictException;
import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.WalletOperations;
import com.storyplatform.monetization.application.port.DonationRepository;
import com.storyplatform.monetization.domain.Donation;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.WalletAccount;
import com.storyplatform.shared.events.persistence.OutboxAppender;
import com.storyplatform.teams.application.contract.TeamStatusDirectory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DonationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T00:00:00Z");
    private static final String KEY = "donation-key-0001";
    private final DonationRepository repository =
            mock(DonationRepository.class);
    private final TeamStatusDirectory teams =
            mock(TeamStatusDirectory.class);
    private final WalletOperations wallets = mock(WalletOperations.class);
    private final LedgerOperations ledger = mock(LedgerOperations.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final DonationService service = new DonationService(
            repository,
            teams,
            wallets,
            ledger,
            outbox,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> UUID.fromString(
                    "10000000-0000-4000-8000-000000000001"
            )
    );

    @Test
    void debitsReaderAndCreditsTeamUsingOnlyXu() {
        when(teams.isActive("team-1")).thenReturn(true);
        when(ledger.post(any())).thenReturn(posting());
        when(repository.insert(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );
        ArgumentCaptor<LedgerOperations.Command> command =
                ArgumentCaptor.forClass(LedgerOperations.Command.class);

        var receipt = service.donate(
                "reader-1",
                KEY,
                "team-1",
                500,
                "Thank you"
        );

        assertThat(receipt.amountXu()).isEqualTo(500);
        assertThat(receipt.replayed()).isFalse();
        verify(ledger).post(command.capture());
        assertThat(command.getValue().type())
                .isEqualTo(LedgerTransaction.Type.DONATION);
        assertThat(command.getValue().entries())
                .extracting(LedgerEntry::side)
                .containsExactly(
                        LedgerEntry.Side.DEBIT,
                        LedgerEntry.Side.CREDIT
                );
        verify(outbox).append(any());
    }

    @Test
    void replaysSamePayloadAndRejectsKeyReuse() {
        Donation existing = donation();
        when(repository.findByIdempotencyKeyHash(any()))
                .thenReturn(Optional.of(existing));

        assertThat(service.donate(
                "reader-1", KEY, "team-1", 500, "Thank you"
        ).replayed()).isTrue();
        assertThatThrownBy(() -> service.donate(
                "reader-1", KEY, "team-1", 501, "Thank you"
        )).isInstanceOf(DonationException.class)
                .extracting("kind")
                .isEqualTo(DonationException.Kind.CONFLICT);
        verify(ledger, never()).post(any());
    }

    @Test
    void rejectsInactiveTeamAndAtomicOverdraft() {
        assertThatThrownBy(() -> service.donate(
                "reader-1", KEY, "team-1", 500, null
        )).isInstanceOf(DonationException.class)
                .extracting("kind")
                .isEqualTo(DonationException.Kind.TEAM_NOT_FOUND);

        when(teams.isActive("team-1")).thenReturn(true);
        when(ledger.post(any())).thenThrow(
                new InsufficientWalletBalanceException()
        );
        assertThatThrownBy(() -> service.donate(
                "reader-1", KEY, "team-1", 500, null
        )).isInstanceOf(DonationException.class)
                .extracting("kind")
                .isEqualTo(DonationException.Kind.INSUFFICIENT_BALANCE);
        verify(repository, never()).insert(any());
        verify(outbox, never()).append(any());
    }

    @Test
    void rejectsInvalidInputBeforePosting() {
        assertThatThrownBy(() -> service.donate(
                " ", KEY, "team-1", 500, null
        )).isInstanceOf(DonationException.class)
                .extracting("kind")
                .isEqualTo(DonationException.Kind.INVALID);
        assertThatThrownBy(() -> service.donate(
                "reader-1", "short", "team-1", 500, null
        )).isInstanceOf(DonationException.class)
                .extracting("kind")
                .isEqualTo(DonationException.Kind.INVALID);
        assertThatThrownBy(() -> service.donate(
                "reader-1", KEY, "team-1", 0, null
        )).isInstanceOf(DonationException.class)
                .extracting("kind")
                .isEqualTo(DonationException.Kind.INVALID);
        assertThatThrownBy(() -> service.donate(
                "reader-1", KEY, "team-1", 500, "bad\u0000message"
        )).isInstanceOf(DonationException.class)
                .extracting("kind")
                .isEqualTo(DonationException.Kind.INVALID);
        verify(ledger, never()).post(any());
    }

    @Test
    void mapsConcurrentLedgerConflictWithoutPersistingDonation() {
        when(teams.isActive("team-1")).thenReturn(true);
        when(ledger.post(any())).thenThrow(
                new LedgerConflictException("concurrent donation")
        );

        assertThatThrownBy(() -> service.donate(
                "reader-1", KEY, "team-1", 500, " "
        )).isInstanceOf(DonationException.class)
                .extracting("kind")
                .isEqualTo(DonationException.Kind.CONFLICT);
        verify(repository, never()).insert(any());
        verify(outbox, never()).append(any());
    }

    private static Donation donation() {
        return new Donation(
                "10000000-0000-4000-8000-000000000001",
                "reader-1",
                "team-1",
                WalletOperations.accountId(
                        WalletAccount.OwnerType.USER,
                        "reader-1"
                ),
                WalletOperations.accountId(
                        WalletAccount.OwnerType.TEAM,
                        "team-1"
                ),
                500,
                "Thank you",
                "20000000-0000-4000-8000-000000000001",
                hash("reader-1\n/donations\n" + KEY),
                hash("team-1\n500\nThank you"),
                Donation.Status.POSTED,
                NOW
        );
    }

    private static LedgerOperations.Posting posting() {
        return new LedgerOperations.Posting(
                LedgerTransaction.post(
                        "20000000-0000-4000-8000-000000000001",
                        LedgerTransaction.Type.DONATION,
                        "donation",
                        "10000000-0000-4000-8000-000000000001",
                        List.of(
                                new LedgerEntry(
                                        WalletOperations.accountId(
                                                WalletAccount.OwnerType.USER,
                                                "reader-1"
                                        ),
                                        LedgerEntry.Side.DEBIT,
                                        500
                                ),
                                new LedgerEntry(
                                        WalletOperations.accountId(
                                                WalletAccount.OwnerType.TEAM,
                                                "team-1"
                                        ),
                                        LedgerEntry.Side.CREDIT,
                                        500
                                )
                        ),
                        "a".repeat(64),
                        NOW
                ),
                false
        );
    }

    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                            ))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
