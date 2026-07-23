package com.storyplatform.unit.identity.api;

import com.storyplatform.identity.api.LoginController;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.LoginRiskUnavailableException;
import com.storyplatform.identity.application.LoginOutcome;
import com.storyplatform.shared.api.ApiExceptionHandler;
import com.storyplatform.shared.api.ApiProblemFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .status;

class LoginControllerTest {

    private IdentityService identityService;
    private MockMvc mockMvc;

    @BeforeEach
    void configure() {
        identityService = mock(IdentityService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LoginController(identityService))
                .setControllerAdvice(new ApiExceptionHandler(
                        new ApiProblemFactory()
                ))
                .build();
    }

    @Test
    void returnsBearerAccessTokenAfterAuthentication() throws Exception {
        when(identityService.login(any())).thenReturn(
                LoginOutcome.authenticated("signed.jwt.token", 600)
        );

        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600));
    }

    @Test
    void invalidCredentialsUseGenericUnauthorizedProblem()
            throws Exception {
        when(identityService.login(any())).thenReturn(
                LoginOutcome.invalidCredentials()
        );

        mockMvc.perform(validRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_FAILED"))
                .andExpect(content().string(not(
                        containsString("reader@example.com")
                )));
    }

    @Test
    void riskLimitAndOutageFailClosed() throws Exception {
        when(identityService.login(any()))
                .thenReturn(LoginOutcome.rateLimited(900));
        mockMvc.perform(validRequest())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(900))
                .andExpect(jsonPath("$.code")
                        .value("LOGIN_RATE_LIMITED"));

        when(identityService.login(any())).thenThrow(
                new LoginRiskUnavailableException(
                        new IllegalStateException("redis down")
                )
        );
        mockMvc.perform(validRequest())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("LOGIN_RISK_UNAVAILABLE"));
    }

    @Test
    void malformedRequestIsRejectedBeforeUseCase() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"));
    }

    private static org.springframework.test.web.servlet
            .request.MockHttpServletRequestBuilder validRequest() {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "reader@example.com",
                          "password": "correct horse battery staple"
                        }
                        """);
    }
}
