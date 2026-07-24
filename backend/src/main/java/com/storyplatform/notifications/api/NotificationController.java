package com.storyplatform.notifications.api;

import com.storyplatform.notifications.application.NotificationException;
import com.storyplatform.notifications.application.NotificationOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public final class NotificationController {

    private final NotificationOperations notifications;

    public NotificationController(NotificationOperations notifications) {
        this.notifications = Objects.requireNonNull(
                notifications,
                "notifications"
        );
    }

    @GetMapping("/notifications")
    public ResponseEntity<NotificationOperations.NotificationPage> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(notifications.list(
                            jwt.getSubject(),
                            limit,
                            cursor
                    ));
        } catch (NotificationException exception) {
            throw problem(exception);
        }
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ResponseEntity<NotificationOperations.NotificationView> markRead(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String notificationId
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(notifications.markRead(
                            jwt.getSubject(),
                            notificationId
                    ));
        } catch (NotificationException exception) {
            throw problem(exception);
        }
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<NotificationOperations.ReadWatermark> markAllRead(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(notifications.markAllRead(jwt.getSubject()));
    }

    private static ApiException problem(NotificationException exception) {
        return new ApiException(
                exception.kind() == NotificationException.Kind.INVALID
                        ? HttpStatus.BAD_REQUEST
                        : HttpStatus.NOT_FOUND,
                exception.code(),
                "Notification request rejected",
                exception.getMessage()
        );
    }
}
