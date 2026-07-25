package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.WithdrawalException;
import com.storyplatform.monetization.application.WithdrawalOperations;
import com.storyplatform.monetization.application
        .MonetizationSuspendedException;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

@RestController
public final class WithdrawalController {

    private final WithdrawalOperations withdrawals;

    public WithdrawalController(WithdrawalOperations withdrawals) {
        this.withdrawals = Objects.requireNonNull(withdrawals);
    }

    @PostMapping("/teams/{teamId}/withdrawals")
    public ResponseEntity<WithdrawalOperations.Receipt> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateWithdrawalRequest request
    ) {
        try {
            var result = withdrawals.create(
                    jwt.getSubject(),
                    teamId,
                    idempotencyKey,
                    request.grossAmountXu(),
                    request.destinationId()
            );
            var response = result.replayed()
                    ? ResponseEntity.ok()
                    : ResponseEntity.created(URI.create(
                            "/api/v1/teams/" + teamId
                                    + "/withdrawals/" + result.id()
                    ));
            return response.cacheControl(CacheControl.noStore())
                    .body(result);
        } catch (WithdrawalException exception) {
            throw api(exception);
        } catch (MonetizationSuspendedException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MONETIZATION_SUSPENDED",
                    "Withdrawal requests temporarily unavailable",
                    exception.getMessage()
            );
        }
    }

    @GetMapping("/teams/{teamId}/withdrawals")
    public ResponseEntity<WithdrawalOperations.Page> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100) int limit
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(withdrawals.list(
                            jwt.getSubject(),
                            teamId,
                            cursor,
                            limit
                    ));
        } catch (WithdrawalException exception) {
            throw api(exception);
        }
    }

    private static ApiException api(WithdrawalException exception) {
        HttpStatus status = switch (exception.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DESTINATION_UNAVAILABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case INSUFFICIENT_BALANCE -> HttpStatus.UNPROCESSABLE_CONTENT;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        return new ApiException(
                status,
                "WITHDRAWAL_" + exception.kind(),
                "Withdrawal request rejected",
                exception.getMessage()
        );
    }
}
