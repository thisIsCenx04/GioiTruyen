package com.storyplatform.unit.notifications.application;

import com.storyplatform.notifications.application.NotificationException;
import com.storyplatform.notifications.application.NotificationOperations;
import com.storyplatform.notifications.application.NotificationService;
import com.storyplatform.notifications.application.port
        .NotificationCursorCodec;
import com.storyplatform.notifications.application.port
        .NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private static final String USER =
            "10000000-0000-4000-8000-000000000001";
    private static final String OTHER =
            "10000000-0000-4000-8000-000000000002";
    private static final String ID =
            "20000000-0000-4000-8000-000000000001";
    private static final Instant NOW =
            Instant.parse("2026-07-24T00:00:00Z");
    private final NotificationRepository repository =
            mock(NotificationRepository.class);
    private final NotificationCursorCodec cursors =
            mock(NotificationCursorCodec.class);

    @Test
    void returnsBoundKeysetPageAndUnreadCount() {
        var item = view();
        when(repository.list(USER, 2, null)).thenReturn(
                new NotificationRepository.Slice(List.of(item), true)
        );
        when(repository.unreadCount(USER)).thenReturn(7L);
        when(cursors.encode(any())).thenReturn("next");

        var page = service().list(USER, 2, null);

        assertThat(page.items()).containsExactly(item);
        assertThat(page.nextCursor()).isEqualTo("next");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.unreadCount()).isEqualTo(7);
        verify(cursors).encode(new NotificationCursorCodec.Position(
                USER, NOW, ID
        ));
    }

    @Test
    void decodesOnlyRecipientBoundCursorAndRejectsLimits() {
        var position = new NotificationCursorCodec.Position(USER, NOW, ID);
        when(cursors.decode("cursor")).thenReturn(position);
        when(repository.list(USER, 20, position)).thenReturn(
                new NotificationRepository.Slice(List.of(), false)
        );
        service().list(USER, 20, "cursor");

        when(cursors.decode("other")).thenReturn(
                new NotificationCursorCodec.Position(OTHER, NOW, ID)
        );
        assertInvalid(() -> service().list(USER, 20, "other"));
        when(cursors.decode("bad")).thenThrow(
                new IllegalArgumentException("bad")
        );
        assertInvalid(() -> service().list(USER, 20, "bad"));
        assertInvalid(() -> service().list(USER, 0, null));
        assertInvalid(() -> service().list(USER, 101, null));
    }

    @Test
    void marksOneOrAllReadIdempotently() {
        when(repository.markRead(USER, ID, NOW))
                .thenReturn(Optional.of(view()));
        assertThat(service().markRead(USER, ID)).isEqualTo(view());
        when(repository.markAllRead(USER, NOW)).thenReturn(NOW);
        when(repository.unreadCount(USER)).thenReturn(0L);
        assertThat(service().markAllRead(USER))
                .isEqualTo(new NotificationOperations.ReadWatermark(NOW, 0));

        when(repository.markRead(USER, ID, NOW))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().markRead(USER, ID))
                .isInstanceOf(NotificationException.class)
                .extracting("code")
                .isEqualTo("NOTIFICATION_NOT_FOUND");
    }

    @Test
    void appendsSanitizedNotificationUsingStableEventDedupe() {
        when(repository.saveIfAbsent(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new NotificationRepository.SaveResult(view(), true));

        assertThat(service().append(
                USER,
                "story.published:1",
                "STORY_PUBLISHED",
                "<b>New chapter</b>",
                "<script>bad()</script> Ready ",
                Map.of("storyId", ID)
        )).isEqualTo(view());
        verify(repository).saveIfAbsent(
                ID,
                USER,
                "story.published:1",
                "STORY_PUBLISHED",
                "New chapter",
                "Ready",
                Map.of("storyId", ID),
                NOW
        );
    }

    @Test
    void rejectsMalformedAppendPayloadsAndIdentifiers() {
        assertInvalid(() -> service().list("bad", 20, null));
        assertInvalid(() -> service().append(
                USER, "bad key", "VALID_CODE", "title", "", Map.of()
        ));
        assertInvalid(() -> service().append(
                USER, "event", "bad", "title", "", Map.of()
        ));
        assertInvalid(() -> service().append(
                USER, "event", "VALID_CODE", " ", "", Map.of()
        ));
    }

    private NotificationService service() {
        return new NotificationService(
                repository,
                cursors,
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> ID
        );
    }

    private static NotificationOperations.NotificationView view() {
        return new NotificationOperations.NotificationView(
                ID,
                "STORY_PUBLISHED",
                "New chapter",
                "Ready",
                Map.of("storyId", ID),
                null,
                NOW
        );
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(NotificationException.class)
                .extracting("code")
                .isEqualTo("NOTIFICATION_INVALID");
    }
}
