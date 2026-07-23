package com.storyplatform.unit.sharedapi;

import com.storyplatform.shared.api.ApiProblemFactory;
import com.storyplatform.shared.api.ApiRequestLimits;
import com.storyplatform.shared.api.RequestBodyLimitFilter;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RequestBodyLimitFilterTest {

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
            new ApiProblemFactory(),
            JsonMapper.builder()
                    .addMixIn(
                            ProblemDetail.class,
                            ProblemDetailJacksonMixin.class
                    )
                    .build()
    );

    @Test
    void nonJsonRequestPassesThroughWithoutBuffering() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter.doFilter(request, response, (filteredRequest, ignoredResponse) -> {
            invoked.set(true);
            assertThat(filteredRequest).isSameAs(request);
        });

        assertThat(invoked).isTrue();
    }

    @Test
    void declaredOversizedJsonIsRejectedBeforeReading() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return ApiRequestLimits.MAX_REQUEST_BODY_BYTES + 1L;
            }
        };
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("oversized request reached the chain");
        });

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
    }

    @Test
    void chunkedOversizedJsonIsRejectedAfterBoundedRead() throws Exception {
        byte[] content = new byte[ApiRequestLimits.MAX_REQUEST_BODY_BYTES + 1];
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(content);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("oversized request reached the chain");
        });

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void boundedVendorJsonIsReplayableForMessageConversion() throws Exception {
        byte[] content = "{\"title\":\"Hợp lệ\"}".getBytes(
                StandardCharsets.UTF_8
        );
        MockHttpServletRequest request = jsonRequest(content);
        request.setContentType("application/vnd.gioitruyen+json");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (filteredRequest, ignoredResponse) -> {
            HttpServletRequest buffered = (HttpServletRequest) filteredRequest;
            assertThat(buffered.getContentLength()).isEqualTo(content.length);
            assertThat(buffered.getContentLengthLong()).isEqualTo(content.length);
            assertThat(buffered.getReader().readLine())
                    .isEqualTo("{\"title\":\"Hợp lệ\"}");

            ServletInputStream input = buffered.getInputStream();
            assertThat(input.isReady()).isTrue();
            assertThat(input.isFinished()).isFalse();
            assertThat(input.readAllBytes()).isEqualTo(content);
            assertThat(input.isFinished()).isTrue();

            AtomicBoolean completed = new AtomicBoolean();
            buffered.getInputStream().setReadListener(new ReadListener() {
                @Override
                public void onDataAvailable() {
                }

                @Override
                public void onAllDataRead() {
                    completed.set(true);
                }

                @Override
                public void onError(Throwable throwable) {
                    throw new AssertionError(throwable);
                }
            });
            assertThat(completed).isTrue();
        });
    }

    private static MockHttpServletRequest jsonRequest(byte[] content) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(content);
        return request;
    }
}
