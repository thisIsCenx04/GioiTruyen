package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api.TopupDiscountController;
import com.storyplatform.monetization.api.UpdateTopupDiscountRequest;
import com.storyplatform.monetization.application.TopupDiscountException;
import com.storyplatform.monetization.application.TopupDiscountOperations;
import com.storyplatform.shared.api.ApiException;
import com.storyplatform.shared.security.JwtPrivilegeEvaluator;
import com.storyplatform.shared.security.PrivilegedCapability;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopupDiscountControllerTest {

    private final TopupDiscountOperations operations =
            mock(TopupDiscountOperations.class);
    private final JwtPrivilegeEvaluator privileges =
            mock(JwtPrivilegeEvaluator.class);
    private final Jwt jwt = new Jwt(
            "token",
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "admin", "roles", java.util.List.of("ADMIN"))
    );
    private final TopupDiscountController controller =
            new TopupDiscountController(operations, privileges);

    @Test
    void readsAndUpdatesPrivateVersionedConfiguration() {
        var view = new TopupDiscountOperations.DiscountView(
                new BigDecimal("12.50"),
                1,
                Instant.EPOCH,
                "admin"
        );
        when(privileges.allows(
                jwt,
                PrivilegedCapability.SYSTEM_CONFIGURE
        )).thenReturn(true);
        when(operations.current()).thenReturn(view);
        when(operations.update(
                "admin",
                "grant",
                0,
                new BigDecimal("12.50"),
                "Seasonal operating policy"
        )).thenReturn(view);

        assertThat(controller.current(jwt).getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(controller.update(
                jwt,
                "\"0\"",
                "grant",
                new UpdateTopupDiscountRequest(
                        new BigDecimal("12.50"),
                        "Seasonal operating policy"
                )
        ).getBody()).isEqualTo(view);
    }

    @Test
    void rejectsMissingPrivilegeInvalidVersionAndServiceConflict() {
        assertThatThrownBy(() -> controller.current(jwt))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("TOPUP_DISCOUNT_FORBIDDEN");
        when(privileges.allows(
                jwt,
                PrivilegedCapability.SYSTEM_CONFIGURE
        )).thenReturn(true);
        assertThatThrownBy(() -> controller.update(
                jwt,
                "0",
                "grant",
                new UpdateTopupDiscountRequest(
                        BigDecimal.TEN,
                        "A valid policy reason"
                )
        )).isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("IF_MATCH_INVALID");
        when(operations.update(
                "admin",
                "grant",
                0,
                BigDecimal.TEN,
                "A valid policy reason"
        )).thenThrow(new TopupDiscountException(
                "stale",
                TopupDiscountException.Kind.CONFLICT
        ));
        assertThatThrownBy(() -> controller.update(
                jwt,
                "\"0\"",
                "grant",
                new UpdateTopupDiscountRequest(
                        BigDecimal.TEN,
                        "A valid policy reason"
                )
        )).isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("TOPUP_DISCOUNT_CONFLICT");
    }
}
