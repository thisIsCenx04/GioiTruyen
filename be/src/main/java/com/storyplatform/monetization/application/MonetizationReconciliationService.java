package com.storyplatform.monetization.application;

import com.storyplatform.monetization.application.port
        .MonetizationReconciliationGateway;
import com.storyplatform.monetization.application.port
        .MonetizationReconciliationRepository;
import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;
import com.storyplatform.shared.events.IntegrationEvent;
import com.storyplatform.shared.events.persistence.OutboxAppender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class MonetizationReconciliationService
        implements MonetizationReconciliationOperations {

    private static final int MAXIMUM_ENTRIES = 10_000;
    private final MonetizationReconciliationRepository repository;
    private final OutboxAppender outbox;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public MonetizationReconciliationService(
            MonetizationReconciliationRepository repository,
            OutboxAppender outbox,
            Clock clock,
            Supplier<UUID> ids
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public Summary reconcile(
            Instant from,
            Instant to,
            MonetizationReconciliationGateway.Statement statement
    ) {
        validate(from, to, statement);
        String statementHash = statementHash(statement.entries());
        var existing = repository.findRun(statement.provider(), from, to);
        if (existing.isPresent()) {
            return replay(existing.orElseThrow(), statementHash);
        }
        Instant now = clock.instant();
        String runId = ids.get().toString();
        repository.insertRun(new MonetizationReconciliationRepository.Run(
                runId,
                statement.provider(),
                from,
                to,
                statementHash,
                MonetizationReconciliationRepository.State.RUNNING,
                null,
                now,
                null
        ));
        Map<Key, MonetizationReconciliationGateway.Entry> provider =
                providerEntries(statement.entries());
        Map<Key, MonetizationReconciliationRepository.LocalEntry> local =
                localEntries(repository.findLocalEntries(
                        statement.provider(), from, to
                ));
        Set<Key> keys = new LinkedHashSet<>(provider.keySet());
        keys.addAll(local.keySet());
        int matched = 0;
        int mismatched = 0;
        for (Key key : keys) {
            var mismatch = compare(local.get(key), provider.get(key));
            if (mismatch == null) {
                matched++;
                continue;
            }
            mismatched++;
            repository.insertCase(caseFor(
                    runId,
                    key,
                    local.get(key),
                    provider.get(key),
                    mismatch,
                    now
            ));
        }
        Summary summary = new Summary(
                runId, matched, mismatched, false
        );
        repository.complete(runId, summary, now);
        outbox.append(new IntegrationEvent(
                ids.get(),
                "monetization.reconciliation.completed",
                1,
                now,
                runId,
                "monetization_reconciliation",
                runId,
                "system:reconciliation",
                null,
                new Completed(
                        statement.provider(),
                        from,
                        to,
                        matched,
                        mismatched,
                        statementHash
                )
        ));
        return summary;
    }

    private static void validate(
            Instant from,
            Instant to,
            MonetizationReconciliationGateway.Statement statement
    ) {
        if (from == null
                || to == null
                || !to.isAfter(from)
                || Duration.between(from, to).compareTo(
                Duration.ofDays(31)
        ) > 0
                || statement == null
                || statement.provider() == null
                || !statement.provider().matches(
                "[a-z0-9][a-z0-9-]{1,31}"
        )
                || statement.entries() == null
                || statement.entries().size() > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException(
                    "Monetization reconciliation input is invalid."
            );
        }
    }

    private static Summary replay(
            MonetizationReconciliationRepository.Run run,
            String statementHash
    ) {
        if (!run.statementHash().equals(statementHash)
                || run.state()
                != MonetizationReconciliationRepository.State.COMPLETED
                || run.summary() == null) {
            throw new MonetizationReconciliationException(
                    "Reconciliation window changed or is already running."
            );
        }
        Summary value = run.summary();
        return new Summary(
                value.runId(),
                value.matched(),
                value.mismatched(),
                true
        );
    }

    private static Map<Key, MonetizationReconciliationGateway.Entry>
            providerEntries(
            List<MonetizationReconciliationGateway.Entry> entries
    ) {
        Map<Key, MonetizationReconciliationGateway.Entry> result =
                new HashMap<>();
        for (var entry : entries) {
            requireProviderEntry(entry);
            Key key = new Key(
                    entry.subjectType(),
                    entry.providerReference()
            );
            if (result.putIfAbsent(key, entry) != null) {
                throw new MonetizationReconciliationException(
                        "Provider statement contains duplicate references."
                );
            }
        }
        return result;
    }

    private static Map<Key, MonetizationReconciliationRepository.LocalEntry>
            localEntries(
            List<MonetizationReconciliationRepository.LocalEntry> entries
    ) {
        Map<Key, MonetizationReconciliationRepository.LocalEntry> result =
                new HashMap<>();
        for (var entry : entries) {
            Key key = new Key(
                    entry.subjectType(),
                    entry.providerReference()
            );
            if (result.putIfAbsent(key, entry) != null) {
                throw new MonetizationReconciliationException(
                        "Local evidence contains duplicate references."
                );
            }
        }
        return result;
    }

    private static void requireProviderEntry(
            MonetizationReconciliationGateway.Entry value
    ) {
        if (value == null
                || value.subjectType() == null
                || value.providerReference() == null
                || !value.providerReference().matches(
                "[A-Za-z0-9][A-Za-z0-9._:-]{2,127}"
        )
                || value.amount() <= 0
                || value.status() == null
                || value.occurredAt() == null) {
            throw new IllegalArgumentException(
                    "Provider reconciliation entry is invalid."
            );
        }
    }

    private static MonetizationReconciliationCase.Mismatch compare(
            MonetizationReconciliationRepository.LocalEntry local,
            MonetizationReconciliationGateway.Entry provider
    ) {
        if (local == null) {
            return MonetizationReconciliationCase.Mismatch
                    .MISSING_LOCAL_EVIDENCE;
        }
        if (provider == null) {
            return MonetizationReconciliationCase.Mismatch
                    .MISSING_PROVIDER_EVIDENCE;
        }
        if (local.amount() != provider.amount()) {
            return MonetizationReconciliationCase.Mismatch.AMOUNT_MISMATCH;
        }
        if (local.status() != provider.status()) {
            return MonetizationReconciliationCase.Mismatch.STATUS_MISMATCH;
        }
        boolean ledgerRequired = provider.status()
                == MonetizationReconciliationGateway.Status.PAID
                || local.subjectType()
                == MonetizationReconciliationCase.SubjectType.WITHDRAWAL
                && provider.status()
                == MonetizationReconciliationGateway.Status.FAILED;
        if (ledgerRequired
                && local.ledgerTransactionId() == null) {
            return MonetizationReconciliationCase.Mismatch
                    .MISSING_LEDGER_POSTING;
        }
        return null;
    }

    private MonetizationReconciliationCase caseFor(
            String runId,
            Key key,
            MonetizationReconciliationRepository.LocalEntry local,
            MonetizationReconciliationGateway.Entry provider,
            MonetizationReconciliationCase.Mismatch mismatch,
            Instant now
    ) {
        long localAmount = local == null ? 0 : local.amount();
        long providerAmount = provider == null ? 0 : provider.amount();
        String localStatus = local == null
                ? null
                : local.status().name();
        String providerStatus = provider == null
                ? null
                : provider.status().name();
        String subjectId = local == null ? null : local.subjectId();
        String evidence = hash(String.join("\n",
                key.type().name(),
                key.reference(),
                Long.toString(localAmount),
                Long.toString(providerAmount),
                Objects.toString(localStatus, ""),
                Objects.toString(providerStatus, ""),
                mismatch.name()
        ));
        return new MonetizationReconciliationCase(
                ids.get().toString(),
                runId,
                key.type(),
                subjectId,
                key.reference(),
                mismatch,
                localAmount,
                providerAmount,
                localStatus,
                providerStatus,
                recommendation(mismatch),
                evidence,
                MonetizationReconciliationCase.Status.OPEN,
                now,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static MonetizationReconciliationCase.RecommendedAction
            recommendation(
            MonetizationReconciliationCase.Mismatch mismatch
    ) {
        return switch (mismatch) {
            case MISSING_LOCAL_EVIDENCE ->
                    MonetizationReconciliationCase.RecommendedAction
                            .REPLAY_VERIFIED_EVENT;
            case MISSING_PROVIDER_EVIDENCE ->
                    MonetizationReconciliationCase.RecommendedAction
                            .CONTACT_PROVIDER;
            case MISSING_LEDGER_POSTING ->
                    MonetizationReconciliationCase.RecommendedAction
                            .VERIFY_LEDGER_AND_COMPENSATE;
            case AMOUNT_MISMATCH, STATUS_MISMATCH ->
                    MonetizationReconciliationCase.RecommendedAction
                            .INVESTIGATE_PROVIDER_EVENT;
        };
    }

    private static String statementHash(
            List<MonetizationReconciliationGateway.Entry> entries
    ) {
        List<String> canonical = new ArrayList<>(entries.size());
        for (var value : entries) {
            requireProviderEntry(value);
            canonical.add(String.join("|",
                    value.subjectType().name(),
                    value.providerReference(),
                    Long.toString(value.amount()),
                    value.status().name(),
                    value.occurredAt().toString()
            ));
        }
        canonical.sort(Comparator.naturalOrder());
        return hash(String.join("\n", canonical));
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

    private record Key(
            MonetizationReconciliationCase.SubjectType type,
            String reference
    ) {
    }

    public record Completed(
            String provider,
            Instant from,
            Instant to,
            int matched,
            int mismatched,
            String statementHash
    ) {
    }
}
