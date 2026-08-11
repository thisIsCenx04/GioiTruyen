package com.storyplatform.quest.api;

import com.storyplatform.quest.application.QuestService;
import com.storyplatform.quest.application.dto.QuestDtos;
import com.storyplatform.shared.api.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reader-facing quest board. Every endpoint needs a signed-in user: quests are
 * per-account, so there is nothing meaningful to show a guest.
 */
@RestController
@RequestMapping("/quests")
public class QuestController {

    private final QuestService questService;

    public QuestController(QuestService questService) {
        this.questService = questService;
    }

    @GetMapping("/me")
    public QuestDtos.QuestBoard board(@AuthenticationPrincipal Jwt jwt) {
        return questService.board(currentUser(jwt));
    }

    /**
     * The quest catalogue with no progress attached, for signed-out visitors.
     * They can see what is on offer; claiming still needs an account.
     */
    @GetMapping("/preview")
    public QuestDtos.QuestBoard preview() {
        return questService.preview();
    }

    /**
     * Called while the reader is active - once a minute from the reading page,
     * or once per share/comment. Returns the refreshed board so the UI updates
     * without a second request.
     */
    @PostMapping("/progress")
    public QuestDtos.QuestBoard progress(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody QuestDtos.ProgressRequest request
    ) {
        return questService.recordProgress(currentUser(jwt), request.questType(), request.amount());
    }

    @PostMapping("/{questId}/claim")
    public QuestDtos.ClaimResult claim(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String questId
    ) {
        return questService.claim(currentUser(jwt), questId);
    }

    private static UUID currentUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.required",
                    "Login required", "Bạn cần đăng nhập để xem nhiệm vụ.");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
