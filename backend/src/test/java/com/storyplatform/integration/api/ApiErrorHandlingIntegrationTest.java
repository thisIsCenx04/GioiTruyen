package com.storyplatform.integration.api;

import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.api.ApiExceptionHandler;
import com.storyplatform.shared.api.ApiProblemFactory;
import com.storyplatform.shared.api.CorrelationId;
import com.storyplatform.shared.api.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiErrorHandlingIntegrationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void configureApiBoundary() {
        ApiProblemFactory problems = new ApiProblemFactory();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ErrorProbeController())
                .setControllerAdvice(new ApiExceptionHandler(problems))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void domainFailureReturnsRfcProblemWithStableCodeAndClientCorrelation()
            throws Exception {
        mockMvc.perform(get("/probe/conflict")
                        .header(CorrelationId.HEADER_NAME, "reader-42"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(header().string(CorrelationId.HEADER_NAME, "reader-42"))
                .andExpect(jsonPath("$.type")
                        .value("urn:problem:story-platform:concurrency-conflict"))
                .andExpect(jsonPath("$.title").value("Dữ liệu đã thay đổi"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONCURRENCY_CONFLICT"))
                .andExpect(jsonPath("$.traceId").value("reader-42"));
    }

    @Test
    void frameworkFailureUsesTheSameProblemContract() throws Exception {
        mockMvc.perform(post("/probe/conflict")
                        .header(CorrelationId.HEADER_NAME, "reader-43"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"))
                .andExpect(jsonPath("$.traceId").value("reader-43"));
    }

    @Test
    void unexpectedFailureNeverLeaksExceptionOrSensitiveMessage()
            throws Exception {
        mockMvc.perform(get("/probe/unexpected")
                        .header(CorrelationId.HEADER_NAME, "reader-44"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.traceId").value("reader-44"))
                .andExpect(content().string(not(containsString("database-password"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @RestController
    @RequestMapping("/probe")
    static final class ErrorProbeController {

        @GetMapping("/conflict")
        void conflict() {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CONCURRENCY_CONFLICT",
                    "Dữ liệu đã thay đổi",
                    "Hãy tải phiên bản mới nhất."
            );
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("database-password must stay private");
        }
    }
}
