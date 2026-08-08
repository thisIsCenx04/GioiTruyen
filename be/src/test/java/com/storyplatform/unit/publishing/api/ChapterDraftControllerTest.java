package com.storyplatform.unit.publishing.api;

import com.storyplatform.publishing.api.ChapterDraftController;
import com.storyplatform.publishing.api.CreateChapterDraftRequest;
import com.storyplatform.publishing.api.UpdateChapterDraftRequest;
import com.storyplatform.publishing.application.ChapterDraftException;
import com.storyplatform.publishing.application.ChapterDraftOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChapterDraftControllerTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "40000000-0000-4000-8000-000000000001";
    private ChapterDraftOperations operations;
    private ChapterDraftController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        operations = mock(ChapterDraftOperations.class);
        controller = new ChapterDraftController(operations);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(ACTOR)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

    @Test
    void returnsCreatedLocationNoStoreAndEtag() {
        var request = new CreateChapterDraftRequest(
                1, "Mở đầu", "<p>Hello</p>"
        );
        var view = view();
        when(operations.create(any(), any(), any(), any()))
                .thenReturn(view);

        var response = controller.create(jwt, TEAM, STORY, request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"1\"");
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(response.getHeaders().getLocation().toString())
                .endsWith("/chapters/" + view.id());
        verify(operations).create(
                ACTOR,
                TEAM,
                STORY,
                new ChapterDraftOperations.CreateCommand(
                        1, "Mở đầu", "<p>Hello</p>"
                )
        );
    }

    @Test
    void mapsDomainConflictAndRejectsInvalidIdentifiers() {
        when(operations.create(any(), any(), any(), any()))
                .thenThrow(new ChapterDraftException(
                        "CHAPTER_NUMBER_EXISTS",
                        "duplicate",
                        ChapterDraftException.Kind.CONFLICT
                ));
        assertThatThrownBy(() -> controller.create(
                jwt,
                TEAM,
                STORY,
                new CreateChapterDraftRequest(1, "Title", "<p>x</p>")
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(409)
        );
        assertThatThrownBy(() -> controller.create(
                jwt,
                "invalid",
                STORY,
                new CreateChapterDraftRequest(1, "Title", "<p>x</p>")
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(400)
        );
    }

    @Test
    void mapsInvalidForbiddenAndMissingDomainFailures() {
        assertMapped(ChapterDraftException.Kind.INVALID, 400);
        assertMapped(ChapterDraftException.Kind.FORBIDDEN, 403);
        assertMapped(ChapterDraftException.Kind.NOT_FOUND, 404);
        assertMapped(ChapterDraftException.Kind.PRECONDITION, 412);
    }

    @Test
    void conditionallyUpdatesAChapterAndReturnsTheNewEtag() {
        var updated = new ChapterDraftOperations.ChapterView(
                view().id(),
                STORY,
                TEAM,
                1,
                view().slug(),
                "New title",
                "DRAFT",
                "70000000-0000-4000-8000-000000000002",
                2,
                2,
                2,
                view().createdAt(),
                view().updatedAt()
        );
        when(operations.update(
                any(),
                any(),
                any(),
                any(),
                org.mockito.ArgumentMatchers.anyLong(),
                any()
        ))
                .thenReturn(updated);

        var response = controller.update(
                jwt,
                TEAM,
                STORY,
                view().id(),
                "\"1\"",
                new UpdateChapterDraftRequest(
                        "New title",
                        "<p>Two words</p>"
                )
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"2\"");
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
        verify(operations).update(
                ACTOR,
                TEAM,
                STORY,
                view().id(),
                1,
                new ChapterDraftOperations.UpdateCommand(
                        "New title",
                        "<p>Two words</p>"
                )
        );
    }

    @Test
    void rejectsAnUnquotedChapterVersion() {
        assertThatThrownBy(() -> controller.update(
                jwt,
                TEAM,
                STORY,
                view().id(),
                "1",
                new UpdateChapterDraftRequest("Title", "<p>x</p>")
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(400)
        );
    }

    private void assertMapped(
            ChapterDraftException.Kind kind,
            int expectedStatus
    ) {
        org.mockito.Mockito.reset(operations);
        when(operations.create(any(), any(), any(), any()))
                .thenThrow(new ChapterDraftException(
                        "CHAPTER_REJECTED",
                        "rejected",
                        kind
                ));
        assertThatThrownBy(() -> controller.create(
                jwt,
                TEAM,
                STORY,
                new CreateChapterDraftRequest(1, "Title", "<p>x</p>")
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value())
                        .isEqualTo(expectedStatus)
        );
    }

    private static ChapterDraftOperations.ChapterView view() {
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        return new ChapterDraftOperations.ChapterView(
                "60000000-0000-4000-8000-000000000001",
                STORY,
                TEAM,
                1,
                "chapter-1-mo-dau-60000000",
                "Mở đầu",
                "DRAFT",
                "70000000-0000-4000-8000-000000000001",
                1,
                1,
                1,
                now,
                now
        );
    }
}
