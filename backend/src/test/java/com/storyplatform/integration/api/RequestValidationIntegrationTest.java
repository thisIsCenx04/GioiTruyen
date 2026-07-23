package com.storyplatform.integration.api;

import com.storyplatform.shared.api.ApiExceptionHandler;
import com.storyplatform.shared.api.ApiProblemFactory;
import com.storyplatform.shared.api.ApiRequestLimits;
import com.storyplatform.shared.api.CorrelationId;
import com.storyplatform.shared.api.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestValidationIntegrationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void configureStrictApiBoundary() {
        JsonFactory jsonFactory = JsonFactory.builder()
                .streamReadConstraints(ApiRequestLimits.jsonConstraints())
                .build();
        JsonMapper mapper = JsonMapper.builder(jsonFactory)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ValidationProbeController())
                .setControllerAdvice(new ApiExceptionHandler(
                        new ApiProblemFactory()
                ))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void beanValidationReturnsStableFieldErrors() throws Exception {
        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationId.HEADER_NAME, "validation-42")
                        .content("""
                                {
                                  "title": " ",
                                  "limit": 101
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").value("validation-42"))
                .andExpect(jsonPath("$.errors.title[0]")
                        .value("TITLE_REQUIRED"))
                .andExpect(jsonPath("$.errors.limit[0]")
                        .value("LIMIT_TOO_LARGE"));
    }

    @Test
    void unknownJsonFieldIsRejectedInsteadOfSilentlyIgnored() throws Exception {
        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Hợp lệ",
                                  "limit": 20,
                                  "isAdmin": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void deeplyNestedJsonIsRejectedAtTheParserBoundary() throws Exception {
        String nestedValue = "[".repeat(ApiRequestLimits.MAX_JSON_DEPTH + 1)
                + "0"
                + "]".repeat(ApiRequestLimits.MAX_JSON_DEPTH + 1);
        String body = """
                {"title":"Hợp lệ","limit":20,"metadata":%s}
                """.formatted(nestedValue);

        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @ParameterizedTest(name = "invalid JSON sample {index} is rejected")
    @ValueSource(strings = {
            "",
            "{",
            "[]",
            "{\"title\":",
            "{\"title\":\"ok\",\"limit\":1} trailing",
            "{\"title\":\"ok\",\"limit\":1,,}"
    })
    void malformedJsonSamplesAreRejectedDeterministically(String body)
            throws Exception {
        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void validRequestReachesTheController() throws Exception {
        mockMvc.perform(post("/probe/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hợp lệ","limit":20}
                                """))
                .andExpect(status().isNoContent());
    }

    @RestController
    @RequestMapping("/probe")
    static final class ValidationProbeController {

        @PostMapping("/validation")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void validate(@Valid @RequestBody ValidationProbeRequest request) {
        }
    }

    record ValidationProbeRequest(
            @NotBlank(message = "TITLE_REQUIRED")
            @Size(max = 80, message = "TITLE_TOO_LONG")
            String title,
            @NotNull(message = "LIMIT_REQUIRED")
            @Min(value = 1, message = "LIMIT_TOO_SMALL")
            @Max(value = 100, message = "LIMIT_TOO_LARGE")
            Integer limit,
            JsonNode metadata
    ) {
    }
}
