package com.storyplatform.unit.community.api;

import com.storyplatform.community.api.StoryRelationController;
import com.storyplatform.community.application.StoryRelationOperations;
import com.storyplatform.community.domain.StoryRelation;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StoryRelationControllerTest {

    private static final String STORY =
            "20000000-0000-4000-8000-000000000001";

    @Test
    void routesFavoriteAndFollowMutationsToAuthenticatedActor() {
        var operations = mock(StoryRelationOperations.class);
        var controller = new StoryRelationController(operations);
        Jwt jwt = jwt();

        controller.status(jwt, STORY, "favorite");
        controller.add(jwt, STORY, "follow");
        controller.remove(jwt, STORY, "favorite");

        verify(operations).status(
                "user", STORY, StoryRelation.Type.FAVORITE
        );
        verify(operations).add(
                "user", STORY, StoryRelation.Type.FOLLOW
        );
        verify(operations).remove(
                "user", STORY, StoryRelation.Type.FAVORITE
        );
    }

    @Test
    void rejectsInvalidStoryAndRelation() {
        var controller = new StoryRelationController(
                mock(StoryRelationOperations.class)
        );
        assertThatThrownBy(() -> controller.add(
                jwt(), "bad", "favorite"
        )).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.add(
                jwt(), STORY, "unknown"
        )).isInstanceOf(ApiException.class);
    }

    private static Jwt jwt() {
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
