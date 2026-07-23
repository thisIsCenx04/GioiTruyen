package com.storyplatform.unit.identity.api;

import com.storyplatform.identity.api.RefreshSessionController;
import com.storyplatform.identity.application.IdentityService;
import com.storyplatform.identity.application.RefreshSessionOutcome;
import com.storyplatform.shared.api.ApiExceptionHandler;
import com.storyplatform.shared.api.ApiProblemFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers
        .status;

class RefreshSessionControllerTest {

    private static final String TOKEN = "A".repeat(43);
    private IdentityService identityService;
    private MockMvc mockMvc;

    @BeforeEach
    void configure() {
        identityService = mock(IdentityService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RefreshSessionController(identityService)
                )
                .setControllerAdvice(new ApiExceptionHandler(
                        new ApiProblemFactory()
                ))
                .build();
    }

    @Test
    void returnsRotatedAccessAndRefreshPair() throws Exception {
        when(identityService.refreshSession(anyString())).thenReturn(
                RefreshSessionOutcome.rotated(
                        "new.access.token",
                        600,
                        "B".repeat(43)
                )
        );

        mockMvc.perform(request(TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("new.access.token"))
                .andExpect(jsonPath("$.refreshToken")
                        .value("B".repeat(43)));
    }

    @Test
    void invalidAndReplayedTokensUseSamePublicFailure()
            throws Exception {
        when(identityService.refreshSession(anyString()))
                .thenReturn(RefreshSessionOutcome.invalid())
                .thenReturn(
                        RefreshSessionOutcome.reuseDetected()
                );

        mockMvc.perform(request(TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REFRESH_TOKEN_INVALID"));
        mockMvc.perform(request(TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void malformedTokenIsRejectedBeforeUseCase() throws Exception {
        mockMvc.perform(request("short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"));
    }

    private static org.springframework.test.web.servlet
            .request.MockHttpServletRequestBuilder request(String token) {
        return post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}");
    }
}
