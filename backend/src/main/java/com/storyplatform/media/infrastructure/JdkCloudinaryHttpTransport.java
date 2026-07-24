package com.storyplatform.media.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class JdkCloudinaryHttpTransport
        implements CloudinaryHttpTransport {

    private final HttpClient http;

    public JdkCloudinaryHttpTransport(HttpClient http) {
        this.http = Objects.requireNonNull(http, "http");
    }

    @Override
    public Response get(
            String url,
            String accept,
            Duration timeout,
            int maximumResponseBytes
    ) throws IOException, InterruptedException {
        return send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(timeout)
                        .header("Accept", accept)
                        .GET()
                        .build(),
                maximumResponseBytes
        );
    }

    @Override
    public Response post(
            String url,
            String contentType,
            byte[] body,
            Duration timeout,
            int maximumResponseBytes
    ) throws IOException, InterruptedException {
        return send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(timeout)
                        .header("Content-Type", contentType)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                maximumResponseBytes
        );
    }

    private Response send(
            HttpRequest request,
            int maximumResponseBytes
    ) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = http.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );
        try (InputStream input = response.body()) {
            return new Response(
                    response.statusCode(),
                    input.readNBytes(maximumResponseBytes + 1)
            );
        }
    }
}
