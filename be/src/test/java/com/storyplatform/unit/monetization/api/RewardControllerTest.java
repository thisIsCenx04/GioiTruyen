package com.storyplatform.unit.monetization.api;

import com.storyplatform.monetization.api.RewardController;
import com.storyplatform.monetization.application.RewardException;
import com.storyplatform.monetization.application.RewardOperations;
import com.storyplatform.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RewardControllerTest {

    private final RewardOperations rewards = mock(RewardOperations.class);
    private final RewardController controller = new RewardController(rewards);
    private final Jwt jwt = new Jwt(
            "token",
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(60),
            Map.of("alg", "none"),
            Map.of("sub", "actor")
    );

    @Test
    void returnsPrivateAuthorizedRewardHistory() {
        var row = new RewardOperations.SettlementView(
                "10000000-0000-4000-8000-000000000001",
                "2026-07-24",
                1_000,
                100,
                false,
                "reward-2026.1",
                "view-aggregate-2026.1",
                "POSTED",
                Instant.EPOCH,
                Instant.EPOCH
        );
        when(rewards.recent("actor", "team", 24))
                .thenReturn(List.of(row));

        var response = controller.recent(jwt, "team", 24);

        assertThat(response.getBody()).containsExactly(row);
        assertThat(response.getHeaders().getCacheControl())
                .contains("no-store");
    }

    @Test
    void mapsRewardErrorsToStableStatuses() {
        assertFailure(RewardException.Kind.INVALID, HttpStatus.BAD_REQUEST);
        assertFailure(RewardException.Kind.FORBIDDEN, HttpStatus.FORBIDDEN);
        assertFailure(RewardException.Kind.CONFLICT, HttpStatus.CONFLICT);
    }

    private void assertFailure(
            RewardException.Kind kind,
            HttpStatus status
    ) {
        doThrow(new RewardException("rejected", kind))
                .when(rewards).recent("actor", "team", 24);

        assertThatThrownBy(() -> controller.recent(jwt, "team", 24))
                .isInstanceOf(ApiException.class)
                .extracting("status", "code")
                .containsExactly(status, "REWARD_" + kind);
    }
}
