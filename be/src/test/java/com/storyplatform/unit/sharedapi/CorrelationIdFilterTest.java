package com.storyplatform.unit.sharedapi;

import com.storyplatform.shared.api.CorrelationId;
import com.storyplatform.shared.api.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void validClientRequestIdIsPropagatedAndScopedToTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER_NAME, "web:request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (filteredRequest, filteredResponse) -> {
            assertThat(filteredRequest.getAttribute(CorrelationId.REQUEST_ATTRIBUTE))
                    .isEqualTo("web:request-123");
            assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("web:request-123");
        });

        assertThat(response.getHeader(CorrelationId.HEADER_NAME))
                .isEqualTo("web:request-123");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void unsafeClientRequestIdIsReplacedInsteadOfReflected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER_NAME, "unsafe request id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        assertThat(response.getHeader(CorrelationId.HEADER_NAME))
                .matches("[0-9a-f-]{36}")
                .isNotEqualTo("unsafe request id");
    }

    @Test
    void missingClientRequestIdIsGenerated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });

        assertThat(response.getHeader(CorrelationId.HEADER_NAME))
                .matches("[0-9a-f-]{36}");
    }
}
