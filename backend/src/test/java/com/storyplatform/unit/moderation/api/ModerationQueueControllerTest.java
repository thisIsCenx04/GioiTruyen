package com.storyplatform.unit.moderation.api;

import com.storyplatform.moderation.api.ModerationQueueController;
import com.storyplatform.moderation.application.ModerationQueueException;
import com.storyplatform.moderation.application.ModerationQueueOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.RoleCapabilityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationQueueControllerTest {

    private static final String REVIEW =
            "80000000-0000-4000-8000-000000000001";
    private static final String MODERATOR =
            "10000000-0000-4000-8000-000000000001";
    private ModerationQueueOperations operations;
    private ModerationQueueController controller;

    @BeforeEach
    void setUp() {
        operations = mock(ModerationQueueOperations.class);
        controller = new ModerationQueueController(
                operations,
                new JwtPrivilegeEvaluator(new RoleCapabilityPolicy())
        );
    }

    @Test
    void listsQueueForModeratorWithNoStore() {
        var page = new ModerationQueueOperations.ReviewPage(
                List.of(review()),
                null
        );
        when(operations.list(20, null)).thenReturn(page);

        var response = controller.list(
                jwt("MODERATOR"),
                "PUBLISHING",
                "OPEN",
                20,
                null
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(response.getBody()).isEqualTo(page);
    }

    @Test
    void claimsWithOptimisticVersionAndReturnsNextEtag() {
        when(operations.claim(MODERATOR, REVIEW, 2))
                .thenReturn(review());

        var response = controller.claim(
                jwt("MODERATOR"),
                REVIEW,
                "\"2\""
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"3\"");
        verify(operations).claim(MODERATOR, REVIEW, 2);
    }

    @Test
    void rejectsReaderInvalidFilterAndMalformedVersion() {
        assertThatThrownBy(() -> controller.list(
                jwt("USER"), "PUBLISHING", "OPEN", 20, null
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(403)
        );
        assertThatThrownBy(() -> controller.list(
                jwt("MODERATOR"), "OTHER", "OPEN", 20, null
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(400)
        );
        assertThatThrownBy(() -> controller.claim(
                jwt("MODERATOR"), REVIEW, "2"
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(400)
        );
    }

    @Test
    void mapsClaimRaceToConflict() {
        when(operations.claim(any(), any(), anyLong()))
                .thenThrow(new ModerationQueueException(
                        "REVIEW_CLAIM_CONFLICT",
                        "race",
                        ModerationQueueException.Kind.CONFLICT
                ));

        assertThatThrownBy(() -> controller.claim(
                jwt("ADMIN"), REVIEW, "\"2\""
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(409)
        );
    }

    private static Jwt jwt(String role) {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(MODERATOR)
                .claim("roles", List.of(role))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

    private static ModerationQueueOperations.ReviewCase review() {
        return new ModerationQueueOperations.ReviewCase(
                REVIEW,
                "STORY",
                "40000000-0000-4000-8000-000000000001",
                "20000000-0000-4000-8000-000000000001",
                "CLAIMED",
                90,
                false,
                List.of(),
                MODERATOR,
                Instant.parse("2026-07-24T00:15:00Z"),
                Instant.parse("2026-07-24T00:00:00Z"),
                3
        );
    }
}
