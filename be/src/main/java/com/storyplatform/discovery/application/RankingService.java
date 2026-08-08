package com.storyplatform.discovery.application;

import com.storyplatform.discovery.application.port.RankingRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class RankingService implements RankingOperations {

    private final RankingRepository repository;
    private final Clock clock;

    public RankingService(RankingRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RankingPage stories(
            String period,
            String metric,
            Instant asOf,
            int limit
    ) {
        return rank("STORY", period, metric, asOf, limit);
    }

    @Override
    public RankingPage teams(
            String period,
            String metric,
            Instant asOf,
            int limit
    ) {
        return rank("TEAM", period, metric, asOf, limit);
    }

    private RankingPage rank(
            String subject,
            String periodValue,
            String metricValue,
            Instant requestedAsOf,
            int limit
    ) {
        Period period = value(periodValue, Period.class, "period");
        RankingRepository.Metric metric = value(
                metricValue,
                RankingRepository.Metric.class,
                "metric"
        );
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        Instant now = clock.instant();
        Instant asOf = requestedAsOf == null ? now : requestedAsOf;
        if (asOf.isAfter(now.plusSeconds(300))
                || asOf.isBefore(now.minus(Duration.ofDays(366)))) {
            throw new IllegalArgumentException(
                    "ranking asOf is outside the supported range"
            );
        }
        Instant end = asOf.truncatedTo(ChronoUnit.DAYS);
        List<RankingRepository.RankedSubject> rows = query(
                subject,
                end.minus(period.duration),
                end,
                metric,
                safeLimit
        );
        boolean fallback = rows.isEmpty() && period != Period.MONTH;
        Period used = fallback ? Period.MONTH : period;
        if (fallback) {
            rows = query(
                    subject,
                    end.minus(used.duration),
                    end,
                    metric,
                    safeLimit
            );
        }
        List<RankingRepository.RankedSubject> rankedRows = rows;
        List<RankingItem> items = java.util.stream.IntStream
                .range(0, rankedRows.size())
                .mapToObj(index -> item(
                        index + 1,
                        rankedRows.get(index)
                ))
                .toList();
        return new RankingPage(
                subject,
                used.name(),
                metric.name(),
                end,
                fallback,
                items
        );
    }

    private List<RankingRepository.RankedSubject> query(
            String subject,
            Instant from,
            Instant to,
            RankingRepository.Metric metric,
            int limit
    ) {
        return "STORY".equals(subject)
                ? repository.stories(from, to, metric, limit)
                : repository.teams(from, to, metric, limit);
    }

    private static RankingItem item(
            int rank,
            RankingRepository.RankedSubject value
    ) {
        return new RankingItem(
                rank,
                value.id(),
                value.name(),
                value.teamId(),
                value.validViews(),
                value.completedViews(),
                value.invalidViews(),
                value.score()
        );
    }

    private static <T extends Enum<T>> T value(
            String value,
            Class<T> type,
            String field
    ) {
        try {
            return Enum.valueOf(
                    type,
                    Objects.requireNonNullElse(value, "")
                            .toUpperCase(Locale.ROOT)
            );
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "ranking " + field + " is invalid"
            );
        }
    }

    private enum Period {
        DAY(Duration.ofDays(1)),
        WEEK(Duration.ofDays(7)),
        MONTH(Duration.ofDays(30));

        private final Duration duration;

        Period(Duration duration) {
            this.duration = duration;
        }
    }
}
