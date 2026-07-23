package com.storyplatform.unit.identity.api;

import com.storyplatform.identity.api.EmailVerificationController;
import com.storyplatform.identity.application.EmailVerificationOutcome;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.shared.api.ApiExceptionHandler;
import com.storyplatform.shared.api.ApiProblemFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .status;

class EmailVerificationControllerTest {

    private IdentityService identityService;
    private MockMvc mockMvc;

    @BeforeEach
    void configure() {
        identityService = mock(IdentityService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new EmailVerificationController(identityService)
                )
                .setControllerAdvice(new ApiExceptionHandler(
                        new ApiProblemFactory()
                ))
                .build();
    }

    @Test
    void returnsNoContentForSuccessfulVerification() throws Exception {
        when(identityService.verifyEmail("valid.token"))
                .thenReturn(EmailVerificationOutcome.VERIFIED);

        mockMvc.perform(request("valid.token"))
                .andExpect(status().isNoContent());
    }

    @Test
    void returnsSameSafeProblemForUnusableToken() throws Exception {
        when(identityService.verifyEmail("unknown.token"))
                .thenReturn(
                        EmailVerificationOutcome.INVALID_OR_EXPIRED
                );

        mockMvc.perform(request("unknown.token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VERIFICATION_TOKEN_INVALID"));
    }

    @Test
    void rejectsMalformedTokenAtBoundary() throws Exception {
        mockMvc.perform(request("spaces are forbidden"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"));
    }

    private static org.springframework.test.web.servlet
            .request.MockHttpServletRequestBuilder request(String token) {
        return post("/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}");
    }
}
