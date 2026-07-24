package com.storyplatform.unit.notifications.infrastructure;

import com.storyplatform.notifications.application
        .NotificationProviderException;
import com.storyplatform.notifications.application.port
        .NotificationDeliveryRepository;
import com.storyplatform.notifications.infrastructure
        .HttpNotificationDeliveryProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpNotificationDeliveryProviderTest {

    private final HttpClient http = mock(HttpClient.class);
    private final HttpResponse<byte[]> response = mock(HttpResponse.class);

    @Test
    void sendsBoundJsonAndUsesSafeProviderMessageId() throws Exception {
        when(response.statusCode()).thenReturn(202);
        when(response.body()).thenReturn("{}".getBytes());
        when(response.headers()).thenReturn(
                java.net.http.HttpHeaders.of(
                        Map.of("X-Message-Id", java.util.List.of("provider-1")),
                        (left, right) -> true
                )
        );
        when(http.send(
                any(HttpRequest.class),
                org.mockito.Mockito
                        .<HttpResponse.BodyHandler<byte[]>>any()
        )).thenReturn(response);

        assertThat(provider().send(job(), target(), "unsubscribe"))
                .isEqualTo("provider-1");

        when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
                Map.of("X-Message-Id", java.util.List.of("bad value")),
                (left, right) -> true
        ));
        assertThat(provider().send(job(), target(), "unsubscribe"))
                .isEqualTo("delivery:1");
    }

    @Test
    void classifiesHttpAndOversizedFailures() throws Exception {
        when(response.body()).thenReturn(new byte[0]);
        when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
                Map.of(), (left, right) -> true
        ));
        when(http.send(
                any(HttpRequest.class),
                org.mockito.Mockito
                        .<HttpResponse.BodyHandler<byte[]>>any()
        )).thenReturn(response);
        for (int status : new int[]{400, 408, 429, 503}) {
            when(response.statusCode()).thenReturn(status);
            assertThatThrownBy(() ->
                    provider().send(job(), target(), "token"))
                    .isInstanceOfSatisfying(
                            NotificationProviderException.class,
                            failure -> assertThat(failure.retryable())
                                    .isEqualTo(status != 400)
                    );
        }
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(new byte[16 * 1024 + 1]);
        assertThatThrownBy(() -> provider().send(job(), target(), "token"))
                .isInstanceOfSatisfying(
                        NotificationProviderException.class,
                        failure -> assertThat(failure.retryable()).isFalse()
                );
    }

    @Test
    void mapsIoFailureAndValidatesConfiguration() throws Exception {
        when(http.send(
                any(HttpRequest.class),
                org.mockito.Mockito
                        .<HttpResponse.BodyHandler<byte[]>>any()
        )).thenThrow(new IOException("offline"));
        assertThatThrownBy(() -> provider().send(job(), target(), "token"))
                .isInstanceOfSatisfying(
                        NotificationProviderException.class,
                        failure -> assertThat(failure.retryable()).isTrue()
                );

        assertThatThrownBy(() -> new HttpNotificationDeliveryProvider(
                http,
                new ObjectMapper(),
                URI.create("http://provider.example/send"),
                "a".repeat(16),
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpNotificationDeliveryProvider(
                http,
                new ObjectMapper(),
                URI.create("https://provider.example/send"),
                "short",
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpNotificationDeliveryProvider(
                http,
                new ObjectMapper(),
                URI.create("https://provider.example/send"),
                "a".repeat(16),
                Duration.ZERO
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private HttpNotificationDeliveryProvider provider() {
        return new HttpNotificationDeliveryProvider(
                http,
                new ObjectMapper(),
                URI.create("https://provider.example/send"),
                "provider-secret-token",
                Duration.ofSeconds(2)
        );
    }

    private static NotificationDeliveryRepository.DeliveryJob job() {
        return new NotificationDeliveryRepository.DeliveryJob(
                "delivery",
                "notification",
                "10000000-0000-4000-8000-000000000001",
                NotificationDeliveryRepository.Channel.EMAIL,
                "STORY_UPDATES",
                "STORY_PUBLISHED",
                "Title",
                "Body",
                Map.of("storyId", "story"),
                1
        );
    }

    private static NotificationDeliveryRepository.DeliveryTarget target() {
        return new NotificationDeliveryRepository.DeliveryTarget(
                "reader@example.com"
        );
    }
}
