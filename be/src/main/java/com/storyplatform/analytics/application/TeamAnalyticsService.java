package com.storyplatform.analytics.application;

import com.storyplatform.analytics.application.port.TeamAnalyticsRepository;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TeamAnalyticsService implements TeamAnalyticsOperations {

    public static final String READ_ANALYTICS = "analytics:read";
    private final TeamPermissionAuthorizer permissions;
    private final TeamAnalyticsRepository repository;
    private final Clock clock;

    public TeamAnalyticsService(
            TeamPermissionAuthorizer permissions,
            TeamAnalyticsRepository repository,
            Clock clock
    ) {
        this.permissions = Objects.requireNonNull(permissions);
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public TeamAnalyticsReport report(
            String actorId,
            String teamId,
            String period
    ) {
        String team = uuid(teamId);
        Range range = Range.parse(period);
        if (!permissions.allows(actorId, team, READ_ANALYTICS)) {
            throw new TeamAnalyticsException(
                    "The analytics:read Team permission is required.",
                    TeamAnalyticsException.Kind.FORBIDDEN
            );
        }
        Instant to = clock.instant();
        Instant from = to.truncatedTo(ChronoUnit.DAYS)
                .minus(range.days() - 1L, ChronoUnit.DAYS);
        List<TeamAnalyticsRepository.DailyAggregate> rows =
                repository.daily(team, from, to);
        Map<String, Long> reasons = new HashMap<>();
        List<Bucket> series = new ArrayList<>();
        long raw = 0;
        long completed = 0;
        long valid = 0;
        long invalid = 0;
        for (var row : rows) {
            raw += row.rawEvents();
            completed += row.completedViews();
            valid += row.validViews();
            invalid += row.invalidViews();
            row.reasonCounts().forEach(
                    (code, count) -> reasons.merge(code, count, Long::sum)
            );
            series.add(new Bucket(
                    row.start(),
                    row.rawEvents(),
                    row.completedViews(),
                    row.validViews(),
                    row.invalidViews(),
                    quality(row.validViews(), row.invalidViews())
            ));
        }
        series.sort(Comparator.comparing(Bucket::start));
        List<Reason> summary = reasons.entrySet().stream()
                .map(entry -> new Reason(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(Reason::count)
                        .reversed()
                        .thenComparing(Reason::code))
                .toList();
        return new TeamAnalyticsReport(
                team,
                range.label(),
                from,
                to,
                new Totals(
                        raw,
                        completed,
                        valid,
                        invalid,
                        quality(valid, invalid)
                ),
                series,
                summary
        );
    }

    private static double quality(long valid, long invalid) {
        long classified = valid + invalid;
        if (classified == 0) {
            return 0;
        }
        return Math.round(valid * 10_000.0 / classified) / 10_000.0;
    }

    private static String uuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException exception) {
            throw new TeamAnalyticsException(
                    "A valid Team identifier is required.",
                    TeamAnalyticsException.Kind.INVALID
            );
        }
    }

    private enum Range {
        SEVEN("7D", 7),
        THIRTY("30D", 30),
        NINETY("90D", 90);

        private final String label;
        private final int days;

        Range(String label, int days) {
            this.label = label;
            this.days = days;
        }

        static Range parse(String value) {
            String normalized = value == null ? "30D" : value.toUpperCase();
            for (Range range : values()) {
                if (range.label.equals(normalized)) {
                    return range;
                }
            }
            throw new TeamAnalyticsException(
                    "Period must be one of 7D, 30D, or 90D.",
                    TeamAnalyticsException.Kind.INVALID
            );
        }

        String label() {
            return label;
        }

        int days() {
            return days;
        }
    }
}
