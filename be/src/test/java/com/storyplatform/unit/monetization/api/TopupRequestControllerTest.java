package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api.CreateTopupRequest;
import com.storyplatform.monetization.api.TopupRequestController;
import com.storyplatform.monetization.application.TopupRequestException;
import com.storyplatform.monetization.application.TopupRequestOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopupRequestControllerTest {

    private final TopupRequestOperations operations =
            mock(TopupRequestOperations.class);
    private final TopupRequestController controller =
            new TopupRequestController(operations);
    private final Jwt jwt = new Jwt(
            "token",
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "reader")
    );

    @Test
    void createsAndReadsOnlyPrivateResponses() {
        var view = view();
        when(operations.create("reader", "request-key-000001", 100_000))
                .thenReturn(view);
        when(operations.recent("reader")).thenReturn(List.of(view));
        when(operations.get("reader", view.id())).thenReturn(view);

        var created = controller.create(
                jwt,
                "request-key-000001",
                new CreateTopupRequest(100_000)
        );

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getHeaders().getLocation())
                .hasToString("/api/v1/wallets/me/topups/" + view.id());
        assertThat(created.getHeaders().getCacheControl())
                .contains("no-store");
        assertThat(controller.recent(jwt).getBody()).containsExactly(view);
        assertThat(controller.get(jwt, view.id()).getBody()).isEqualTo(view);
    }

    @Test
    void mapsBusinessErrorsWithoutLeakingInternalDetails() {
        when(operations.create(
                "reader",
                "request-key-000001",
                100_000
        )).thenThrow(new TopupRequestException(
                "key reused",
                TopupRequestException.Kind.CONFLICT
        ));

        assertThatThrownBy(() -> controller.create(
                jwt,
                "request-key-000001",
                new CreateTopupRequest(100_000)
        )).isInstanceOf(ApiException.class)
                .extracting("status", "code")
                .containsExactly(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "TOPUP_REQUEST_CONFLICT"
                );
    }

    private static TopupRequestOperations.TopupView view() {
        return new TopupRequestOperations.TopupView(
                "10000000-0000-4000-8000-000000000001",
                100_000,
                90_000,
                BigDecimal.TEN,
                0,
                "GT12345678901234",
                "server-qr",
                "AWAITING_PAYMENT",
                Instant.EPOCH.plusSeconds(1800),
                Instant.EPOCH
        );
    }
}
