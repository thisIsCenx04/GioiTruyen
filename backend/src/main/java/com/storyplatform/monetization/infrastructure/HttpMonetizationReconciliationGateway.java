package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.port
        .MonetizationReconciliationGateway;
import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class HttpMonetizationReconciliationGateway
        implements MonetizationReconciliationGateway {

    private final HttpClient http;
    private final ObjectMapper json;
    private final URI endpoint;
    private final String provider;
    private final String token;
    private final Duration timeout;

    public HttpMonetizationReconciliationGateway(
            HttpClient http,
            ObjectMapper json,
            URI endpoint,
            String provider,
            String token,
            Duration timeout
    ) {
        this.http = Objects.requireNonNull(http);
        this.json = Objects.requireNonNull(json);
        this.endpoint = requireHttps(endpoint);
        if (provider == null
                || !provider.matches("[a-z0-9][a-z0-9-]{1,31}")
                || token == null
                || token.length() < 16
                || timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException(
                    "Reconciliation provider configuration is invalid."
            );
        }
        this.provider = provider;
        this.token = token;
        this.timeout = timeout;
    }

    @Override
    public Statement fetch(Instant from, Instant to) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri(from, to))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            if (response.statusCode() < 200
                    || response.statusCode() >= 300
                    || response.body().length > 1_048_576) {
                throw new IllegalStateException(
                        "Reconciliation provider response is invalid."
                );
            }
            StatementResponse value = json.readValue(
                    response.body(),
                    StatementResponse.class
            );
            if (!provider.equals(value.provider())
                    || value.entries() == null) {
                throw new IllegalStateException(
                        "Reconciliation statement provider is invalid."
                );
            }
            return new Statement(
                    provider,
                    value.entries().stream()
                            .map(EntryResponse::toDomain)
                            .toList()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Reconciliation provider call was interrupted.",
                    exception
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Reconciliation provider call failed.",
                    exception
            );
        }
    }

    private URI uri(Instant from, Instant to) {
        String separator = endpoint.getQuery() == null ? "?" : "&";
        return URI.create(endpoint + separator
                + "from=" + encode(from.toString())
                + "&to=" + encode(to.toString()));
    }

    private static String encode(String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private static URI requireHttps(URI value) {
        if (value == null
                || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null) {
            throw new IllegalArgumentException(
                    "Reconciliation endpoint must use HTTPS."
            );
        }
        return value;
    }

    private record StatementResponse(
            String provider,
            List<EntryResponse> entries
    ) {
    }

    private record EntryResponse(
            String subjectType,
            String providerReference,
            long amount,
            String status,
            Instant occurredAt
    ) {
        MonetizationReconciliationGateway.Entry toDomain() {
            return new MonetizationReconciliationGateway.Entry(
                    MonetizationReconciliationCase.SubjectType.valueOf(
                            subjectType
                    ),
                    providerReference,
                    amount,
                    Status.valueOf(status),
                    occurredAt
            );
        }
    }
}
