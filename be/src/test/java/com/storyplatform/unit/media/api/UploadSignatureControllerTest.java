package com.storyplatform.unit.media.api;

import com.storyplatform.media.api.UploadSignatureController;
import com.storyplatform.media.api.UploadSignatureRequest;
import com.storyplatform.media.application.MediaRequestException;
import com.storyplatform.media.application.UploadSignatureOperations;
import com.storyplatform.media.domain.MediaOwnerType;
import com.storyplatform.media.domain.UploadPurpose;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadSignatureControllerTest {

    private static final String USER_ID =
            "00000000-0000-4000-8000-000000000001";
    private UploadSignatureOperations signatures;
    private UploadSignatureController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        signatures = mock(UploadSignatureOperations.class);
        controller = new UploadSignatureController(signatures);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(USER_ID)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
    }

    @Test
    void delegatesAuthenticatedSubjectAndDisablesCaching() {
        UploadSignatureRequest request = request();
        UploadSignatureOperations.UploadGrant grant =
                new UploadSignatureOperations.UploadGrant(
                        USER_ID,
                        "https://api.cloudinary.com/v1_1/demo"
                                + "/image/authenticated",
                        "key",
                        "a".repeat(64),
                        Instant.parse("2026-07-24T00:10:00Z"),
                        1024,
                        Set.of("image/png"),
                        Map.of("timestamp", "1784851200")
                );
        when(signatures.issue(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                any()
        )).thenReturn(grant);

        var response = controller.issue(jwt, request);

        assertThat(response.getBody()).isSameAs(grant);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
        verify(signatures).issue(
                USER_ID,
                new UploadSignatureOperations.UploadCommand(
                        request.ownerType(),
                        request.ownerId(),
                        request.purpose(),
                        request.contentType(),
                        request.sizeBytes(),
                        request.sha256()
                )
        );
    }

    @Test
    void mapsOwnershipFailureToForbiddenProblem() {
        when(signatures.issue(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                any()
        )).thenThrow(new MediaRequestException(
                "MEDIA_OWNER_FORBIDDEN",
                "denied",
                true
        ));

        assertThatThrownBy(() -> controller.issue(jwt, request()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.code())
                            .isEqualTo("MEDIA_OWNER_FORBIDDEN");
                    assertThat(exception.status().value()).isEqualTo(403);
                });
    }

    private static UploadSignatureRequest request() {
        return new UploadSignatureRequest(
                MediaOwnerType.USER,
                USER_ID,
                UploadPurpose.AVATAR,
                "image/png",
                1024,
                "a".repeat(64)
        );
    }
}
