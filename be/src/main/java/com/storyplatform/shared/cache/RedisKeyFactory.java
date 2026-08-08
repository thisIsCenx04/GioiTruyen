package com.storyplatform.shared.cache;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Builds bounded, versioned keys without accepting raw PII, tokens or delimiters.
 */
public final class RedisKeyFactory {

    private static final Pattern SAFE_SEGMENT = Pattern.compile(
            "[a-zA-Z0-9][a-zA-Z0-9._~-]{0,127}"
    );

    private final String rootPrefix;

    public RedisKeyFactory(
            String applicationPrefix,
            String keyVersion,
            String environment
    ) {
        rootPrefix = String.join(
                ":",
                requireSafeSegment(applicationPrefix, "applicationPrefix"),
                requireSafeSegment(keyVersion, "keyVersion"),
                requireSafeSegment(environment, "environment")
        );
    }

    public RedisKey create(
            RedisNamespace namespace,
            String... segments
    ) {
        Objects.requireNonNull(namespace, "namespace");
        if (segments == null || segments.length == 0) {
            throw new IllegalArgumentException(
                    "Redis key requires at least one segment"
            );
        }

        StringBuilder key = new StringBuilder(rootPrefix)
                .append(':')
                .append(namespace.keySegment());
        for (int index = 0; index < segments.length; index++) {
            key.append(':').append(requireSafeSegment(
                    segments[index],
                    "segments[" + index + "]"
            ));
        }
        return new RedisKey(namespace, key.toString());
    }

    private static String requireSafeSegment(
            String value,
            String name
    ) {
        if (value == null || !SAFE_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    name + " must be a bounded, opaque Redis key segment"
            );
        }
        return value;
    }
}
