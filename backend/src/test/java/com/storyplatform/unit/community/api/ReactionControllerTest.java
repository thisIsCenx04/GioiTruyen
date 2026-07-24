package com.storyplatform.unit.community.api;

import com.storyplatform.community.api.ReactionController;
import com.storyplatform.community.application.ReactionOperations;
import com.storyplatform.community.domain.Reaction;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReactionControllerTest {

    private static final String TARGET =
            "30000000-0000-4000-8000-000000000001";

    @Test
    void routesIdempotentLifecycleToAuthenticatedActor() {
        ReactionOperations operations = mock(ReactionOperations.class);
        ReactionController controller = new ReactionController(operations);

        controller.status(jwt(), "comment", TARGET);
        controller.add(jwt(), "story", TARGET);
        controller.remove(jwt(), "chapter", TARGET);

        verify(operations).status(
                "actor", Reaction.TargetType.COMMENT, TARGET
        );
        verify(operations).add(
                "actor", Reaction.TargetType.STORY, TARGET
        );
        verify(operations).remove(
                "actor", Reaction.TargetType.CHAPTER, TARGET
        );
    }

    @Test
    void rejectsInvalidTargetTypeAndIdentifier() {
        ReactionController controller = new ReactionController(
                mock(ReactionOperations.class)
        );
        assertThatThrownBy(() -> controller.add(
                jwt(), "unknown", TARGET
        )).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.add(
                jwt(), "comment", "bad"
        )).isInstanceOf(ApiException.class);
    }

    private static Jwt jwt() {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("actor")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
