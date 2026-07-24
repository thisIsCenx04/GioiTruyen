package com.storyplatform.monetization.api;

import com.storyplatform.monetization.application.RewardException;
import com.storyplatform.monetization.application.RewardOperations;
import com.storyplatform.shared.api.ApiException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
public final class RewardController {

    private final RewardOperations rewards;

    public RewardController(RewardOperations rewards) {
        this.rewards = Objects.requireNonNull(rewards);
    }

    @GetMapping("/teams/{teamId}/rewards")
    public ResponseEntity<List<RewardOperations.SettlementView>> recent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String teamId,
            @RequestParam(defaultValue = "24") int limit
    ) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(rewards.recent(
                            jwt.getSubject(),
                            teamId,
                            limit
                    ));
        } catch (RewardException exception) {
            HttpStatus status = switch (exception.kind()) {
                case INVALID -> HttpStatus.BAD_REQUEST;
                case FORBIDDEN -> HttpStatus.FORBIDDEN;
                case CONFLICT -> HttpStatus.CONFLICT;
            };
            throw new ApiException(
                    status,
                    "REWARD_" + exception.kind(),
                    "Reward request rejected",
                    exception.getMessage()
            );
        }
    }
}
