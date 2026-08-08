package com.storyplatform.unit.identity.api;

import com.storyplatform.identity.api.RegistrationController;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.RegistrationOutcome;
import com.storyplatform.shared.api.ApiExceptionHandler;
import com.storyplatform.shared.api.ApiProblemFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationControllerTest {

    private IdentityService useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void configureController() {
        useCase = mock(IdentityService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RegistrationController(useCase))
                .setControllerAdvice(new ApiExceptionHandler(
                        new ApiProblemFactory()
                ))
                .build();
    }

    @Test
    void acceptedResponseDoesNotExposeAccountOrEmailExistence()
            throws Exception {
        when(useCase.register(any()))
                .thenReturn(RegistrationOutcome.ACCEPTED);

        mockMvc.perform(validRequest())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status")
                        .value("REGISTRATION_ACCEPTED"))
                .andExpect(content().string(not(
                        containsString("reader@example.com")
                )))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @ParameterizedTest
    @EnumSource(
            value = RegistrationOutcome.class,
            names = {
                    "INVALID_EMAIL",
                    "WEAK_PASSWORD",
                    "CONSENT_VERSION_REJECTED"
            }
    )
    void policyRejectionUsesSafeProblemDetails(
            RegistrationOutcome outcome
    ) throws Exception {
        when(useCase.register(any())).thenReturn(outcome);

        mockMvc.perform(validRequest())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(content().string(not(
                        containsString("reader@example.com")
                )));
    }

    @Test
    void missingConsentIsRejectedAtApiBoundary() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "reader@example.com",
                                  "password": "correct horse battery staple",
                                  "acceptedTerms": false,
                                  "consentVersion": "2026-07-24"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.acceptedTerms[0]")
                        .value("CONSENT_REQUIRED"));
    }

    private static org.springframework.test.web.servlet
            .request.MockHttpServletRequestBuilder validRequest() {
        return post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "reader@example.com",
                          "password": "correct horse battery staple",
                          "acceptedTerms": true,
                          "consentVersion": "2026-07-24"
                        }
                        """);
    }
}
