package com.storyplatform.notifications.api;

import com.storyplatform.notifications.application.NotificationException;
import com.storyplatform.notifications.application.PushSubscriptionOperations;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

@RestController
public final class PushSubscriptionController {

    private final PushSubscriptionOperations subscriptions;

    public PushSubscriptionController(
            PushSubscriptionOperations subscriptions
    ) {
        this.subscriptions = Objects.requireNonNull(
                subscriptions,
                "subscriptions"
        );
    }

    @PostMapping("/notification-push-subscriptions")
    public ResponseEntity<PushSubscriptionOperations.SubscriptionView>
            register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        try {
            var saved = subscriptions.register(
                    jwt.getSubject(),
                    new PushSubscriptionOperations.SubscriptionCommand(
                            request.endpoint(),
                            request.p256dh(),
                            request.auth()
                    )
            );
            return ResponseEntity.created(URI.create(
                            "/notification-push-subscriptions/" + saved.id()
                    ))
                    .cacheControl(CacheControl.noStore())
                    .body(saved);
        } catch (NotificationException exception) {
            throw NotificationPreferenceController.problem(exception);
        }
    }

    @DeleteMapping("/notification-push-subscriptions/{subscriptionId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String subscriptionId
    ) {
        try {
            subscriptions.remove(jwt.getSubject(), subscriptionId);
            return ResponseEntity.status(HttpStatus.NO_CONTENT)
                    .cacheControl(CacheControl.noStore())
                    .build();
        } catch (NotificationException exception) {
            throw NotificationPreferenceController.problem(exception);
        }
    }

    public record PushSubscriptionRequest(
            @NotBlank @Size(max = 2048) String endpoint,
            @NotBlank @Size(min = 43, max = 128) String p256dh,
            @NotBlank @Size(min = 16, max = 64) String auth
    ) {
    }
}
