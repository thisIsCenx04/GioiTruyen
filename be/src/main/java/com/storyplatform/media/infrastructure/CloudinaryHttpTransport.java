package com.storyplatform.media.infrastructure;

import java.time.Duration;

/**
 * Minimal HTTP transport abstraction for Cloudinary API calls.
 * Decoupled from any specific HTTP client to allow unit testing via stubs.
 */
public interface CloudinaryHttpTransport {

    /**
     * Sends a GET request and returns the response.
     *
     * @param url                  absolute URL
     * @param accept               Accept header value
     * @param timeout              request timeout
     * @param maximumResponseBytes maximum body size in bytes
     * @return the response
     */
    Response get(
            String url,
            String accept,
            Duration timeout,
            int maximumResponseBytes
    );

    /**
     * Sends a multipart/form-data POST request and returns the response.
     *
     * @param url                  absolute URL
     * @param contentType          Content-Type header value
     * @param body                 raw request body
     * @param timeout              request timeout
     * @param maximumResponseBytes maximum body size in bytes
     * @return the response
     */
    Response post(
            String url,
            String contentType,
            byte[] body,
            Duration timeout,
            int maximumResponseBytes
    );

    /**
     * HTTP response value type.
     */
    record Response(int status, byte[] body) {
    }
}
