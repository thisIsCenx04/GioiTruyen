package com.storyplatform.notifications.infrastructure;

import com.storyplatform.notifications.application
        .NotificationProviderException;
import com.storyplatform.notifications.application.port
        .NotificationDeliveryProvider;
import com.storyplatform.notifications.application.port
        .NotificationDeliveryRepository;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public final class HttpNotificationDeliveryProvider
        implements NotificationDeliveryProvider {

    private static final int MAXIMUM_RESPONSE_BYTES = 16 * 1024;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String bearerToken;
    private final Duration timeout;

    public HttpNotificationDeliveryProvider(
            HttpClient http,
            ObjectMapper mapper,
            URI endpoint,
            String bearerToken,
            Duration timeout
    ) {
        this.http = Objects.requireNonNull(http, "http");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.endpoint = secure(endpoint);
        if (bearerToken == null || bearerToken.length() < 16) {
            throw new IllegalArgumentException(
                    "notification provider token is invalid"
            );
        }
        this.bearerToken = bearerToken;
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("provider timeout is invalid");
        }
        this.timeout = timeout;
    }

    @Override
    public String send(
            NotificationDeliveryRepository.DeliveryJob job,
            NotificationDeliveryRepository.DeliveryTarget target,
            String unsubscribeToken
    ) {
        try {
            byte[] body = mapper.writeValueAsBytes(Map.of(
                    "deliveryId", job.id(),
                    "to", target.address(),
                    "credentials", target.credentials(),
                    "type", job.type(),
                    "title", job.title(),
                    "body", job.body(),
                    "data", job.data(),
                    "unsubscribeToken", unsubscribeToken
            ));
            HttpResponse<byte[]> response = http.send(
                    HttpRequest.newBuilder(endpoint)
                            .timeout(timeout)
                            .header("Accept", "application/json")
                            .header(
                                    "Authorization",
                                    "Bearer " + bearerToken
                            )
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            int status = response.statusCode();
            if (response.body().length > MAXIMUM_RESPONSE_BYTES) {
                throw failure("PROVIDER_RESPONSE_TOO_LARGE", false, null);
            }
            if (status < 200 || status >= 300) {
                boolean retryable = status == 408
                        || status == 429
                        || status >= 500;
                throw failure(
                        "PROVIDER_HTTP_" + status,
                        retryable,
                        null
                );
            }
            return response.headers()
                    .firstValue("X-Message-Id")
                    .filter(value -> value.matches(
                            "[A-Za-z0-9._:-]{1,128}"
                    ))
                    .orElse(job.id() + ":" + job.attempt());
        } catch (NotificationProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("PROVIDER_INTERRUPTED", true, exception);
        } catch (IOException exception) {
            throw failure("PROVIDER_IO_FAILURE", true, exception);
        }
    }

    private static URI secure(URI value) {
        if (value == null
                || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null
                || value.getUserInfo() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException(
                    "notification provider endpoint must be HTTPS"
            );
        }
        return value;
    }

    private static NotificationProviderException failure(
            String code,
            boolean retryable,
            Throwable cause
    ) {
        NotificationProviderException exception =
                new NotificationProviderException(
                        code,
                        "Notification provider request failed.",
                        retryable
                );
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }
}
