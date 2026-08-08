package com.storyplatform.unit.discovery.application;

import com.storyplatform.discovery.application.RankingService;
import com.storyplatform.discovery.application.port.RankingRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private final RankingRepository repository = mock(RankingRepository.class);
    private final RankingService service = new RankingService(
            repository,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void returnsRankedValidViewsWithExplicitSnapshot() {
        when(repository.stories(
                any(), any(), eq(RankingRepository.Metric.VALID_VIEWS), eq(20)
        )).thenReturn(List.of(
                row("story-1", 120, 140, 20, 120),
                row("story-2", 90, 100, 10, 90)
        ));

        var page = service.stories(
                "WEEK", "VALID_VIEWS", NOW, 20
        );

        assertThat(page.period()).isEqualTo("WEEK");
        assertThat(page.metric()).isEqualTo("VALID_VIEWS");
        assertThat(page.asOf())
                .isEqualTo(Instant.parse("2026-07-24T00:00:00Z"));
        assertThat(page.fallback()).isFalse();
        assertThat(page.items()).extracting(item -> item.rank())
                .containsExactly(1, 2);
        assertThat(page.items().getFirst().validViews()).isEqualTo(120);
        assertThat(page.items().getFirst().invalidViews()).isEqualTo(20);
    }

    @Test
    void fallsBackToMonthAndSupportsTeamQualityRanking() {
        when(repository.teams(
                any(), any(), eq(RankingRepository.Metric.QUALITY_RATE), eq(10)
        )).thenReturn(
                List.of(),
                List.of(row("team", 10, 12, 2, 0.8333))
        );

        var page = service.teams(
                "DAY", "QUALITY_RATE", null, 10
        );

        assertThat(page.fallback()).isTrue();
        assertThat(page.period()).isEqualTo("MONTH");
        assertThat(page.subject()).isEqualTo("TEAM");
        assertThat(page.items()).hasSize(1);
        verify(repository, org.mockito.Mockito.times(2)).teams(
                any(), any(), eq(RankingRepository.Metric.QUALITY_RATE), eq(10)
        );
    }

    @Test
    void validatesEnumsLimitAndSnapshotBounds() {
        assertThatThrownBy(() -> service.stories(
                "YEAR", "VALID_VIEWS", NOW, 20
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stories(
                "DAY", "CLICKS", NOW, 20
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stories(
                "DAY", "VALID_VIEWS", NOW, 0
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stories(
                "DAY",
                "VALID_VIEWS",
                NOW.plusSeconds(301),
                20
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stories(
                "DAY",
                "VALID_VIEWS",
                NOW.minusSeconds(367L * 86400),
                20
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static RankingRepository.RankedSubject row(
            String id,
            long valid,
            long completed,
            long invalid,
            double score
    ) {
        return new RankingRepository.RankedSubject(
                id,
                "Name",
                "team",
                valid,
                completed,
                invalid,
                score
        );
    }
}
