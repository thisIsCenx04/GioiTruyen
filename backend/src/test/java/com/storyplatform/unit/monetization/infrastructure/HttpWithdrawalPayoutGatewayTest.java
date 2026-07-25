package com.storyplatform.unit.monetization.infrastructure;

import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import com.storyplatform.monetization.infrastructure
        .HttpWithdrawalPayoutGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpWithdrawalPayoutGatewayTest {

    private static final URI ENDPOINT =
            URI.create("https://payout.example.test/v1/transfers");
    private static final String TOKEN = "secret-provider-token";
    private final HttpClient http = mock(HttpClient.class);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sendsAuthenticatedIdempotentRequestAndDecodesResult()
            throws Exception {
        HttpResponse<byte[]> response = response(
                200,
                """
                {
                  "status": "PAID",
                  "providerReference": "provider-001",
                  "errorCode": null
                }
                """.getBytes(StandardCharsets.UTF_8)
        );
        doReturn(response).when(http).send(any(), any());
        var request = ArgumentCaptor.forClass(HttpRequest.class);

        var result = gateway().submit(command());

        verify(http).send(request.capture(), any());
        assertThat(request.getValue().headers().firstValue(
                "Authorization"
        )).contains("Bearer " + TOKEN);
        assertThat(request.getValue().headers().firstValue(
                "Idempotency-Key"
        )).contains(command().idempotencyKey());
        assertThat(request.getValue().timeout())
                .contains(Duration.ofSeconds(5));
        assertThat(result.status())
                .isEqualTo(WithdrawalPayoutGateway.Status.PAID);
        assertThat(result.providerReference()).isEqualTo("provider-001");
    }

    @Test
    void rejectsNonSuccessOversizedAndMalformedResponses()
            throws Exception {
        doReturn(
                        response(500, new byte[0]),
                        response(200, new byte[65_537]),
                        response(200, "{}".getBytes(StandardCharsets.UTF_8))
                ).when(http).send(any(), any());

        assertProviderFailure();
        assertProviderFailure();
        assertProviderFailure();
    }

    @Test
    void restoresInterruptFlagWhenProviderCallIsInterrupted()
            throws Exception {
        doThrow(new InterruptedException("stop"))
                .when(http).send(any(), any());
        try {
            assertProviderFailure();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsUnsafeEndpointTokenAndTimeoutConfiguration() {
        assertThatThrownBy(() -> new HttpWithdrawalPayoutGateway(
                http,
                json,
                URI.create("http://payout.example.test"),
                TOKEN,
                Duration.ofSeconds(5)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpWithdrawalPayoutGateway(
                http,
                json,
                URI.create("https:///missing-host"),
                TOKEN,
                Duration.ofSeconds(5)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpWithdrawalPayoutGateway(
                http, json, ENDPOINT, "short", Duration.ofSeconds(5)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpWithdrawalPayoutGateway(
                http, json, ENDPOINT, TOKEN, Duration.ZERO
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpWithdrawalPayoutGateway(
                http, json, ENDPOINT, TOKEN, Duration.ofSeconds(31)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertProviderFailure() {
        assertThatThrownBy(() -> gateway().submit(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Payout provider call");
    }

    private HttpWithdrawalPayoutGateway gateway() {
        return new HttpWithdrawalPayoutGateway(
                http,
                json,
                ENDPOINT,
                TOKEN,
                Duration.ofSeconds(5)
        );
    }

    private static WithdrawalPayoutGateway.Command command() {
        return new WithdrawalPayoutGateway.Command(
                "10000000-0000-4000-8000-000000000001",
                "withdrawal:10000000-0000-4000-8000-000000000001",
                80_000,
                "encrypted:v1:provider-token"
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
