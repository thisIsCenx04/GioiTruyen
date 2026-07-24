package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.TopupRequestException;
import com.storyplatform.monetization.application.TopupRequestOperations;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@RestController
public final class TopupRequestController {

    private final TopupRequestOperations topups;

    public TopupRequestController(TopupRequestOperations topups) {
        this.topups = Objects.requireNonNull(topups);
    }

    @PostMapping("/wallets/me/topups")
    public ResponseEntity<TopupRequestOperations.TopupView> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTopupRequest request
    ) {
        try {
            var created = topups.create(
                    jwt.getSubject(),
                    idempotencyKey,
                    request.amountVnd()
            );
            return ResponseEntity.created(
                            URI.create("/api/v1/wallets/me/topups/"
                                    + created.id())
                    )
                    .cacheControl(CacheControl.noStore())
                    .body(created);
        } catch (TopupRequestException exception) {
            throw apiException(exception);
        }
    }

    @GetMapping("/wallets/me/topups")
    public ResponseEntity<List<TopupRequestOperations.TopupView>> recent(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(topups.recent(jwt.getSubject()));
    }

    @GetMapping("/wallets/me/topups/{requestId}")
    public ResponseEntity<TopupRequestOperations.TopupView> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String requestId
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(topups.get(jwt.getSubject(), requestId));
        } catch (TopupRequestException exception) {
            throw apiException(exception);
        }
    }

    private static ApiException apiException(
            TopupRequestException exception
    ) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case CONFLICT -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return new ApiException(
                status,
                "TOPUP_REQUEST_" + exception.kind(),
                "Top-up request rejected",
                exception.getMessage()
        );
    }
}
