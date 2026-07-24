package com.storyplatform.unit.discovery.application;

import com.storyplatform.discovery.application.HomeReadModel;
import com.storyplatform.discovery.application.HomeSectionBuilder;
import com.storyplatform.discovery.application.HomeService;
import com.storyplatform.discovery.application.port.HomeReadModelRepository;
import com.storyplatform.discovery.application.port.HomeStorySource;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeServiceTest {

    @Test
    void returnsExistingModelWithoutRebuilding() {
        HomeReadModelRepository models =
                mock(HomeReadModelRepository.class);
        HomeReadModel existing = model("vi-VN", "v1");
        when(models.find("vi-VN")).thenReturn(Optional.of(existing));

        assertThat(service(models).get("vi_vn")).isEqualTo(existing);
    }

    @Test
    void rebuildsMissingModelsAndNormalizesLocale() {
        HomeReadModelRepository models =
                mock(HomeReadModelRepository.class);
        when(models.find("en")).thenReturn(Optional.empty());

        HomeReadModel rebuilt = service(models).get("EN");

        assertThat(rebuilt.locale()).isEqualTo("en");
        assertThat(rebuilt.sections()).hasSize(3);
        verify(models).replace(any(HomeReadModel.class));
    }

    @Test
    void rejectsInvalidLocaleAndVersion() {
        HomeReadModelRepository models =
                mock(HomeReadModelRepository.class);
        assertThatThrownBy(() -> service(models).get("invalid-locale"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(models).rebuild("vi-VN", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static HomeService service(
            HomeReadModelRepository models
    ) {
        HomeStorySource source = mock(HomeStorySource.class);
        when(source.find(any(), anyInt()))
                .thenReturn(List.of());
        return new HomeService(
                models,
                new HomeSectionBuilder(source),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        );
    }

    private static HomeReadModel model(String locale, String version) {
        return new HomeReadModel(
                locale,
                version,
                Instant.EPOCH,
                List.of()
        );
    }
}
