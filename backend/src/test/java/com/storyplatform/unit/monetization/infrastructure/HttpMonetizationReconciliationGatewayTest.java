package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.port
        .MonetizationReconciliationGateway;
import com.storyplatform.monetization.domain
        .MonetizationReconciliationCase;
import com.storyplatform.monetization.infrastructure
        .HttpMonetizationReconciliationGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpMonetizationReconciliationGatewayTest {

    private static final Instant FROM =
            Instant.parse("2026-07-24T00:00:00Z");
    private static final Instant TO =
            Instant.parse("2026-07-25T00:00:00Z");
    private final HttpClient http = mock(HttpClient.class);

    @Test
    void fetchesAuthenticatedBoundedStatement() throws Exception {
        doReturn(response(200, """
                {
                  "provider": "bank-provider",
                  "entries": [{
                    "subjectType": "WITHDRAWAL",
                    "providerReference": "provider-001",
                    "amount": 80000,
                    "status": "PAID",
                    "occurredAt": "2026-07-24T01:00:00Z"
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8)))
                .when(http).send(any(), any());
        var request = ArgumentCaptor.forClass(HttpRequest.class);

        var statement = gateway().fetch(FROM, TO);

        verify(http).send(request.capture(), any());
        assertThat(request.getValue().uri().getQuery())
                .contains("from=", "to=");
        assertThat(request.getValue().headers().firstValue(
                "Authorization"
        )).contains("Bearer secret-reconciliation-token");
        assertThat(statement.entries()).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.subjectType()).isEqualTo(
                            MonetizationReconciliationCase.SubjectType
                                    .WITHDRAWAL
                    );
                    assertThat(entry.status()).isEqualTo(
                            MonetizationReconciliationGateway.Status.PAID
                    );
                });
    }

    @Test
    void rejectsProviderMismatchAndUnsafeConfiguration()
            throws Exception {
        doReturn(response(200, """
                {"provider":"other","entries":[]}
                """.getBytes(StandardCharsets.UTF_8)))
                .when(http).send(any(), any());
        assertThatThrownBy(() -> gateway().fetch(FROM, TO))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new
                HttpMonetizationReconciliationGateway(
                http,
                new ObjectMapper(),
                URI.create("http://provider.example/reconciliation"),
                "bank-provider",
                "secret-reconciliation-token",
                Duration.ofSeconds(5)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private HttpMonetizationReconciliationGateway gateway() {
        return new HttpMonetizationReconciliationGateway(
                http,
                new ObjectMapper(),
                URI.create("https://provider.example/reconciliation"),
                "bank-provider",
                "secret-reconciliation-token",
                Duration.ofSeconds(5)
        );
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(
            int status,
            byte[] body
    ) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
