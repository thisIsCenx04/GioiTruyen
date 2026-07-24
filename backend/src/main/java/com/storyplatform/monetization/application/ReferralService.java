package com.storyplatform.monetization.application;

import com.storyplatform.identity.application.contract.IdentityUserDirectory;
import com.storyplatform.monetization.application.port.ReferralCodeCodec;
import com.storyplatform.monetization.application.port.ReferralRepository;
import com.storyplatform.monetization.domain.LedgerEntry;
import com.storyplatform.monetization.domain.LedgerTransaction;
import com.storyplatform.monetization.domain.ReferralAttribution;
import com.storyplatform.monetization.domain.WalletAccount;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ReferralService implements ReferralOperations {

    private static final String ROUTE = "/referrals/me/attribution";
    private static final String CLEARING_OWNER = "referral-clearing";
    private static final Set<String> PLUS_ALIAS_DOMAINS = Set.of(
            "gmail.com",
            "googlemail.com",
            "outlook.com",
            "hotmail.com"
    );
    private final ReferralRepository repository;
    private final ReferralCodeCodec codes;
    private final IdentityUserDirectory users;
    private final WalletOperations wallets;
    private final LedgerOperations ledger;
    private final ReferralRule rule;
    private final Clock clock;

    public ReferralService(
            ReferralRepository repository,
            ReferralCodeCodec codes,
            IdentityUserDirectory users,
            WalletOperations wallets,
            LedgerOperations ledger,
            ReferralRule rule,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.codes = Objects.requireNonNull(codes);
        this.users = Objects.requireNonNull(users);
        this.wallets = Objects.requireNonNull(wallets);
        this.ledger = Objects.requireNonNull(ledger);
        this.rule = Objects.requireNonNull(rule);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public AttributionView attribute(
            String refereeId,
            String code,
            String idempotencyKey
    ) {
        String safeCode = requireCode(code);
        String keyHash = hash(
                refereeId + "\n" + ROUTE + "\n"
                        + requireKey(idempotencyKey)
        );
        String requestHash = hash(codes.hash(safeCode));
        var replay = repository.findByIdempotencyKeyHash(keyHash);
        if (replay.isPresent()) {
            var stored = replay.orElseThrow();
            if (!MessageDigest.isEqual(
                    stored.requestHash().getBytes(
                            StandardCharsets.US_ASCII
                    ),
                    requestHash.getBytes(StandardCharsets.US_ASCII)
            )) {
                throw conflict("Idempotency-Key was reused.");
            }
            return view(stored.attribution(), true);
        }
        String referrerId = codes.decode(safeCode).orElseThrow(() ->
                new ReferralException(
                        "Referral code was not found.",
                        ReferralException.Kind.NOT_FOUND
                ));
        var existing = repository.findByRefereeId(refereeId);
        if (existing.isPresent()) {
            if (!existing.orElseThrow().referrerId().equals(referrerId)) {
                throw conflict("This account already has an attribution.");
            }
            return view(existing.orElseThrow(), true);
        }
        Instant now = clock.instant();
        IdentityUserDirectory.IdentityUser referee =
                activeUser(refereeId);
        IdentityUserDirectory.IdentityUser referrer =
                activeUser(referrerId);
        requireEligible(referee, referrer, now);
        if (repository.countRecentByReferrer(
                referrerId,
                now.minus(rule.attributionWindow())
        ) >= rule.maximumRecentReferrals()) {
            throw new ReferralException(
                    "Referral quota is temporarily exhausted.",
                    ReferralException.Kind.RATE_LIMITED
            );
        }
        ReferralAttribution attribution = new ReferralAttribution(
                UUID.randomUUID().toString(),
                referrerId,
                refereeId,
                codes.hash(safeCode),
                rule.version(),
                rule.rewardXu(),
                ReferralAttribution.State.PENDING,
                List.of(),
                null,
                now,
                now.plus(rule.rewardDelay()),
                null
        );
        return view(repository.insert(
                attribution,
                keyHash,
                requestHash
        ), false);
    }

    @Override
    public ReferralView mine(String userId) {
        var attribution = repository.findByRefereeId(userId);
        ReferralRepository.Summary summary = repository.summary(userId);
        return new ReferralView(
                codes.encode(userId),
                attribution.map(ReferralAttribution::referrerId)
                        .orElse(null),
                attribution.map(value -> value.state().name())
                        .orElse(null),
                summary.referredCount(),
                summary.pendingCount(),
                summary.rewardedCount(),
                summary.rewardedXu()
        );
    }

    @Override
    public int rewardEligible(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "Referral reward limit must be 1 to 100."
            );
        }
        int rewarded = 0;
        for (ReferralAttribution attribution
                : repository.findRewardable(clock.instant(), limit)) {
            if (!active(attribution.referrerId())
                    || !active(attribution.refereeId())) {
                continue;
            }
            reward(attribution);
            rewarded++;
        }
        return rewarded;
    }

    private void reward(ReferralAttribution attribution) {
        wallets.open(WalletAccount.OwnerType.PLATFORM, CLEARING_OWNER);
        wallets.open(
                WalletAccount.OwnerType.USER,
                attribution.referrerId()
        );
        var posting = ledger.post(new LedgerOperations.Command(
                LedgerTransaction.Type.REWARD,
                "referral_reward",
                attribution.id(),
                List.of(
                        entry(
                                WalletAccount.OwnerType.PLATFORM,
                                CLEARING_OWNER,
                                LedgerEntry.Side.DEBIT,
                                attribution.rewardXu()
                        ),
                        entry(
                                WalletAccount.OwnerType.USER,
                                attribution.referrerId(),
                                LedgerEntry.Side.CREDIT,
                                attribution.rewardXu()
                        )
                ),
                hash("referral-reward\n" + attribution.id()),
                attribution.id(),
                "system:referral-reward",
                null
        ));
        if (!repository.markRewarded(
                attribution.id(),
                posting.transaction().id(),
                clock.instant()
        )) {
            throw conflict("Referral reward changed concurrently.");
        }
    }

    private void requireEligible(
            IdentityUserDirectory.IdentityUser referee,
            IdentityUserDirectory.IdentityUser referrer,
            Instant now
    ) {
        if (referee.id().equals(referrer.id())
                || canonicalMailbox(referee.email()).equals(
                canonicalMailbox(referrer.email())
        )) {
            throw conflict("Self-referral is not allowed.");
        }
        if (referee.createdAt().isAfter(now)
                || referee.createdAt().isBefore(
                now.minus(rule.attributionWindow())
        )) {
            throw conflict("Referral attribution window has closed.");
        }
        if (referrer.createdAt().isAfter(
                now.minus(rule.referrerMinimumAge())
        )) {
            throw conflict("Referrer account is not yet eligible.");
        }
    }

    private IdentityUserDirectory.IdentityUser activeUser(String id) {
        return users.findById(id)
                .filter(IdentityUserDirectory.IdentityUser::active)
                .orElseThrow(() -> new ReferralException(
                        "Referral account was not found.",
                        ReferralException.Kind.NOT_FOUND
                ));
    }

    private boolean active(String id) {
        return users.findById(id)
                .filter(IdentityUserDirectory.IdentityUser::active)
                .isPresent();
    }

    private static LedgerEntry entry(
            WalletAccount.OwnerType type,
            String ownerId,
            LedgerEntry.Side side,
            long amount
    ) {
        return new LedgerEntry(
                WalletOperations.accountId(type, ownerId),
                side,
                amount
        );
    }

    private static String canonicalMailbox(String email) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        int separator = normalized.lastIndexOf('@');
        if (separator < 1) {
            return normalized;
        }
        String local = normalized.substring(0, separator);
        String domain = normalized.substring(separator + 1);
        if ("googlemail.com".equals(domain)) {
            domain = "gmail.com";
        }
        if (PLUS_ALIAS_DOMAINS.contains(domain)) {
            int plus = local.indexOf('+');
            if (plus >= 0) {
                local = local.substring(0, plus);
            }
        }
        return local + "@" + domain;
    }

    private static String requireCode(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{32,64}")) {
            throw new ReferralException(
                    "Referral code is invalid.",
                    ReferralException.Kind.INVALID
            );
        }
        return value;
    }

    private static String requireKey(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9._:-]{16,128}")) {
            throw new ReferralException(
                    "A valid Idempotency-Key is required.",
                    ReferralException.Kind.INVALID
            );
        }
        return value;
    }

    private static AttributionView view(
            ReferralAttribution value,
            boolean replayed
    ) {
        return new AttributionView(
                value.id(),
                value.state().name(),
                value.attributedAt(),
                value.eligibleAt(),
                replayed
        );
    }

    private static ReferralException conflict(String message) {
        return new ReferralException(
                message,
                ReferralException.Kind.CONFLICT
        );
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
