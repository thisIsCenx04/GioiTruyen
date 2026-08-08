package com.storyplatform.discovery.application;

import com.storyplatform.discovery.application.port.HomeReadModelRepository;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class HomeService implements HomeOperations {

    public static final String DEFAULT_LOCALE = "vi-VN";
    private static final Pattern LOCALE = Pattern.compile(
            "[a-z]{2}(?:-[A-Z]{2})?"
    );

    private final HomeReadModelRepository models;
    private final HomeSectionBuilder builder;
    private final Clock clock;

    public HomeService(
            HomeReadModelRepository models,
            HomeSectionBuilder builder,
            Clock clock
    ) {
        this.models = Objects.requireNonNull(models, "models");
        this.builder = Objects.requireNonNull(builder, "builder");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public HomeReadModel get(String requestedLocale) {
        String locale = normalizeLocale(requestedLocale);
        return models.find(locale).orElseGet(() -> rebuild(
                locale,
                "bootstrap-" + UUID.randomUUID()
        ));
    }

    @Override
    public HomeReadModel rebuild(String requestedLocale, String version) {
        String locale = normalizeLocale(requestedLocale);
        if (version == null || version.isBlank() || version.length() > 128) {
            throw new IllegalArgumentException(
                    "home model version is invalid"
            );
        }
        HomeReadModel model = builder.build(
                locale,
                version,
                clock.instant()
        );
        models.replace(model);
        return model;
    }

    private static String normalizeLocale(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_LOCALE;
        }
        String normalized = value.replace('_', '-');
        String[] parts = normalized.split("-", -1);
        normalized = parts.length == 1
                ? parts[0].toLowerCase(Locale.ROOT)
                : parts[0].toLowerCase(Locale.ROOT)
                        + "-"
                        + parts[1].toUpperCase(Locale.ROOT);
        if (!LOCALE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("locale is invalid");
        }
        return normalized;
    }
}
