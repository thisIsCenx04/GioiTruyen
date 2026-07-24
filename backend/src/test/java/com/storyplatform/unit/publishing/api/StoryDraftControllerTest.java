package com.storyplatform.unit.publishing.api;

import com.storyplatform.publishing.domain.StoryDraft;
import com.storyplatform.publishing.api.CreateStoryDraftRequest;
import com.storyplatform.publishing.api.StoryDraftController;
import com.storyplatform.publishing.application.StoryDraftException;
import com.storyplatform.publishing.application.StoryDraftOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryDraftControllerTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String CATEGORY =
            "30000000-0000-4000-8000-000000000001";

    private StoryDraftOperations operations;
    private StoryDraftController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        operations = mock(StoryDraftOperations.class);
        controller = new StoryDraftController(operations);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(ACTOR)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

    @Test
    void returnsCreatedNoStoreLocationAndVersionEtag() {
        var request = request();
        var draft = draft();
        when(operations.create(
                eq(ACTOR),
                eq(TEAM),
                eq("draft-request-001"),
                any()
        )).thenReturn(draft);

        var response = controller.create(
                jwt,
                TEAM,
                "draft-request-001",
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"1\"");
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(response.getHeaders().getLocation().toString())
                .endsWith("/stories/" + draft.id());
        verify(operations).create(
                ACTOR,
                TEAM,
                "draft-request-001",
                new StoryDraftOperations.CreateCommand(
                        request.title(),
                        request.synopsis(),
                        request.origin(),
                        request.language(),
                        request.categoryIds(),
                        request.coverAssetId()
                )
        );
    }

    @Test
    void mapsForbiddenAndConflictWithoutLeakingResources() {
        when(operations.create(any(), any(), any(), any()))
                .thenThrow(new StoryDraftException(
                        "STORY_CREATE_FORBIDDEN",
                        "denied",
                        StoryDraftException.Kind.FORBIDDEN
                ));

        assertThatThrownBy(() -> controller.create(
                jwt,
                TEAM,
                "draft-request-001",
                request()
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.status().value()).isEqualTo(403);
            assertThat(exception.code())
                    .isEqualTo("STORY_CREATE_FORBIDDEN");
        });
    }

    private static CreateStoryDraftRequest request() {
        return new CreateStoryDraftRequest(
                "Truyện Một",
                "Tóm tắt",
                StoryDraft.Origin.ORIGINAL,
                "vi",
                List.of(CATEGORY),
                null
        );
    }

    private static StoryDraftOperations.DraftView draft() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new StoryDraftOperations.DraftView(
                "40000000-0000-4000-8000-000000000001",
                TEAM,
                "truyen-mot-40000000",
                "Truyện Một",
                "Tóm tắt",
                StoryDraft.Origin.ORIGINAL,
                "vi",
                List.of(CATEGORY),
                null,
                StoryDraft.CompletionStatus.ONGOING,
                StoryDraft.WorkflowStatus.DRAFT,
                "50000000-0000-4000-8000-000000000001",
                1,
                1,
                now,
                now
        );
    }
}
