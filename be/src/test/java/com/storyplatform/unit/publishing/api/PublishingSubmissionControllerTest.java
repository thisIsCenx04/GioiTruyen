package com.storyplatform.unit.publishing.api;

import com.storyplatform.publishing.api.PublishingSubmissionController;
import com.storyplatform.publishing.application
        .PublishingSubmissionException;
import com.storyplatform.publishing.application
        .PublishingSubmissionOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingSubmissionControllerTest {

    private static final String ACTOR =
            "10000000-0000-4000-8000-000000000001";
    private static final String TEAM =
            "20000000-0000-4000-8000-000000000001";
    private static final String STORY =
            "40000000-0000-4000-8000-000000000001";
    private PublishingSubmissionOperations operations;
    private PublishingSubmissionController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        operations = mock(PublishingSubmissionOperations.class);
        controller = new PublishingSubmissionController(operations);
        Instant now = Instant.parse("2026-07-24T00:00:00Z");
        jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(ACTOR)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }

    @Test
    void returnsAcceptedReviewLocationNoStoreAndEtag() {
        when(operations.submit(any(), any(), any(), any()))
                .thenReturn(view());

        var response = controller.submit(
                jwt,
                TEAM,
                STORY,
                "submit-request-001"
        );

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"1\"");
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(response.getHeaders().getLocation().toString())
                .endsWith("/moderation/reviews/" + view().reviewId());
        verify(operations).submit(
                ACTOR,
                TEAM,
                STORY,
                "submit-request-001"
        );
    }

    @Test
    void mapsEveryDomainFailureAndInvalidIdentifiers() {
        assertMapped(PublishingSubmissionException.Kind.INVALID, 400);
        assertMapped(PublishingSubmissionException.Kind.FORBIDDEN, 403);
        assertMapped(PublishingSubmissionException.Kind.NOT_FOUND, 404);
        assertMapped(PublishingSubmissionException.Kind.CONFLICT, 409);
        assertMapped(
                PublishingSubmissionException.Kind.UNPROCESSABLE,
                422
        );
        assertThatThrownBy(() -> controller.submit(
                jwt, "invalid", STORY, "submit-request-001"
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(400)
        );
    }

    private void assertMapped(
            PublishingSubmissionException.Kind kind,
            int status
    ) {
        org.mockito.Mockito.reset(operations);
        when(operations.submit(any(), any(), any(), any()))
                .thenThrow(new PublishingSubmissionException(
                        "SUBMISSION_REJECTED",
                        "rejected",
                        kind
                ));
        assertThatThrownBy(() -> controller.submit(
                jwt, TEAM, STORY, "submit-request-001"
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.status().value()).isEqualTo(status)
        );
    }

    private static PublishingSubmissionOperations.SubmissionView view() {
        return new PublishingSubmissionOperations.SubmissionView(
                "80000000-0000-4000-8000-000000000001",
                STORY,
                "50000000-0000-4000-8000-000000000001",
                List.of(new PublishingSubmissionOperations
                        .FrozenChapterRevision(
                        "60000000-0000-4000-8000-000000000001",
                        "70000000-0000-4000-8000-000000000001",
                        1
                )),
                "AUTOMATED_CHECK_PENDING",
                1,
                Instant.parse("2026-07-24T00:00:00Z")
        );
    }
}
