package com.storyplatform.notifications.api;

import com.storyplatform.notifications.application.NotificationException;
import com.storyplatform.notifications.application
        .NotificationPreferenceOperations;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Set;

@RestController
public final class NotificationPreferenceController {

    private final NotificationPreferenceOperations preferences;

    public NotificationPreferenceController(
            NotificationPreferenceOperations preferences
    ) {
        this.preferences = Objects.requireNonNull(
                preferences,
                "preferences"
        );
    }

    @GetMapping("/notification-preferences")
    public ResponseEntity<NotificationPreferenceOperations.PreferenceView>
            get(@AuthenticationPrincipal Jwt jwt) {
        return response(preferences.get(jwt.getSubject()));
    }

    @PatchMapping("/notification-preferences")
    public ResponseEntity<NotificationPreferenceOperations.PreferenceView>
            update(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody PreferenceRequest request
    ) {
        try {
            return response(preferences.update(
                    jwt.getSubject(),
                    version(ifMatch),
                    new NotificationPreferenceOperations.PreferenceCommand(
                            request.emailEnabled(),
                            request.pushEnabled(),
                            request.categories(),
                            request.consentGranted()
                    )
            ));
        } catch (NotificationException exception) {
            throw problem(exception);
        }
    }

    @PostMapping("/notification-unsubscribe")
    public ResponseEntity<NotificationPreferenceOperations.PreferenceView>
            unsubscribe(@RequestParam String token) {
        try {
            return response(preferences.unsubscribe(token));
        } catch (NotificationException exception) {
            throw problem(exception);
        }
    }

    private static ResponseEntity<
            NotificationPreferenceOperations.PreferenceView> response(
            NotificationPreferenceOperations.PreferenceView preference
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.ETAG,
                        "\"" + preference.version() + "\""
                )
                .body(preference);
    }

    private static long version(String value) {
        if (value == null || !value.matches("\"[0-9]+\"")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Preference update rejected",
                    "If-Match must contain one quoted version."
            );
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Preference update rejected",
                    "If-Match version is outside the supported range."
            );
        }
    }

    static ApiException problem(NotificationException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return new ApiException(
                status,
                exception.code(),
                "Notification preference rejected",
                exception.getMessage()
        );
    }

    public record PreferenceRequest(
            boolean emailEnabled,
            boolean pushEnabled,
            @NotNull Set<String> categories,
            boolean consentGranted
    ) {
        public PreferenceRequest {
            categories = categories == null
                    ? Set.of()
                    : Set.copyOf(categories);
        }
    }
}
