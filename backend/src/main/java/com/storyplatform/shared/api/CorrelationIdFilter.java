package com.storyplatform.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.function.Supplier;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter extends OncePerRequestFilter {

    private final Supplier<String> generator;

    public CorrelationIdFilter() {
        this(() -> java.util.UUID.randomUUID().toString());
    }

    CorrelationIdFilter(Supplier<String> generator) {
        this.generator = generator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = CorrelationId.resolve(
                request.getHeader(CorrelationId.HEADER_NAME),
                generator
        );
        request.setAttribute(CorrelationId.REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(CorrelationId.HEADER_NAME, correlationId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable(
                CorrelationId.MDC_KEY,
                correlationId
        )) {
            filterChain.doFilter(request, response);
        }
    }
}
