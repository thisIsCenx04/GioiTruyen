package com.storyplatform.publishing.infrastructure;

import com.storyplatform.publishing.application.EdgePropagationGateway;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HttpEdgePropagationGateway
        implements EdgePropagationGateway {

    private final RestClient client;
    private final URI cloudflareEndpoint;
    private final URI isrEndpoint;
    private final String cloudflareToken;
    private final String isrToken;

    public HttpEdgePropagationGateway(
            RestClient client,
            String cloudflareEndpoint,
            String isrEndpoint,
            String cloudflareToken,
            String isrToken
    ) {
        this(
                client,
                URI.create(cloudflareEndpoint),
                URI.create(isrEndpoint),
                cloudflareToken,
                isrToken
        );
    }

    public HttpEdgePropagationGateway(
            RestClient client,
            URI cloudflareEndpoint,
            URI isrEndpoint,
            String cloudflareToken,
            String isrToken
    ) {
        this.client = Objects.requireNonNull(client);
        this.cloudflareEndpoint = secure(
                cloudflareEndpoint,
                "cloudflareEndpoint"
        );
        this.isrEndpoint = secure(isrEndpoint, "isrEndpoint");
        this.cloudflareToken = required(
                cloudflareToken,
                "cloudflareToken"
        );
        this.isrToken = required(isrToken, "isrToken");
    }

    @Override
    public void invalidate(
            String idempotencyKey,
            List<String> targets
    ) {
        List<String> cacheTags = targets.stream()
                .filter(value -> value.startsWith("cloudflare:tag:"))
                .map(value -> value.substring("cloudflare:tag:".length()))
                .toList();
        List<String> isrTargets = targets.stream()
                .filter(value -> value.startsWith("next:isr:"))
                .map(value -> value.substring("next:isr:".length()))
                .toList();
        if (!cacheTags.isEmpty()) {
            client.post()
                    .uri(cloudflareEndpoint)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + cloudflareToken
                    )
                    .header("Idempotency-Key", idempotencyKey)
                    .body(Map.of("tags", cacheTags))
                    .retrieve()
                    .toBodilessEntity();
        }
        if (!isrTargets.isEmpty()) {
            client.post()
                    .uri(isrEndpoint)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + isrToken
                    )
                    .header("Idempotency-Key", idempotencyKey)
                    .body(Map.of(
                            "eventId", idempotencyKey,
                            "targets", isrTargets
                    ))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    private static URI secure(URI value, String field) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme())) {
            throw new IllegalArgumentException(
                    field + " must use https"
            );
        }
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.length() < 32) {
            throw new IllegalArgumentException(
                    field + " requires at least 32 characters"
            );
        }
        return value;
    }
}
