package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.DonationException;
import com.storyplatform.monetization.application.DonationOperations;
import com.storyplatform.shared.api.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

@RestController
public final class DonationController {

    private final DonationOperations donations;

    public DonationController(DonationOperations donations) {
        this.donations = Objects.requireNonNull(donations);
    }

    @PostMapping("/donations")
    public ResponseEntity<DonationOperations.Receipt> donate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateDonationRequest request
    ) {
        try {
            var receipt = donations.donate(
                    jwt.getSubject(),
                    idempotencyKey,
                    request.teamId(),
                    request.amountXu(),
                    request.message()
            );
            var response = receipt.replayed()
                    ? ResponseEntity.ok()
                    : ResponseEntity.created(URI.create(
                            "/api/v1/donations/" + receipt.donationId()
                    ));
            return response.cacheControl(CacheControl.noStore())
                    .body(receipt);
        } catch (DonationException exception) {
            HttpStatus status = switch (exception.kind()) {
                case INVALID -> HttpStatus.BAD_REQUEST;
                case TEAM_NOT_FOUND -> HttpStatus.NOT_FOUND;
                case INSUFFICIENT_BALANCE ->
                        HttpStatus.UNPROCESSABLE_CONTENT;
                case CONFLICT -> HttpStatus.CONFLICT;
            };
            throw new ApiException(
                    status,
                    "DONATION_" + exception.kind(),
                    "Donation rejected",
                    exception.getMessage()
            );
        }
    }
}
