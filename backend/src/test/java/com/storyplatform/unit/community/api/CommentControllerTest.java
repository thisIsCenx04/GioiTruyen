package com.storyplatform.unit.community.api;

import com.storyplatform.community.api.CommentController;
import com.storyplatform.community.application.CommentOperations;
import com.storyplatform.community.domain.Comment;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentControllerTest {

    private static final String ID =
            "20000000-0000-4000-8000-000000000001";

    @Test
    void routesLifecycleToAuthenticatedActorAndParsesVersion() {
        CommentOperations operations = mock(CommentOperations.class);
        CommentController controller = new CommentController(operations);
        CommentOperations.CommentView view = new CommentOperations.CommentView(
                ID,
                Comment.TargetType.STORY,
                ID,
                null,
                ID,
                0,
                new CommentOperations.AuthorView("user", "Độc giả", null),
                "Nội dung",
                Comment.Status.VISIBLE,
                1,
                Instant.parse("2026-07-25T00:00:00Z"),
                Instant.parse("2026-07-25T00:00:00Z")
        );
        when(operations.create(eq("user"), any())).thenReturn(view);
        when(operations.update(eq("user"), eq(ID), eq(3L), eq("Mới")))
                .thenReturn(view);
        when(operations.delete("user", ID, 4)).thenReturn(view);

        controller.create(jwt(), new CommentController.CreateCommentRequest(
                "story", ID, null, "Nội dung"
        ));
        controller.update(
                jwt(),
                ID,
                "\"3\"",
                new CommentController.UpdateCommentRequest("Mới")
        );
        controller.delete(jwt(), ID, "\"4\"");

        verify(operations).create(eq("user"), any());
        verify(operations).update("user", ID, 3, "Mới");
        verify(operations).delete("user", ID, 4);
    }

    @Test
    void rejectsInvalidIdentifiersTargetTypesAndEtags() {
        CommentController controller = new CommentController(
                mock(CommentOperations.class)
        );
        assertThatThrownBy(() -> controller.list(
                "unknown", ID, null, 20
        )).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.delete(
                jwt(), ID, "3"
        )).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> controller.create(
                jwt(),
                new CommentController.CreateCommentRequest(
                        Comment.TargetType.STORY.name(),
                        "bad",
                        null,
                        "body"
                )
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
