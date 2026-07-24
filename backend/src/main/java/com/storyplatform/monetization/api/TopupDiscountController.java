package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.TopupDiscountException;
import com.storyplatform.monetization.application.TopupDiscountOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public final class TopupDiscountController {

    public static final String REAUTHENTICATION =
            "Scoped-Reauthentication";
    private final TopupDiscountOperations discounts;
    private final JwtPrivilegeEvaluator privileges;

    public TopupDiscountController(
            TopupDiscountOperations discounts,
            JwtPrivilegeEvaluator privileges
    ) {
        this.discounts = Objects.requireNonNull(discounts);
        this.privileges = Objects.requireNonNull(privileges);
    }

    @GetMapping("/admin/configuration/topup-discount")
    public ResponseEntity<TopupDiscountOperations.DiscountView> current(
            @AuthenticationPrincipal Jwt jwt
    ) {
        authorize(jwt);
        return noStore(discounts.current());
    }

    @PatchMapping("/admin/configuration/topup-discount")
    public ResponseEntity<TopupDiscountOperations.DiscountView> update(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestHeader(REAUTHENTICATION) String reauthentication,
            @Valid @RequestBody UpdateTopupDiscountRequest request
    ) {
        authorize(jwt);
        try {
            return noStore(discounts.update(
                    jwt.getSubject(),
                    reauthentication,
                    version(ifMatch),
                    request.discountPercent(),
                    request.reason()
            ));
        } catch (TopupDiscountException exception) {
            HttpStatus status = switch (exception.kind()) {
                case INVALID -> HttpStatus.BAD_REQUEST;
                case FORBIDDEN -> HttpStatus.FORBIDDEN;
                case CONFLICT -> HttpStatus.CONFLICT;
            };
            throw new ApiException(
                    status,
                    "TOPUP_DISCOUNT_" + exception.kind(),
                    "Top-up discount change rejected",
                    exception.getMessage()
            );
        }
    }

    private void authorize(Jwt jwt) {
        if (!privileges.allows(
                jwt,
                PrivilegedCapability.SYSTEM_CONFIGURE
        )) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "TOPUP_DISCOUNT_FORBIDDEN",
                    "Top-up discount request rejected",
                    "Administrator configuration privileges are required."
            );
        }
    }

    private static long version(String value) {
        if (value == null || !value.matches("\"[0-9]+\"")) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Top-up discount change rejected",
                    "If-Match must contain one quoted non-negative version."
            );
        }
        try {
            return Long.parseLong(value.substring(1, value.length() - 1));
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "IF_MATCH_INVALID",
                    "Top-up discount change rejected",
                    "If-Match version is outside the supported range."
            );
        }
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
