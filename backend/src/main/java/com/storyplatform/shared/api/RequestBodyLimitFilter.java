package com.storyplatform.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class RequestBodyLimitFilter extends OncePerRequestFilter {

    private final ApiProblemFactory problems;
    private final ObjectMapper objectMapper;

    public RequestBodyLimitFilter(
            ApiProblemFactory problems,
            ObjectMapper objectMapper
    ) {
        this.problems = problems;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isJson(request.getContentType())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getContentLengthLong() > ApiRequestLimits.MAX_REQUEST_BODY_BYTES) {
            writePayloadTooLarge(request, response);
            return;
        }

        byte[] body = request.getInputStream().readNBytes(
                ApiRequestLimits.MAX_REQUEST_BODY_BYTES + 1
        );
        if (body.length > ApiRequestLimits.MAX_REQUEST_BODY_BYTES) {
            writePayloadTooLarge(request, response);
            return;
        }

        filterChain.doFilter(new BufferedRequest(request, body), response);
    }

    private void writePayloadTooLarge(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                problems.create(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "PAYLOAD_TOO_LARGE",
                        "Nội dung yêu cầu quá lớn",
                        "Nội dung JSON vượt quá giới hạn cho phép.",
                        request
                )
        );
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }

        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith(MediaType.APPLICATION_JSON_VALUE)
                || normalized.startsWith("application/")
                && normalized.contains("+json");
    }

    private static final class BufferedRequest
            extends HttpServletRequestWrapper {

        private final byte[] body;

        private BufferedRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new BufferedServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(
                    getInputStream(),
                    StandardCharsets.UTF_8
            ));
        }
    }

    private static final class BufferedServletInputStream
            extends ServletInputStream {

        private final ByteArrayInputStream input;

        private BufferedServletInputStream(byte[] body) {
            this.input = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return input.read();
        }

        @Override
        public boolean isFinished() {
            return input.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            try {
                readListener.onDataAvailable();
                readListener.onAllDataRead();
            } catch (IOException exception) {
                readListener.onError(exception);
            }
        }
    }
}
