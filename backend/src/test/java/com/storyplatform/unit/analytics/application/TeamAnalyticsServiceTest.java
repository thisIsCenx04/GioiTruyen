package com.storyplatform.unit.analytics.application;

import com.storyplatform.analytics.application.TeamAnalyticsException;
import com.storyplatform.analytics.application.TeamAnalyticsService;
import com.storyplatform.analytics.application.port.TeamAnalyticsRepository;
import com.storyplatform.teams.application.contract.TeamPermissionAuthorizer;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamAnalyticsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");
    private static final String TEAM =
            "10000000-0000-4000-8000-000000000001";
    private final TeamPermissionAuthorizer permissions =
            mock(TeamPermissionAuthorizer.class);
    private final TeamAnalyticsRepository repository =
            mock(TeamAnalyticsRepository.class);
    private final TeamAnalyticsService service = new TeamAnalyticsService(
            permissions,
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void summarizesDailyQualityAndInvalidReasons() {
        when(permissions.allows("actor", TEAM, "analytics:read"))
                .thenReturn(true);
        Instant from = Instant.parse("2026-07-19T00:00:00Z");
        when(repository.daily(TEAM, from, NOW)).thenReturn(List.of(
                row("2026-07-20T00:00:00Z", 20, 12, 9, 3,
                        Map.of("DUPLICATE", 2L, "BOT_SIGNAL", 1L)),
                row("2026-07-21T00:00:00Z", 10, 5, 4, 1,
                        Map.of("DUPLICATE", 1L))
        ));

        var report = service.report("actor", TEAM, "7d");

        assertThat(report.period()).isEqualTo("7D");
        assertThat(report.from()).isEqualTo(from);
        assertThat(report.totals().rawEvents()).isEqualTo(30);
        assertThat(report.totals().validViews()).isEqualTo(13);
        assertThat(report.totals().invalidViews()).isEqualTo(4);
        assertThat(report.totals().qualityRate()).isEqualTo(0.7647);
        assertThat(report.reasons()).extracting(value -> value.code())
                .containsExactly("DUPLICATE", "BOT_SIGNAL");
        assertThat(report.series()).isSortedAccordingTo(
                java.util.Comparator.comparing(value -> value.start())
        );
        verify(repository).daily(TEAM, from, NOW);
    }

    @Test
    void rejectsMissingPermissionBeforeReadingAnalytics() {
        when(permissions.allows("actor", TEAM, "analytics:read"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.report("actor", TEAM, "30D"))
                .isInstanceOf(TeamAnalyticsException.class)
                .extracting("kind")
                .isEqualTo(TeamAnalyticsException.Kind.FORBIDDEN);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsInvalidTeamAndUnboundedPeriod() {
        assertThatThrownBy(() -> service.report(
                "actor", "not-a-team", "30D"
        )).isInstanceOf(TeamAnalyticsException.class);
        assertThatThrownBy(() -> service.report(
                "actor", TEAM, "365D"
        )).isInstanceOf(TeamAnalyticsException.class);
        verifyNoInteractions(repository);
    }

    private static TeamAnalyticsRepository.DailyAggregate row(
            String start,
            long raw,
            long completed,
            long valid,
            long invalid,
            Map<String, Long> reasons
    ) {
        return new TeamAnalyticsRepository.DailyAggregate(
                Instant.parse(start),
                raw,
                completed,
                valid,
                invalid,
                reasons
        );
    }
}
