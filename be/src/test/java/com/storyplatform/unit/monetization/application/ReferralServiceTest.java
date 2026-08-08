package com.storyplatform.unit.monetization.application;

import com.storyplatform.identity.application.contract.IdentityUserDirectory;
import com.storyplatform.monetization.application.LedgerOperations;
import com.storyplatform.monetization.application.ReferralException;
import com.storyplatform.monetization.application.ReferralRule;
import com.storyplatform.monetization.application.ReferralService;
import com.storyplatform.monetization.application.WalletOperations;
import com.storyplatform.monetization.application.port.ReferralCodeCodec;
import com.storyplatform.monetization.application.port.ReferralRepository;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.ReferralAttribution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferralServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T01:00:00Z");
    private static final String REFERRER =
            "10000000-0000-4000-8000-000000000001";
    private static final String REFEREE =
            "20000000-0000-4000-8000-000000000001";
    private static final String LEDGER_ID =
            "30000000-0000-4000-8000-000000000001";
    private static final String KEY = "referral-key-0001";
    private final ReferralRepository repository =
            mock(ReferralRepository.class);
    private final IdentityUserDirectory users =
            mock(IdentityUserDirectory.class);
    private final WalletOperations wallets = mock(WalletOperations.class);
    private final LedgerOperations ledger = mock(LedgerOperations.class);
    private final ReferralCodeCodec codes = new TestReferralCodeCodec();
    private final ReferralService service = new ReferralService(
            repository,
            codes,
            users,
            wallets,
            ledger,
            new ReferralRule(
                    "referral-2026.1",
                    100,
                    Duration.ofDays(7),
                    Duration.ofDays(7),
                    Duration.ofDays(7),
                    100
            ),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @BeforeEach
    void setUp() {
        when(repository.findByIdempotencyKeyHash(any()))
                .thenReturn(Optional.empty());
        when(repository.findByRefereeId(any()))
                .thenReturn(Optional.empty());
        when(repository.insert(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findById(REFERRER)).thenReturn(Optional.of(user(
                REFERRER, "author@example.com", NOW.minus(Duration.ofDays(30))
        )));
        when(users.findById(REFEREE)).thenReturn(Optional.of(user(
                REFEREE, "reader@example.com", NOW.minus(Duration.ofDays(1))
        )));
    }

    @Test
    void createsPendingAttributionInsideEligibilityWindow() {
        var result = service.attribute(
                REFEREE,
                codes.encode(REFERRER),
                KEY
        );

        assertThat(result.state()).isEqualTo("PENDING");
        assertThat(result.eligibleAt()).isEqualTo(NOW.plus(
                Duration.ofDays(7)
        ));
        assertThat(result.replayed()).isFalse();
        verify(repository).countRecentByReferrer(
                REFERRER,
                NOW.minus(Duration.ofDays(7))
        );
    }

    @Test
    void blocksMailboxAliasesSelfReferralAndQuotaRings() {
        when(users.findById(REFEREE)).thenReturn(Optional.of(user(
                REFEREE,
                "author+campaign@gmail.com",
                NOW.minus(Duration.ofDays(1))
        )));
        when(users.findById(REFERRER)).thenReturn(Optional.of(user(
                REFERRER,
                "author@gmail.com",
                NOW.minus(Duration.ofDays(30))
        )));

        assertConflict(() -> service.attribute(
                REFEREE, codes.encode(REFERRER), KEY
        ));
        verify(repository, never()).insert(any(), any(), any());

        when(users.findById(REFEREE)).thenReturn(Optional.of(user(
                REFEREE,
                "reader@example.com",
                NOW.minus(Duration.ofDays(1))
        )));
        when(repository.countRecentByReferrer(any(), any()))
                .thenReturn(100L);
        assertThatThrownBy(() -> service.attribute(
                REFEREE, codes.encode(REFERRER), KEY
        )).isInstanceOf(ReferralException.class)
                .extracting("kind")
                .isEqualTo(ReferralException.Kind.RATE_LIMITED);
    }

    @Test
    void rejectsExpiredNewAccountAndYoungReferrer() {
        when(users.findById(REFEREE)).thenReturn(Optional.of(user(
                REFEREE,
                "reader@example.com",
                NOW.minus(Duration.ofDays(8))
        )));
        assertConflict(() -> service.attribute(
                REFEREE, codes.encode(REFERRER), KEY
        ));

        when(users.findById(REFEREE)).thenReturn(Optional.of(user(
                REFEREE,
                "reader@example.com",
                NOW.minus(Duration.ofDays(1))
        )));
        when(users.findById(REFERRER)).thenReturn(Optional.of(user(
                REFERRER,
                "author@example.com",
                NOW.minus(Duration.ofDays(1))
        )));
        assertConflict(() -> service.attribute(
                REFEREE, codes.encode(REFERRER), KEY
        ));
    }

    @Test
    void rewardsOnlyEligibleActiveAccountsThroughBalancedLedger() {
        ReferralAttribution attribution = attribution();
        when(repository.findRewardable(NOW, 100))
                .thenReturn(List.of(attribution));
        when(repository.markRewarded(
                attribution.id(), LEDGER_ID, NOW
        )).thenReturn(true);
        when(ledger.post(any())).thenAnswer(invocation -> {
            LedgerOperations.Command command = invocation.getArgument(0);
            return new LedgerOperations.Posting(
                    LedgerTransaction.post(
                            LEDGER_ID,
                            command.type(),
                            command.referenceType(),
                            command.referenceId(),
                            command.entries(),
                            command.idempotencyKeyHash(),
                            NOW
                    ),
                    false
            );
        });
        ArgumentCaptor<LedgerOperations.Command> command =
                ArgumentCaptor.forClass(LedgerOperations.Command.class);

        assertThat(service.rewardEligible(100)).isOne();
        verify(ledger).post(command.capture());
        assertThat(command.getValue().type())
                .isEqualTo(LedgerTransaction.Type.REWARD);
        assertThat(command.getValue().entries()).hasSize(2);
        assertThat(command.getValue().entries().stream()
                .mapToLong(entry -> entry.amountXu()).sum())
                .isEqualTo(200);
    }

    @Test
    void skipsInactiveAccountsAndValidatesPublicInput() {
        when(repository.findRewardable(NOW, 100))
                .thenReturn(List.of(attribution()));
        when(users.findById(REFERRER)).thenReturn(Optional.empty());

        assertThat(service.rewardEligible(100)).isZero();
        verify(ledger, never()).post(any());
        assertThatThrownBy(() ->
                service.attribute(REFEREE, "bad", KEY)
        ).isInstanceOf(ReferralException.class)
                .extracting("kind")
                .isEqualTo(ReferralException.Kind.INVALID);
        assertThatThrownBy(() -> service.rewardEligible(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void returnsPrivateCodeAndAggregateSummary() {
        when(repository.findByRefereeId(REFERRER))
                .thenReturn(Optional.empty());
        when(repository.summary(REFERRER))
                .thenReturn(new ReferralRepository.Summary(4, 1, 3, 300));

        var result = service.mine(REFERRER);

        assertThat(codes.decode(result.code())).contains(REFERRER);
        assertThat(result.referredCount()).isEqualTo(4);
        assertThat(result.rewardedXu()).isEqualTo(300);
    }

    private static IdentityUserDirectory.IdentityUser user(
            String id,
            String email,
            Instant createdAt
    ) {
        return new IdentityUserDirectory.IdentityUser(
                id, email, Set.of("USER"), "ACTIVE", createdAt
        );
    }

    private ReferralAttribution attribution() {
        return new ReferralAttribution(
                "40000000-0000-4000-8000-000000000001",
                REFERRER,
                REFEREE,
                codes.hash(codes.encode(REFERRER)),
                "referral-2026.1",
                100,
                ReferralAttribution.State.PENDING,
                List.of(),
                null,
                NOW.minus(Duration.ofDays(7)),
                NOW,
                null
        );
    }

    private static void assertConflict(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ReferralException.class)
                .extracting("kind")
                .isEqualTo(ReferralException.Kind.CONFLICT);
    }

    private static final class TestReferralCodeCodec
            implements ReferralCodeCodec {

        @Override
        public String encode(String userId) {
            return "ref-" + userId;
        }

        @Override
        public Optional<String> decode(String code) {
            if (!code.startsWith("ref-")) {
                return Optional.empty();
            }
            return Optional.of(code.substring(4));
        }

        @Override
        public String hash(String code) {
            try {
                return HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(
                                code.getBytes(StandardCharsets.UTF_8)
                        )
                );
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
