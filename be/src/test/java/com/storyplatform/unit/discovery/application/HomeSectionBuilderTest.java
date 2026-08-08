package com.storyplatform.unit.discovery.application;

import com.storyplatform.discovery.application.HomeReadModel;
import com.storyplatform.discovery.application.HomeSectionBuilder;
import com.storyplatform.discovery.application.HomeStorySummary;
import com.storyplatform.discovery.application.port.HomeStorySource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeSectionBuilderTest {

    @Test
    void ordersSectionsAppliesFallbackAndCapsPayload() {
        HomeStorySource source = mock(HomeStorySource.class);
        List<HomeStorySummary> latest = IntStream.rangeClosed(1, 13)
                .mapToObj(HomeSectionBuilderTest::story)
                .toList();
        when(source.find(HomeStorySource.Filter.LATEST, 13))
                .thenReturn(latest);
        when(source.find(
                HomeStorySource.Filter.COMPLETED,
                13
        )).thenReturn(List.of());
        when(source.find(HomeStorySource.Filter.ORIGINAL, 13))
                .thenReturn(List.of(story(99)));

        HomeReadModel model = new HomeSectionBuilder(source).build(
                "vi-VN",
                "version-1",
                Instant.EPOCH
        );

        assertThat(model.sections())
                .extracting(HomeReadModel.HomeSection::type)
                .containsExactly(
                        HomeReadModel.SectionType.LATEST,
                        HomeReadModel.SectionType.COMPLETED,
                        HomeReadModel.SectionType.ORIGINAL
                );
        assertThat(model.sections().get(0).stories()).hasSize(12);
        assertThat(model.sections().get(1).stories())
                .isEqualTo(model.sections().get(0).stories());
        assertThat(model.sections().get(2).stories())
                .containsExactly(story(99));
        assertThat(model.sections().get(0).title())
                .isEqualTo("Mới cập nhật");
    }

    private static HomeStorySummary story(int number) {
        return new HomeStorySummary(
                "10000000-0000-4000-8000-%012d".formatted(number),
                "20000000-0000-4000-8000-000000000001",
                "story-" + number,
                "Story " + number,
                null,
                Instant.EPOCH.plusSeconds(number)
        );
    }
}
