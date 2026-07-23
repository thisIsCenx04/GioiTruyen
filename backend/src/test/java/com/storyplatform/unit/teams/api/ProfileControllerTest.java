package com.storyplatform.unit.teams.api;

import com.storyplatform.shared.api.ApiException;
import com.storyplatform.teams.api.ProfileController;
import com.storyplatform.teams.api.UpdateProfileRequest;
import com.storyplatform.teams.application.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProfileControllerTest {

    private static final String USER_ID =
            "73457d55-9602-4bcd-bbf0-e38b99c6c56e";
    private ProfileService service;
    private ProfileController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        service = mock(ProfileService.class);
        controller = new ProfileController(service);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(USER_ID)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
    }

    @Test
    void routesPrivateReadAndOptimisticUpdateToActor() {
        controller.me(jwt);
        controller.update(jwt, new UpdateProfileRequest(
                "Lam Dạ",
                "Tác giả",
                null,
                4L
        ));

        verify(service).privateProfile(USER_ID);
        verify(service).update(
                USER_ID,
                4,
                "Lam Dạ",
                "Tác giả",
                null
        );
    }

    @Test
    void validatesPublicIdentifierBeforeLookup() {
        controller.publicProfile(USER_ID);
        verify(service).publicProfile(USER_ID);

        assertThatThrownBy(() -> controller.publicProfile("invalid"))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(
                                exception.code()
                        ).isEqualTo("USER_ID_INVALID"));
    }
}
