package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.ReferralException;
import com.storyplatform.monetization.application.ReferralOperations;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

@RestController
public final class ReferralController {

    private final ReferralOperations referrals;

    public ReferralController(ReferralOperations referrals) {
        this.referrals = Objects.requireNonNull(referrals);
    }

    @GetMapping("/referrals/me")
    public ResponseEntity<ReferralOperations.ReferralView> mine(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(referrals.mine(jwt.getSubject()));
    }

    @PostMapping("/referrals/me/attribution")
    public ResponseEntity<ReferralOperations.AttributionView> attribute(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateReferralAttributionRequest request
    ) {
        try {
            var result = referrals.attribute(
                    jwt.getSubject(),
                    request.code(),
                    idempotencyKey
            );
            var response = result.replayed()
                    ? ResponseEntity.ok()
                    : ResponseEntity.created(URI.create(
                            "/api/v1/referrals/me"
                    ));
            return response.cacheControl(CacheControl.noStore())
                    .body(result);
        } catch (ReferralException exception) {
            HttpStatus status = switch (exception.kind()) {
                case INVALID -> HttpStatus.BAD_REQUEST;
                case NOT_FOUND -> HttpStatus.NOT_FOUND;
                case CONFLICT -> HttpStatus.CONFLICT;
                case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            };
            throw new ApiException(
                    status,
                    "REFERRAL_" + exception.kind(),
                    "Referral request rejected",
                    exception.getMessage()
            );
        }
    }
}
