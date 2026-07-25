package com.storyplatform.monetization.infrastructure;

import com.storyplatform.monetization.application.port
        .WithdrawalPayoutGateway;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class HttpWithdrawalPayoutGateway
        implements WithdrawalPayoutGateway {

    private final HttpClient http;
    private final ObjectMapper json;
    private final URI endpoint;
    private final String token;
    private final Duration timeout;

    public HttpWithdrawalPayoutGateway(
            HttpClient http,
            ObjectMapper json,
            URI endpoint,
            String token,
            Duration timeout
    ) {
        this.http = Objects.requireNonNull(http);
        this.json = Objects.requireNonNull(json);
        this.endpoint = requireHttps(endpoint);
        if (token == null || token.length() < 16
                || timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException(
                    "Withdrawal payout provider configuration is invalid."
            );
        }
        this.token = token;
        this.timeout = timeout;
    }

    @Override
    public Result submit(Command command) {
        try {
            byte[] body = json.writeValueAsBytes(new ProviderRequest(
                    command.withdrawalId(),
                    command.amountVnd(),
                    "VND",
                    command.encryptedDestination()
            ));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", command.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<byte[]> response = http.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            if (response.statusCode() < 200
                    || response.statusCode() >= 300
                    || response.body().length > 65_536) {
                throw new IllegalStateException(
                        "Payout provider returned an invalid response."
                );
            }
            ProviderResponse value = json.readValue(
                    response.body(),
                    ProviderResponse.class
            );
            return new Result(
                    Status.valueOf(value.status()),
                    value.providerReference(),
                    value.errorCode()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Payout provider call was interrupted.",
                    exception
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Payout provider call failed.",
                    exception
            );
        }
    }

    private static URI requireHttps(URI value) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null) {
            throw new IllegalArgumentException(
                    "Payout provider endpoint must use HTTPS."
            );
        }
        return value;
    }

    private record ProviderRequest(
            String withdrawalId,
            long amount,
            String currency,
            String destinationToken
    ) {
    }

    private record ProviderResponse(
            String status,
            String providerReference,
            String errorCode
    ) {
    }
}
