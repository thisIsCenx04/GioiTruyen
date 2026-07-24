package com.storyplatform.integration.api;

import com.storyplatform.shared.api.CorrelationId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityProblemHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousDenialUsesProblemJsonAndPreservesCorrelationId()
            throws Exception {
        mockMvc.perform(get("/private-resource")
                        .header(CorrelationId.HEADER_NAME, "security-42"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(header().string(CorrelationId.HEADER_NAME, "security-42"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").value("security-42"));
    }

    @Test
    @WithMockUser
    void authenticatedDenialUsesTheForbiddenProblemContract() throws Exception {
        mockMvc.perform(get("/private-resource")
                        .header(CorrelationId.HEADER_NAME, "security-43"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.traceId").value("security-43"));
    }

    @Test
    void oversizedJsonIsRejectedBeforeAuthenticationOrControllerDispatch()
            throws Exception {
        String body = "\"" + "x".repeat(
                com.storyplatform.shared.api.ApiRequestLimits
                        .MAX_REQUEST_BODY_BYTES
        ) + "\"";

        mockMvc.perform(post("/private-resource")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationId.HEADER_NAME, "security-44")
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.traceId").value("security-44"));
    }

    @Test
    void anonymousRegistrationReachesValidationWithoutCsrfToken()
            throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(
                                CorrelationId.HEADER_NAME,
                                "security-registration"
                        )
                        .content("""
                                {
                                  "email": "reader@example.com",
                                  "password": "correct horse battery staple",
                                  "acceptedTerms": false,
                                  "consentVersion": "2026-07-24"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId")
                        .value("security-registration"));
    }

    @Test
    void anonymousCommentMutationRequiresAuthenticationNotCsrf()
            throws Exception {
        mockMvc.perform(post("/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(
                                CorrelationId.HEADER_NAME,
                                "security-comment"
                        )
                        .content("""
                                {
                                  "targetType": "story",
                                  "targetId": "20000000-0000-4000-8000-000000000001",
                                  "body": "Nội dung"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId")
                        .value("security-comment"));
    }
}
