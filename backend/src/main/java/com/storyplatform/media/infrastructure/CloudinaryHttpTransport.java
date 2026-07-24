package com.storyplatform.media.infrastructure;

import java.io.IOException;
import java.time.Duration;

public interface CloudinaryHttpTransport {

    Response get(
            String url,
            String accept,
            Duration timeout,
            int maximumResponseBytes
    ) throws IOException, InterruptedException;

    Response post(
            String url,
            String contentType,
            byte[] body,
            Duration timeout,
            int maximumResponseBytes
    ) throws IOException, InterruptedException;

    record Response(int status, byte[] body) {
    }
}
