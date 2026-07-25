package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application
        .MonetizationKillSwitchException;
import com.storyplatform.monetization.application
        .MonetizationKillSwitchOperations;
import com.storyplatform.monetization.domain.MonetizationKillSwitch;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@RestController
public final class MonetizationKillSwitchController {

    private final MonetizationKillSwitchOperations switches;
    private final JwtPrivilegeEvaluator privileges;

    public MonetizationKillSwitchController(
            MonetizationKillSwitchOperations switches,
            JwtPrivilegeEvaluator privileges
    ) {
        this.switches = Objects.requireNonNull(switches);
        this.privileges = Objects.requireNonNull(privileges);
    }

    @GetMapping("/admin/configuration/monetization-kill-switches")
    public ResponseEntity<List<MonetizationKillSwitch>> current(
            @AuthenticationPrincipal Jwt jwt
    ) {
        authorize(jwt);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(switches.current());
    }

    @PatchMapping(
            "/admin/configuration/monetization-kill-switches/{operation}"
    )
    public ResponseEntity<MonetizationKillSwitch> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String operation,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestHeader("Scoped-Reauthentication")
            String reauthentication,
            @Valid @RequestBody UpdateMonetizationKillSwitchRequest request
    ) {
        authorize(jwt);
        try {
            var result = switches.update(
                    jwt.getSubject(),
                    reauthentication,
                    operation(operation),
                    version(ifMatch),
                    request.engaged(),
                    request.reason()
            );
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .eTag(Long.toString(result.version()))
                    .body(result);
        } catch (MonetizationKillSwitchException exception) {
            HttpStatus status = switch (exception.kind()) {
                case INVALID -> HttpStatus.BAD_REQUEST;
                case FORBIDDEN -> HttpStatus.FORBIDDEN;
                case CONFLICT -> HttpStatus.CONFLICT;
            };
            throw api(status, exception.getMessage());
        }
    }

    private void authorize(Jwt jwt) {
        if (!privileges.allows(
                jwt,
                PrivilegedCapability.SYSTEM_CONFIGURE
        )) {
            throw api(
                    HttpStatus.FORBIDDEN,
                    "System configuration privileges are required."
            );
        }
    }

    private static MonetizationKillSwitch.Operation operation(
            String value
    ) {
        try {
            return MonetizationKillSwitch.Operation.valueOf(
                    value.replace('-', '_').toUpperCase(Locale.ROOT)
            );
        } catch (RuntimeException exception) {
            throw api(
                    HttpStatus.BAD_REQUEST,
                    "Kill switch operation is invalid."
            );
        }
    }

    private static long version(String value) {
        if (value == null || !value.matches("\"[0-9]+\"")) {
            throw api(
                    HttpStatus.BAD_REQUEST,
                    "If-Match must contain a quoted version."
            );
        }
        try {
            return Long.parseLong(value.substring(
                    1, value.length() - 1
            ));
        } catch (NumberFormatException exception) {
            throw api(
                    HttpStatus.BAD_REQUEST,
                    "If-Match version is outside the supported range."
            );
        }
    }

    private static ApiException api(
            HttpStatus status,
            String detail
    ) {
        return new ApiException(
                status,
                "MONETIZATION_KILL_SWITCH_REJECTED",
                "Monetization kill switch change rejected",
                detail
        );
    }
}
