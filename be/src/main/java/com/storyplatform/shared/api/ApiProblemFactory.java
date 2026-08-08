package com.storyplatform.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public final class ApiProblemFactory {

    private static final String TYPE_PREFIX = "urn:problem:story-platform:";

    public ProblemDetail create(
            HttpStatusCode status,
            String code,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + slug(code)));
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("traceId", CorrelationId.from(request));
        return problem;
    }

    private static String slug(String code) {
        return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
