package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application
        .WithdrawalReviewOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public final class WithdrawalReviewController {

    private final WithdrawalReviewOperations reviews;
    private final JwtPrivilegeEvaluator privileges;

    public WithdrawalReviewController(
            WithdrawalReviewOperations reviews,
            JwtPrivilegeEvaluator privileges
    ) {
        this.reviews = Objects.requireNonNull(reviews);
        this.privileges = Objects.requireNonNull(privileges);
    }

    @PostMapping("/admin/withdrawals/{withdrawalId}/approve")
    public ResponseEntity<WithdrawalReviewOperations.Decision> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String withdrawalId,
            @RequestHeader("Scoped-Reauthentication") String reauthentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReviewWithdrawalRequest request
    ) {
        requirePrivilege(jwt);
        try {
            return response(reviews.approve(
                    jwt.getSubject(),
                    withdrawalId,
                    reauthentication,
                    idempotencyKey,
                    request.reason()
            ));
        } catch (WithdrawalException exception) {
            throw api(exception);
        }
    }

    @PostMapping("/admin/withdrawals/{withdrawalId}/reject")
    public ResponseEntity<WithdrawalReviewOperations.Decision> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String withdrawalId,
            @RequestHeader("Scoped-Reauthentication") String reauthentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReviewWithdrawalRequest request
    ) {
        requirePrivilege(jwt);
        try {
            return response(reviews.reject(
                    jwt.getSubject(),
                    withdrawalId,
                    reauthentication,
                    idempotencyKey,
                    request.reason()
            ));
        } catch (WithdrawalException exception) {
            throw api(exception);
        }
    }

    private void requirePrivilege(Jwt jwt) {
        if (!privileges.allows(
                jwt,
                PrivilegedCapability.FINANCE_REVIEW
        )) {
            throw api(new WithdrawalException(
                    "Finance review privileges are required.",
                    WithdrawalException.Kind.FORBIDDEN
            ));
        }
    }

    private static ResponseEntity<WithdrawalReviewOperations.Decision>
            response(WithdrawalReviewOperations.Decision decision) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(decision);
    }

    private static ApiException api(WithdrawalException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DESTINATION_UNAVAILABLE, INSUFFICIENT_BALANCE ->
                    HttpStatus.UNPROCESSABLE_CONTENT;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return new ApiException(
                status,
                "WITHDRAWAL_" + exception.kind(),
                "Withdrawal review rejected",
                exception.getMessage()
        );
    }
}
